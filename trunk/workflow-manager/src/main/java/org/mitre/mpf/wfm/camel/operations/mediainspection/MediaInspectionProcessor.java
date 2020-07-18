/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

package org.mitre.mpf.wfm.camel.operations.mediainspection;

import com.google.common.base.Preconditions;
import org.apache.camel.Exchange;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParsersConfigReader;
import org.mitre.mpf.framecounter.FrameCounter;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.MediaTypeUtils;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** This processor extracts metadata about the input medium. */
@Component(MediaInspectionProcessor.REF)
public class MediaInspectionProcessor extends WfmProcessor {
    private static final Logger log = LoggerFactory.getLogger(MediaInspectionProcessor.class);
    public static final String REF = "mediaInspectionProcessor";

    private final InProgressBatchJobsService inProgressJobs;

    private final IoUtils ioUtils;

    @Inject
    public MediaInspectionProcessor(InProgressBatchJobsService inProgressJobs, IoUtils ioUtils) {
        this.inProgressJobs = inProgressJobs;
        this.ioUtils = ioUtils;
    }


    @Override
    public void wfmProcess(Exchange exchange) throws WfmProcessingException {
        long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        long mediaId = exchange.getIn().getHeader(MpfHeaders.MEDIA_ID, Long.class);


        // Check if user has provided the required metadata.
        boolean needsInspection = true;

        // During testing make "inProgressJobs.getJob(jobId);" a mock call.
        // Using the when method
        // and make sure that getMedia never gets called.
        // Updated: switch to getMedia().getMediaProperties()
        //BatchJob currentJob = inProgressJobs.getJob(jobId);

        Media media = inProgressJobs.getJob(jobId).getMedia(mediaId);
        // Mocked job object, can set this jobProperties directly.
        // Unless this is final, which means it should be created directly.
        //Map<String, String> jobProperties = currentJob.getJobProperties();
        Map<String, String> mediaProperties = media.getMediaSpecificProperties();

        if (mediaProperties.containsKey("MIME_TYPE") && mediaProperties.containsKey("MEDIA_HASH")) {

            int length = -1;
            String sha = mediaProperties.get("MEDIA_HASH");
            String mimeType = TextUtils.trim(mediaProperties.get("MIME_TYPE"));

            // Copy over all user specified media properties.
            Map<String, String> mediaMetadata = new HashMap<>();
            mediaMetadata.putAll(mediaProperties);

            switch (MediaTypeUtils.parse(mimeType)) {
                case AUDIO:
                    // Return a boolean.
                    needsInspection = inspectAudioMetadata(mediaMetadata);
                    break;
                case VIDEO:
                    // Return length of video. Negative implies failure reading metadata.
                    length = inspectVideoMetadata(mediaMetadata);
                    needsInspection = (length < 0);
                    break;
                case IMAGE:
                    needsInspection = inspectImageMetadata(mediaMetadata);
                    // If inspection successful, set media length to 1 for images.
                    if (!needsInspection) {
                        length = 1;
                    }
                    break;
                default:
                    // If unknown mimetype specified, automatically allow media inspection to be skipped.
                    needsInspection = false;
                    break;
            }

            if (!needsInspection) {
                // Copy these headers to the output exchange.
                inProgressJobs.addMediaInspectionInfo(jobId, mediaId, sha, mimeType, length, mediaMetadata);
                exchange.getOut().setHeader(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
                exchange.getOut().setHeader(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
                exchange.getOut().setHeader(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));
                exchange.getOut().setHeader(MpfHeaders.JOB_ID, jobId);
                exchange.getOut().setHeader(MpfHeaders.MEDIA_ID, mediaId);
                return;
            } else {
                log.warn("User specified media metadata not recognized. Proceeding to media inspection.");
            }

        }


        if(!media.isFailed()) {
            // Any request to pull a remote file should have already populated the local uri.
            assert media.getLocalPath() != null : "Media being processed by the MediaInspectionProcessor must have a local URI associated with them.";

            try {
                Path localPath = media.getLocalPath();
                String sha = null;
                String mimeType = null;

                try (InputStream inputStream = Files.newInputStream(localPath)) {
                    log.debug("Calculating hash for '{}'.", localPath);
                    sha = DigestUtils.sha256Hex(inputStream);
                } catch(IOException ioe) {
                    String errorMessage = "Could not calculate the SHA-256 hash for the file due to IOException: "
                            + ioe;
                    inProgressJobs.addError(jobId, mediaId, IssueCodes.ARTIFACT_EXTRACTION, errorMessage);
                    log.error(errorMessage, ioe);
                }

                try {
                    mimeType = ioUtils.getMimeType(localPath);
                } catch(IOException ioe) {
                    String errorMessage = "Could not determine the MIME type for the media due to IOException: "
                            + ioe;
                    inProgressJobs.addError(jobId, mediaId, IssueCodes.MEDIA_INSPECTION, errorMessage);
                    log.error(errorMessage, ioe);
                }

                Map<String, String> mediaMetadata = new HashMap<>();
                mediaMetadata.put("MIME_TYPE", mimeType);
                int length = -1;
                switch(MediaTypeUtils.parse(mimeType)) {
                    case AUDIO:
                        length = inspectAudio(localPath, jobId, mediaId, mediaMetadata);
                        break;

                    case VIDEO:
                        length = inspectVideo(localPath, jobId, mediaId, mimeType, mediaMetadata);
                        break;

                    case IMAGE:
                        length = inspectImage(localPath, jobId, mediaId, mediaMetadata);
                        break;

                    default:
                        log.error("transientMedia.getMediaType() = {} is undefined. ", media.getMediaType());
                        break;
                }
                inProgressJobs.addMediaInspectionInfo(jobId, mediaId, sha, mimeType, length, mediaMetadata);
            } catch (Exception exception) {
                log.error("[Job {}|*|*] Failed to inspect {} due to an exception.", exchange.getIn().getHeader(MpfHeaders.JOB_ID), media.getLocalPath(), exception);
                if (exception instanceof TikaException) {
                    inProgressJobs.addError(jobId, mediaId, IssueCodes.MEDIA_INSPECTION,
                                            "Tika media inspection error: " + exception.getMessage());
                } else {
                    inProgressJobs.addError(jobId, mediaId, IssueCodes.MEDIA_INSPECTION, exception.getMessage());
                }
            }
        } else {
            log.error("[Job {}|*|*] Skipping inspection of Media #{} as it is in an error state.",
                      jobId, media.getId());
        }

        // Copy these headers to the output exchange.
        exchange.getOut().setHeader(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
        exchange.getOut().setHeader(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
        exchange.getOut().setHeader(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));
        exchange.getOut().setHeader(MpfHeaders.JOB_ID, jobId);
        exchange.getOut().setHeader(MpfHeaders.MEDIA_ID, mediaId);
        if (media.isFailed()) {
            inProgressJobs.setJobStatus(jobId, BatchJobStatusType.ERROR);
        }
    }

    private boolean checkRequiredMediaMetadata(Map<String, String> properties, String[] requiredProperties) {
        for (String property : requiredProperties) {
            if (!properties.containsKey(property)) {
                return true;
            }
        }
        return false;
    }


    private boolean inspectAudioMetadata(Map<String, String> mediaMetadata) {
        if (!mediaMetadata.containsKey("DURATION")) {
            return true;
        }
        try {
            // Confirm that metadata values are valid.
            String duration = mediaMetadata.get("DURATION");
            int check = Integer.parseInt(duration.trim());
            if (check < 0) {
                return true;
            }
        } catch (NumberFormatException ex) {
            log.warn("Formatting error for audio duration. \nException : {}", ex.toString());
            return true;
        } catch (NullPointerException ex) {
            log.warn("Formatting error for audio file metadata: {}", ex.toString());
            return true;
        }
        return false;
    }

    private int inspectVideoMetadata( Map<String, String> mediaMetadata) {
        String[] required = { "FRAME_WIDTH", "FRAME_HEIGHT", "FRAME_COUNT", "FPS", "DURATION"};
        // Double check if these properties below can be optional as well.
        // Optional parameter: rotation only.
        String[] optional = {"ROTATION"};

        int frameCount = -1;
        if (checkRequiredMediaMetadata(mediaMetadata, required)) {
            return -1;
        }
        checkRequiredMediaMetadata(mediaMetadata, optional);

        // Confirm that metadata values are valid.
        try {
            int frameWidth = Integer.parseInt(mediaMetadata.get("FRAME_WIDTH"));
            int frameHeight = Integer.parseInt(mediaMetadata.get("FRAME_HEIGHT"));
            frameCount = Integer.parseInt(mediaMetadata.get("FRAME_COUNT"));
            int duration = Integer.parseInt(mediaMetadata.get("DURATION"));
            if (mediaMetadata.containsKey("ROTATION")) {
                int rotation = Integer.parseInt(mediaMetadata.get("ROTATION"));
            }
            double fps = Double.parseDouble(mediaMetadata.get("FPS"));

            // Double check if this inspection is needed or should be removed. Not sure if negative values are ever allowed.
            if (frameCount < 0 || frameWidth < 0 || frameHeight < 0 || duration < 0 || fps < 0) {
                log.warn("Formatting error for video file metadata. Negative frame count, frame size, fps, and/or duration.",
                        mediaMetadata.get("FRAME_WIDTH"), mediaMetadata.get("FRAME_HEIGHT"));
                return -1;
            }

        } catch (NumberFormatException ex) {
            log.warn("Formatting error for video file metadata: {}", ex.toString());
            return -1;
        } catch (NullPointerException ex) {
            log.warn("Formatting error for video file metadata: {}", ex.toString());
            return -1;
        }
        return frameCount;
    }

    private boolean inspectImageMetadata(Map<String, String> mediaMetadata) {
        String[] required = {"FRAME_WIDTH", "FRAME_HEIGHT"};

        // Appears to be an optional metadata output.
        // Double check: if rotation and horizontal flip can be specified without the other present.
        // Also check if rotation and horizontal flip can be optional.
        String[] optional = {"EXIF_ORIENTATION", "ROTATION", "HORIZONTAL_FLIP"};

        if (checkRequiredMediaMetadata(mediaMetadata, required)) {
            return true;
        }
        checkRequiredMediaMetadata(mediaMetadata, optional);

        // Confirm that metadata values are valid.
        try {
            int frameWidth = Integer.parseInt(mediaMetadata.get("FRAME_WIDTH"));
            int frameHeight = Integer.parseInt(mediaMetadata.get("FRAME_HEIGHT"));
            if (mediaMetadata.containsKey("EXIF_ORIENTATION")) {
                int exif = Integer.parseInt(mediaMetadata.get("EXIF_ORIENTATION"));
            }
            if (mediaMetadata.containsKey("ROTATION")) {
                int rotation = Integer.parseInt(mediaMetadata.get("ROTATION"));
            }

            if (frameWidth < 0 || frameHeight < 0) {
                log.warn("Formatting error for video file metadata. Invalid image dimensions ({}, {})",
                        mediaMetadata.get("FRAME_WIDTH"), mediaMetadata.get("FRAME_HEIGHT"));
                return true;
            }

        } catch (NumberFormatException ex) {
            log.warn("Formatting error for video file metadata: {}", ex.toString());
            return true;
        } catch (NullPointerException ex) {
            log.warn("Formatting error for video file metadata: {}", ex.toString());
            return true;
        }
        return false;
    }

    private int inspectAudio(Path localPath,  long jobId, long mediaId, Map<String, String> mediaMetadata)
            throws IOException, TikaException, SAXException {
        // We do not fetch the length of audio files.
        Metadata audioMetadata = generateFFMPEGMetadata(localPath);

        String durationStr = audioMetadata.get("xmpDM:duration");
        if (durationStr == null) {
            inProgressJobs.addError(jobId, mediaId, IssueCodes.MEDIA_INSPECTION,
                                    "Cannot detect audio file duration.");
            return -1;
        }

        int audioMilliseconds = calculateDurationMilliseconds(durationStr);
        if (audioMilliseconds >= 0) {
            mediaMetadata.put("DURATION", Integer.toString(audioMilliseconds));
        }
        return -1;
    }


    // inspectVideo may add the following properties to the transientMedias metadata:
    // FRAME_COUNT, FRAME_HEIGHT, FRAME_WIDTH, FPS, DURATION, ROTATION.
    // The Media's length will be set to FRAME_COUNT.
    private int inspectVideo(Path localPath, long jobId, long mediaId, String mimeType, Map<String, String> mediaMetadata)
            throws IOException, TikaException, SAXException {
        // FRAME_COUNT

        // Use the frame counter native library to calculate the length of videos.
        log.debug("Counting frames in '{}'.", localPath);

        // We can't get the frame count directly from a gif,
        // so iterate over the frames and count them one by one
        boolean isGif = "image/gif".equals(mimeType);
        int retval = new FrameCounter(localPath.toFile()).count(isGif);

        if (retval <= 0) {
            inProgressJobs.addError(jobId, mediaId, IssueCodes.MEDIA_INSPECTION,
                                    "Cannot detect video file length.");
            return -1;
        }

        int frameCount = retval;
        mediaMetadata.put("FRAME_COUNT", Integer.toString(frameCount));

        // FRAME_WIDTH and FRAME_HEIGHT

        Metadata videoMetadata = generateFFMPEGMetadata(localPath);

        String resolutionStr = videoMetadata.get("videoResolution");
        if (resolutionStr == null) {
            inProgressJobs.addError(jobId, mediaId, IssueCodes.MEDIA_INSPECTION,
                                    "Cannot detect video file resolution.");
            return -1;
        }

        String[] resolutionTokens = resolutionStr.split("x");
        int frameWidth = Integer.parseInt(resolutionTokens[0]);
        int frameHeight = Integer.parseInt(resolutionTokens[1]);
        mediaMetadata.put("FRAME_WIDTH", Integer.toString(frameWidth));
        mediaMetadata.put("FRAME_HEIGHT", Integer.toString(frameHeight));

        // FPS

        String fpsStr = videoMetadata.get("xmpDM:videoFrameRate");
        double fps = 0;
        if (fpsStr != null) {
            fps = Double.parseDouble(fpsStr);
            mediaMetadata.put("FPS", Double.toString(fps));
        }

        // DURATION

        int duration = this.calculateDurationMilliseconds(videoMetadata.get("xmpDM:duration"));
        if (duration <= 0 && fps > 0) {
            duration = (int) ((frameCount / fps) * 1000);
        }
        if (duration > 0) {
            mediaMetadata.put("DURATION", Integer.toString(duration));
        }

        // ROTATION

        String rotation = videoMetadata.get("rotation");
        if (rotation != null) {
            mediaMetadata.put("ROTATION", rotation);
        }
        return frameCount;
    }

    private int inspectImage(Path localPath, long jobId, long mediaId, Map<String, String> mediaMetdata)
            throws IOException, TikaException, SAXException {
        Metadata imageMetadata = generateExifMetadata(localPath);

        String widthStr = imageMetadata.get("tiff:ImageWidth"); // jpeg, png
        if (widthStr == null) {
            widthStr = imageMetadata.get("Image Width"); // jpeg, webp
        }
        if (widthStr == null) {
            widthStr = imageMetadata.get("width"); // png
        }

        String heightStr = imageMetadata.get("tiff:ImageLength"); // jpeg, png
        if (heightStr == null) {
            heightStr = imageMetadata.get("Image Height"); // jpeg, webp
        }
        if (heightStr == null) {
            heightStr = imageMetadata.get("height"); // png
        }

        if (widthStr == null || heightStr == null) {
            // As a last resort, load the whole image into memory.
            BufferedImage bimg = ImageIO.read(localPath.toFile());
            if (bimg == null) {
                inProgressJobs.addError(jobId, mediaId, IssueCodes.MEDIA_INSPECTION,
                                        "Cannot detect image file frame size. Cannot read image file.");
                return -1;
            }
            widthStr = Integer.toString(bimg.getWidth());
            heightStr = Integer.toString(bimg.getHeight());
        }
        mediaMetdata.put("FRAME_WIDTH", widthStr);
        mediaMetdata.put("FRAME_HEIGHT", heightStr);

        String orientationStr = imageMetadata.get("tiff:Orientation");
        if (orientationStr != null) {
            mediaMetdata.put("EXIF_ORIENTATION", orientationStr);
            int orientation = Integer.valueOf(orientationStr);
            switch (orientation) {
                case 1:
                    mediaMetdata.put("ROTATION", "0");
                    mediaMetdata.put("HORIZONTAL_FLIP", "FALSE");
                    break;
                case 2:
                    mediaMetdata.put("ROTATION", "0");
                    mediaMetdata.put("HORIZONTAL_FLIP", "TRUE");
                    break;
                case 3:
                    mediaMetdata.put("ROTATION", "180");
                    mediaMetdata.put("HORIZONTAL_FLIP", "FALSE");
                    break;
                case 4:
                    mediaMetdata.put("ROTATION", "180");
                    mediaMetdata.put("HORIZONTAL_FLIP", "TRUE");
                    break;
                case 5:
                    mediaMetdata.put("ROTATION", "90");
                    mediaMetdata.put("HORIZONTAL_FLIP", "TRUE");
                    break;
                case 6:
                    mediaMetdata.put("ROTATION", "90");
                    mediaMetdata.put("HORIZONTAL_FLIP", "FALSE");
                    break;
                case 7:
                    mediaMetdata.put("ROTATION", "270");
                    mediaMetdata.put("HORIZONTAL_FLIP", "TRUE");
                    break;
                case 8:
                    mediaMetdata.put("ROTATION", "270");
                    mediaMetdata.put("HORIZONTAL_FLIP", "FALSE");
                    break;
            }
        }
        return 1;
    }

    private Metadata generateFFMPEGMetadata(Path path) throws IOException, TikaException, SAXException {
        Metadata metadata = new Metadata();
        try (InputStream stream = Preconditions.checkNotNull(TikaInputStream.get(path),
                "Cannot open file '%s'", path)) {
            metadata.set(Metadata.CONTENT_TYPE, ioUtils.getMimeType(path));
            URL url = this.getClass().getClassLoader().getResource("tika-external-parsers.xml");
            Parser parser = ExternalParsersConfigReader.read(url.openStream()).get(0);
            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }
        return metadata;
    }

    private Metadata generateExifMetadata(Path path) throws IOException, TikaException, SAXException {
        Metadata metadata = new Metadata();
        try (InputStream stream = Preconditions.checkNotNull(TikaInputStream.get(path),
                "Cannot open file '%s'", path)) {
            metadata.set(Metadata.CONTENT_TYPE, ioUtils.getMimeType(stream));
            Parser parser = new AutoDetectParser();
            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }
        return metadata;
    }

    private int calculateDurationMilliseconds(String durationStr) {
        if (durationStr != null) {
            String[] durationArray = durationStr.split("\\.|:");
            int hours = Integer.parseInt(durationArray[0]);
            int minutes = Integer.parseInt(durationArray[1]);
            int seconds = Integer.parseInt(durationArray[2]);
            int milliseconds = Integer.parseInt(durationArray[3]);
            milliseconds = milliseconds + 1000 * seconds + 1000 * 60 * minutes + 1000 * 60 * 60 * hours;
            return milliseconds;
        }
        return -1;
    }
}
