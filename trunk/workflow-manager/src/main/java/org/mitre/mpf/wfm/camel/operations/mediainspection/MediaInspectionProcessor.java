/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.MediaTypeUtils;
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
		TransientMedia transientMedia = inProgressJobs.getJob(jobId).getMedia(mediaId);

		if(!transientMedia.isFailed()) {
			// Any request to pull a remote file should have already populated the local uri.
			assert transientMedia.getLocalPath() != null : "Media being processed by the MediaInspectionProcessor must have a local URI associated with them.";

			try {
				Path localPath = transientMedia.getLocalPath();
				String sha = null;
				String mimeType = null;

				try (InputStream inputStream = Files.newInputStream(localPath)) {
					log.debug("Calculating hash for '{}'.", localPath);
                    sha = DigestUtils.sha256Hex(inputStream);
				} catch(IOException ioe) {
					String errorMessage = "Could not calculate the SHA-256 hash for the file due to IOException: "
                                            + ioe;
					inProgressJobs.addMediaError(jobId, mediaId, errorMessage);
					log.error(errorMessage, ioe);
				}

				try {
					mimeType = ioUtils.getMimeType(localPath);
				} catch(IOException ioe) {
					String errorMessage = "Could not determine the MIME type for the media due to IOException: "
                                            + ioe;
					inProgressJobs.addMediaError(jobId, mediaId, errorMessage);
                    log.error(errorMessage, ioe);
				}

				Map<String, String> mediaMetadata = new HashMap<>();
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
						// DEBUG
						//transientMedia.setFailed(true);
						//transientMedia.setMessage("Unsupported file format.");
                        log.error("transientMedia.getMediaType() = {} is undefined. ", transientMedia.getMediaType());
						break;
				}
				inProgressJobs.addMediaInspectionInfo(jobId, mediaId, sha, mimeType, length, mediaMetadata);
			} catch (Exception exception) {
				log.error("[Job {}|*|*] Failed to inspect {} due to an exception.", exchange.getIn().getHeader(MpfHeaders.JOB_ID), transientMedia.getLocalPath(), exception);
				if (exception instanceof TikaException) {
					inProgressJobs.addMediaError(jobId, mediaId, "Tika media inspection error: " + exception.getMessage());
				} else {
					inProgressJobs.addMediaError(jobId, mediaId, exception.getMessage());
				}
			}
		} else {
			log.error("[Job {}|*|*] Skipping inspection of Media #{} as it is in an error state.", transientMedia.getId());
		}

		// Copy these headers to the output exchange.
		exchange.getOut().setHeader(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
		exchange.getOut().setHeader(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
		exchange.getOut().setHeader(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));
		exchange.getOut().setHeader(MpfHeaders.JOB_ID, jobId);
		exchange.getOut().setHeader(MpfHeaders.MEDIA_ID, mediaId);
		if (transientMedia.isFailed()) {
			inProgressJobs.setJobStatus(jobId, BatchJobStatusType.ERROR);
		}
	}

	private int inspectAudio(Path localPath,  long jobId, long mediaId, Map<String, String> mediaMetadata)
			throws IOException, TikaException, SAXException {
		// We do not fetch the length of audio files.
		Metadata audioMetadata = generateFFMPEGMetadata(localPath);

		String durationStr = audioMetadata.get("xmpDM:duration");
		if (durationStr == null) {
			inProgressJobs.addMediaError(jobId, mediaId, "Cannot detect audio file duration.");
			return -1;
		}

		int audioMilliseconds = calculateDurationMilliseconds(durationStr);
		if (audioMilliseconds >= 0) {
			mediaMetadata.put("DURATION", Integer.toString(audioMilliseconds));
		}
		return -1;
	}


	// inspectVideo may add the following properties to the transientMedias metadata: FRAME_COUNT, FPS, DURATION, ROTATION.
    // The TransientMedias length will be set to FRAME_COUNT.
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
			inProgressJobs.addMediaError(jobId, mediaId, "Cannot detect video file length.");
			return -1;
		}

		int frameCount = retval;
		mediaMetadata.put("FRAME_COUNT", Integer.toString(frameCount));

		// FRAME_WIDTH and FRAME_HEIGHT

		Metadata videoMetadata = generateFFMPEGMetadata(localPath);

		String resolutionStr = videoMetadata.get("videoResolution");
		if (resolutionStr == null) {
			inProgressJobs.addMediaError(jobId, mediaId, "Cannot detect video file resolution.");
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
				inProgressJobs.addMediaError(jobId, mediaId,
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
