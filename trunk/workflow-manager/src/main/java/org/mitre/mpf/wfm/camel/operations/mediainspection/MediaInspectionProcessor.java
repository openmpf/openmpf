/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

/** This processor extracts metadata about the input medium. */
@Component(MediaInspectionProcessor.REF)
public class MediaInspectionProcessor extends WfmProcessor {
	private static final Logger log = LoggerFactory.getLogger(MediaInspectionProcessor.class);
	public static final String REF = "mediaInspectionProcessor";

	@Autowired
	private IoUtils ioUtils;

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Override
	public void wfmProcess(Exchange exchange) throws WfmProcessingException {
		assert exchange.getIn().getBody() != null : "The body must not be null.";
		assert exchange.getIn().getBody(byte[].class) != null : "The body must be convertible to a String.";

		TransientMedia transientMedia = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), TransientMedia.class);
		log.debug("Inspecting Media {}.", transientMedia.getId());

		if(!transientMedia.isFailed()) {
			try {
				URL url = URI.create(transientMedia.getUri()).toURL();
				try (InputStream inputStream = url.openStream()) {
					log.debug("Calculating hash for '{}'.", url);
					transientMedia.setSha256(DigestUtils.sha256Hex(inputStream));
				} catch(IOException ioe) {
					transientMedia.setFailed(true);
					String errorMessage = "Could not calculate the SHA-256 hash for the file due to IOException.";
					transientMedia.setMessage(errorMessage);
					log.error(errorMessage, ioe);
				}

				try {
					String type = (ioUtils.getMimeType(url));
					transientMedia.setType(type);
				} catch(Exception ioe) {
					transientMedia.setFailed(true);
					String errorMessage = "Could not determine the MIME type: " + ioe.getMessage();
					transientMedia.setMessage(errorMessage);
                    log.error(errorMessage, ioe);
				}

				switch(transientMedia.getMediaType()) {
					case AUDIO:
						inspectAudio(url, transientMedia);
						break;

					case VIDEO:
						inspectVideo(url, transientMedia);
						break;

					case IMAGE:
						inspectImage(url, transientMedia);
						break;

					default:
						// DEBUG
						//transientMedia.setFailed(true);
						//transientMedia.setMessage("Unsupported file format.");
                        log.error("transientMedia.getMediaType() = {} is undefined. ", transientMedia.getMediaType());
						break;
				}
			} catch (Exception exception) {
				log.error("[Job {}|*|*] Failed to inspect {} due to an exception.", exchange.getIn().getHeader(MpfHeaders.JOB_ID), transientMedia.getUri(), exception);
				transientMedia.setFailed(true);
				if (exception instanceof TikaException) {
					transientMedia.setMessage("Tika media inspection error: " + exception.getMessage());
				} else {
					transientMedia.setMessage(exception.getMessage());
				}
			}
		} else {
			log.error("[Job {}|*|*] Skipping inspection of Media #{} as it is in an error state.", transientMedia.getId());
		}

		// Copy these headers to the output exchange.
		exchange.getOut().setHeader(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
		exchange.getOut().setHeader(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
		exchange.getOut().setHeader(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));

		exchange.getOut().setBody(jsonUtils.serialize(transientMedia));
		if (transientMedia.isFailed()) {
			redis.setJobStatus(exchange.getIn().getHeader(MpfHeaders.JOB_ID,Long.class), BatchJobStatusType.IN_PROGRESS_ERRORS);
		}
        redis.persistMedia(exchange.getOut().getHeader(MpfHeaders.JOB_ID, Long.class), transientMedia);
	}

	private void inspectAudio(URL url, TransientMedia transientMedia) throws IOException, TikaException, SAXException {
		// We do not fetch the length of audio files.
		Metadata audioMetadata = generateFFMPEGMetadata(url);
		int audioMilliseconds = calculateDurationMilliseconds(audioMetadata.get("xmpDM:duration"));
		if (audioMilliseconds >= 0) {
			transientMedia.addMetadata("DURATION", Integer.toString(audioMilliseconds));
		}
		transientMedia.setLength(-1);
	}

	// inspectVideo may add the following properties to the transientMedias metadata: FRAME_COUNT, FPS, DURATION, ROTATION.
    // The TransientMedias length will be set to FRAME_COUNT.
	private void inspectVideo(URL url, TransientMedia transientMedia) throws IOException, TikaException, SAXException {
		// FRAME_COUNT

		// Use the frame counter native library to calculate the length of videos.
		log.debug("Counting frames in '{}'.", url);

		// We can't get the frame count directly from a gif,
		// so iterate over the frames and count them one by one
		boolean isGif = transientMedia.getType().equals("image/gif");
		int retval = new FrameCounter(url).count(isGif);

		if (retval <= 0) {
			transientMedia.setFailed(true);
			transientMedia.setMessage("Cannot detect video file length.");
			return;
		}

		int frameCount = retval;
		transientMedia.setLength(frameCount);
		transientMedia.addMetadata("FRAME_COUNT", Integer.toString(frameCount));

		// FPS

		Metadata videoMetadata = generateFFMPEGMetadata(url);
		String fpsStr = videoMetadata.get("xmpDM:videoFrameRate");
		double fps = 0;
		if (fpsStr != null) {
			fps = Double.parseDouble(fpsStr);
			transientMedia.addMetadata("FPS", Double.toString(fps));
		}

		// DURATION

		int duration = this.calculateDurationMilliseconds(videoMetadata.get("xmpDM:duration"));
		if (duration <= 0 && frameCount > 0 && fps > 0) {
			duration = (int) ((frameCount / fps) * 1000);
		}
		if (duration > 0) {
			transientMedia.addMetadata("DURATION", Integer.toString(duration));
		}

		// ROTATION

		String rotation = videoMetadata.get("rotation");
		if (rotation != null) {
			transientMedia.addMetadata("ROTATION", rotation);
		}
	}

	private void inspectImage(URL url, TransientMedia transientMedia) throws IOException, TikaException, SAXException {
		Metadata imageMetadata = generateExifMetadata(url);
		if (imageMetadata.get("tiff:Orientation") != null) {
			transientMedia.addMetadata("EXIF_ORIENTATION", imageMetadata.get("tiff:Orientation"));
			int orientation = Integer.valueOf(imageMetadata.get("tiff:Orientation"));
			switch (orientation) {
				case 1:
					transientMedia.addMetadata("ROTATION", "0");
					transientMedia.addMetadata("HORIZONTAL_FLIP", "FALSE");
					break;
				case 2:
					transientMedia.addMetadata("ROTATION", "0");
					transientMedia.addMetadata("HORIZONTAL_FLIP", "TRUE");
					break;
				case 3:
					transientMedia.addMetadata("ROTATION", "180");
					transientMedia.addMetadata("HORIZONTAL_FLIP", "FALSE");
					break;
				case 4:
					transientMedia.addMetadata("ROTATION", "180");
					transientMedia.addMetadata("HORIZONTAL_FLIP", "TRUE");
					break;
				case 5:
					transientMedia.addMetadata("ROTATION", "90");
					transientMedia.addMetadata("HORIZONTAL_FLIP", "TRUE");
					break;
				case 6:
					transientMedia.addMetadata("ROTATION", "90");
					transientMedia.addMetadata("HORIZONTAL_FLIP", "FALSE");
					break;
				case 7:
					transientMedia.addMetadata("ROTATION", "270");
					transientMedia.addMetadata("HORIZONTAL_FLIP", "TRUE");
					break;
				case 8:
					transientMedia.addMetadata("ROTATION", "270");
					transientMedia.addMetadata("HORIZONTAL_FLIP", "FALSE");
					break;

			}
		}
		transientMedia.setLength(1);
	}

	private Metadata generateFFMPEGMetadata(URL mediaUrl) throws IOException, TikaException, SAXException {
		Metadata metadata = new Metadata();
		try (InputStream stream = Preconditions.checkNotNull(TikaInputStream.get(mediaUrl), "Cannot open file '%s'", mediaUrl)) {
			metadata.set(Metadata.CONTENT_TYPE, ioUtils.getMimeType(mediaUrl));
			URL parsersUrl = this.getClass().getClassLoader().getResource("tika-external-parsers.xml");
			Parser parser = ExternalParsersConfigReader.read(parsersUrl.openStream()).get(0);
			parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
		}
		return metadata;
	}

	private Metadata generateExifMetadata(URL mediaUrl) throws IOException, TikaException, SAXException {
		Metadata metadata = new Metadata();
		try (InputStream stream = Preconditions.checkNotNull(TikaInputStream.get(mediaUrl), "Cannot open file '%s'", mediaUrl)) {
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
