/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.mitre.mpf.heif.HeifConverter;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.CustomJpegParser;
import org.mitre.mpf.wfm.util.FrameTimeInfo;
import org.mitre.mpf.wfm.util.FrameTimeInfoBuilder;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.MediaTypeUtils;
import org.mitre.mpf.wfm.util.PngDefry;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.base.Preconditions;

/**
 * Class used to extract metadata about a piece of media. Media inspection will be skipped if the appropriate media
 * metadata properties are provided as job inputs.
 * <p>
 * If a piece of media with a "video/*" MIME type has a video stream we will treat it as a VIDEO data type. Otherwise,
 * we determine if we can treat it as an AUDIO data type.
 * <p>
 * If a piece of media with an "audio/*" MIME type (or "video/*" MIME type without an video stream) has an audio
 * stream we will treat it as an AUDIO data type. Otherwise, we will treat it as an UNKNOWN data type.
 * <p>
 * To summarize, fallback is performed in this order: VIDEO --> AUDIO --> UNKNOWN. This is to handle cases where
 * a video container format can contain zero or more video/audio/subtitle/attachment/data streams.
 * <p>
 * There is no fallback for the IMAGE data type. "image/*" MIME types are not containers like "video/*" MIME types.
 */
@Component
public class MediaInspectionHelper {
    private static final Logger LOG = LoggerFactory.getLogger(MediaInspectionHelper.class);

    private final PropertiesUtil _propertiesUtil;

    private final InProgressBatchJobsService _inProgressJobs;

    private final IoUtils _ioUtils;

    private final MediaTypeUtils _mediaTypeUtils;

    private final MediaMetadataValidator _mediaMetadataValidator;

    private final FfprobeMetadataExtractor _ffprobeMetadataExtactor;

    private final TikaConfig _tikaConfig;

    @Inject
    public MediaInspectionHelper(
            PropertiesUtil propertiesUtil, InProgressBatchJobsService inProgressJobs,
            IoUtils ioUtils,
            MediaTypeUtils mediaTypeUtils,
            MediaMetadataValidator mediaMetadataValidator,
            FfprobeMetadataExtractor ffprobeMetadataExtractor)
            throws TikaException, IOException, SAXException {
        _propertiesUtil = propertiesUtil;
        _inProgressJobs = inProgressJobs;
        _ioUtils = ioUtils;
        _mediaTypeUtils = mediaTypeUtils;
        _mediaMetadataValidator = mediaMetadataValidator;
        _ffprobeMetadataExtactor = ffprobeMetadataExtractor;
        _tikaConfig = new TikaConfig(getClass().getResource("/tika.config"));
    }

    public void inspectMedia(Media media, long jobId) throws WfmProcessingException {
        if (media.isFailed()) {
            LOG.error("Skipping media {}. It is in an error state.", media.getId());
            return;
        }
        if (_mediaMetadataValidator.skipInspection(jobId, media)) {
            return;
        }

        long mediaId = media.getId();
        String sha = null;
        String mimeType = null;
        int length = -1;
        MediaType mediaType = MediaType.UNKNOWN;

        Map<String, String> mediaMetadata = new HashMap<>();
        if (media.isDerivative()) {
            mediaMetadata.putAll(media.getMetadata());
        }

        try {
            Path localPath = media.getLocalPath();

            try (InputStream inputStream = Files.newInputStream(localPath)) {
                LOG.debug("Calculating hash for '{}'.", localPath);
                sha = DigestUtils.sha256Hex(inputStream);
            } catch (IOException ioe) {
                String errorMessage = "Could not calculate the SHA-256 hash for the file due to IOException: "
                        + ioe;
                _inProgressJobs.addError(jobId, mediaId, IssueCodes.ARTIFACT_EXTRACTION, errorMessage);
                LOG.error(errorMessage, ioe);
            }

            mimeType = media.getMimeType().orElseGet(() -> _ioUtils.getMimeType(localPath));

            mediaMetadata.put("MIME_TYPE", mimeType);
            mediaType = _mediaTypeUtils.parse(mimeType);
            FfprobeMetadata ffprobeMetadata = null;

            var job = _inProgressJobs.getJob(jobId);
            switch (mediaType) {
                case IMAGE:
                    length = inspectImage(job, media, mediaMetadata);
                    break;

                case VIDEO:
                    ffprobeMetadata = _ffprobeMetadataExtactor.getAudioVideoMetadata(job, media);
                    if (ffprobeMetadata.video().isPresent()) {
                        length = inspectVideo(
                                localPath, jobId, mediaId, mediaMetadata,
                                ffprobeMetadata.video().get());
                        break;
                    }
                    _inProgressJobs.addWarning(
                            jobId, mediaId, IssueCodes.MISSING_VIDEO_STREAM,
                            "Cannot detect video resolution. Media may be missing video stream.");
                    mediaType = MediaType.AUDIO;
                    // fall through

                case AUDIO:
                    if (ffprobeMetadata == null) {
                        ffprobeMetadata = _ffprobeMetadataExtactor.getAudioVideoMetadata(job, media);
                    }
                    if (ffprobeMetadata.audio().isPresent()) {
                        length = inspectAudio(
                                jobId, mediaId, mediaMetadata, ffprobeMetadata.audio().get());
                        break;
                    }
                    _inProgressJobs.addWarning(jobId, mediaId, IssueCodes.MISSING_AUDIO_STREAM,
                                                "Cannot detect audio file sample rate. Media may be missing audio stream.");
                    mediaType = MediaType.UNKNOWN;
                    // fall through

                    default:
                        LOG.warn("Treating job {}'s media {} as UNKNOWN data type.", jobId, mediaId);
                        break;
                }
            }
            catch (Exception e) {
                var message = "Failed to inspect %s due to an exception: %s"
                        .formatted(media.getUri(), e);
                LOG.error(message, e);
                var issueCode = e instanceof MediaInspectionException mediaInspectionEx
                        ? mediaInspectionEx.getIssueCode()
                        : IssueCodes.MEDIA_INSPECTION;
                _inProgressJobs.addError(jobId, mediaId, issueCode, message);
            }

        _inProgressJobs.addMediaInspectionInfo(jobId, mediaId, sha, mediaType, mimeType, length, mediaMetadata);
        LOG.info("Media with URI {} (id={}) has data type {} and mime type {}.",
                media.getUri(), media.getId(), media.getType(), media.getMimeType());
    }

    private int inspectAudio(
            long jobId, long mediaId, Map<String, String> mediaMetadata,
            FfprobeMetadata.Audio ffprobeMetadata) {
        if (ffprobeMetadata.durationMs().isPresent()) {
            var duration = ffprobeMetadata.durationMs().getAsLong();
            mediaMetadata.put("DURATION", Long.toString(duration));
        }
        else {
            _inProgressJobs.addWarning(
                    jobId, mediaId, IssueCodes.MEDIA_INSPECTION,
                    "Cannot detect audio file duration.");
        }
        return -1;
    }

    private int inspectVideo(Path localPath, long jobId, long mediaId,
                             Map<String, String> mediaMetadata,
                             FfprobeMetadata.Video ffprobeMetadata) {

        mediaMetadata.put("FRAME_WIDTH", Integer.toString(ffprobeMetadata.width()));
        mediaMetadata.put("FRAME_HEIGHT", Integer.toString(ffprobeMetadata.height()));
        mediaMetadata.put("FPS", String.valueOf(ffprobeMetadata.fps().toDouble()));
        mediaMetadata.put("ROTATION", String.valueOf(ffprobeMetadata.rotation()));

        FrameTimeInfo frameTimeInfo;
        try {
            frameTimeInfo = FrameTimeInfoBuilder.getFrameTimeInfo(localPath, ffprobeMetadata);
        }
        catch (MediaInspectionException e) {
            if (ffprobeMetadata.frameCount().isPresent()) {
                LOG.warn(e.getMessage(), e);
                frameTimeInfo = FrameTimeInfo.forVariableFrameRateWithEstimatedTimes(
                        ffprobeMetadata.fps());
            }
            else {
                LOG.error(e.getMessage(), e);
                _inProgressJobs.addError(jobId, mediaId, IssueCodes.FRAME_COUNT, e.getMessage());
                return -1;
            }
        }
        if (frameTimeInfo.requiresTimeEstimation()) {
            _inProgressJobs.addWarning(
                    jobId, mediaId, IssueCodes.MEDIA_INSPECTION,
                    "One or more presentation timestamp (PTS) values were missing from the " +
                            "video file. Some times in the output object will be estimated.");
        }

        if (frameTimeInfo.hasConstantFrameRate()) {
            mediaMetadata.put("HAS_CONSTANT_FRAME_RATE", "true");
        }

        int frameCount;
        if (frameTimeInfo.getExactFrameCount().isPresent()) {
            frameCount = frameTimeInfo.getExactFrameCount().getAsInt();
            mediaMetadata.put("FRAME_COUNT", Integer.toString(frameCount));
        }
        else if (ffprobeMetadata.frameCount().isPresent()) {
            frameCount = (int) ffprobeMetadata.frameCount().getAsLong();
            mediaMetadata.put("FRAME_COUNT", Integer.toString(frameCount));
        }
        else {
            _inProgressJobs.addError(
                    jobId, mediaId, IssueCodes.FRAME_COUNT, "Failed to get frame count.");
            return -1;
        }

        if (ffprobeMetadata.durationMs().isPresent()) {
            mediaMetadata.put(
                    "DURATION", Long.toString(ffprobeMetadata.durationMs().getAsLong()));
        }
        else if (frameTimeInfo.getEstimatedDuration().isPresent()) {
            mediaMetadata.put(
                    "DURATION", Integer.toString(frameTimeInfo.getEstimatedDuration().getAsInt()));
        }

        _inProgressJobs.addFrameTimeInfo(jobId, mediaId, frameTimeInfo);
        return frameCount;
    }

    private int inspectImage(BatchJob job, Media media, Map<String, String> mediaMetadata)
            throws IOException, TikaException, SAXException {
        String mimeType = mediaMetadata.get("MIME_TYPE");

        Path mediaPath;
        if (mimeType.equalsIgnoreCase("image/heic") || mimeType.equalsIgnoreCase("image/avif")) {
            var tempDir = _propertiesUtil.getTemporaryMediaDirectory().toPath();
            mediaPath = tempDir.resolve(UUID.randomUUID() + ".png");
            LOG.info("{} is HEIF image. It will be converted to PNG.", media.getLocalPath());
            HeifConverter.convert(media.getLocalPath(), mediaPath);
            _inProgressJobs.addConvertedMediaPath(job.getId(), media.getId(), mediaPath);
        }
        else if (mimeType.equalsIgnoreCase("image/png")
                && PngDefry.isCrushed(media.getLocalPath())) {
            LOG.info(
                "Detected that \"{}\" is an Apple-optimized PNG. It will be converted to a " +
                "regular PNG.", media.getLocalPath());
            var defriedPath = PngDefry.defry(
                    media.getLocalPath(), _propertiesUtil.getTemporaryMediaDirectory().toPath());
            _inProgressJobs.addConvertedMediaPath(job.getId(), media.getId(), defriedPath);
            mediaPath = defriedPath;
        }
        else if (mimeType.equalsIgnoreCase("image/webp")) {
            mediaPath = WebpUtil.fixLengthIfNeeded(
                    media.getLocalPath(), _propertiesUtil.getTemporaryMediaDirectory().toPath());
            if (!mediaPath.equals(media.getLocalPath())) {
                _inProgressJobs.addConvertedMediaPath(job.getId(), media.getId(), mediaPath);
            }
        }
        else {
            mediaPath = media.getLocalPath();
        }

        var ffprobeMetadata = _ffprobeMetadataExtactor.getImageMetadata(job, media);
        if (ffprobeMetadata.width().isPresent()
                && ffprobeMetadata.height().isPresent()
                && ffprobeMetadata.exifOrientation().isPresent()) {
            mediaMetadata.put(
                    "FRAME_WIDTH", Integer.toString(ffprobeMetadata.width().getAsInt()));
            mediaMetadata.put(
                    "FRAME_HEIGHT", Integer.toString(ffprobeMetadata.height().getAsInt()));
            addExifOrientationInfo(ffprobeMetadata.exifOrientation().getAsInt(), mediaMetadata);
            return 1;
        }

        Metadata tikaMetadata = generateExifMetadata(mediaPath, mimeType);

        String widthStr;
        if (ffprobeMetadata.width().isPresent()) {
            widthStr = Integer.toString(ffprobeMetadata.width().getAsInt());
        }
        else {
            widthStr = firstNonNullValue(
                    tikaMetadata,
                    "tiff:ImageWidth", // jpeg, png
                    "Image Width", // jpeg, webp
                    "width"); // png
        }

        String heightStr;
        if (ffprobeMetadata.height().isPresent()) {
            heightStr = Integer.toString(ffprobeMetadata.height().getAsInt());
        }
        else {
            heightStr = firstNonNullValue(
                    tikaMetadata,
                    "tiff:ImageLength", // jpeg, png
                    "Image Height", // jpeg, webp
                    "height"); //png
        }

        if (widthStr == null || heightStr == null) {
            // As a last resort, load the whole image into memory.
            BufferedImage bimg = ImageIO.read(mediaPath.toFile());
            if (bimg == null) {
                _inProgressJobs.addError(
                        job.getId(), media.getId(), IssueCodes.MEDIA_INSPECTION,
                        "Cannot detect image file frame size. Cannot read image file.");
                return -1;
            }
            widthStr = Integer.toString(bimg.getWidth());
            heightStr = Integer.toString(bimg.getHeight());
        }
        mediaMetadata.put("FRAME_WIDTH", widthStr);
        mediaMetadata.put("FRAME_HEIGHT", heightStr);

        if (ffprobeMetadata.exifOrientation().isPresent()) {
            addExifOrientationInfo(ffprobeMetadata.exifOrientation().getAsInt(), mediaMetadata);
            return 1;
        }

        String orientationStr = tikaMetadata.get("tiff:Orientation");
        if (orientationStr != null) {
            mediaMetadata.put("EXIF_ORIENTATION", orientationStr);
            int orientation = Integer.parseInt(orientationStr);
            addExifOrientationInfo(orientation, mediaMetadata);
        }
        return 1;
    }


    private static void addExifOrientationInfo(
            int exifOrientation, Map<String, String> mediaMetadata) {

        mediaMetadata.put("EXIF_ORIENTATION", Integer.toString(exifOrientation));

        var rotation = switch (exifOrientation) {
            case 3, 4 -> "180";
            case 5, 6 -> "90";
            case 7, 8 -> "270";
            default -> "0";
        };
        mediaMetadata.put("ROTATION", rotation);

        var flip = switch (exifOrientation) {
            case 1, 3, 6, 8 -> "FALSE";
            default -> "TRUE";
        };
        mediaMetadata.put("HORIZONTAL_FLIP", flip);
    }

    private static String firstNonNullValue(Metadata metadata, String...keys) {
        return Stream.of(keys)
            .map(metadata::get)
            .filter(Objects::nonNull)
            .findAny()
            .orElse(null);
    }


    private Metadata generateExifMetadata(Path path, String mimeType) throws IOException, TikaException, SAXException {
        Metadata metadata = new Metadata();
        try (InputStream stream = Preconditions.checkNotNull(TikaInputStream.get(path),
                "Cannot open file '%s'", path)) {
            metadata.set(Metadata.CONTENT_TYPE, mimeType);

            Parser parser = mimeType.equals("image/jpeg")
                    ? new CustomJpegParser()
                    : new AutoDetectParser(_tikaConfig);

            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }
        return metadata;
    }
}
