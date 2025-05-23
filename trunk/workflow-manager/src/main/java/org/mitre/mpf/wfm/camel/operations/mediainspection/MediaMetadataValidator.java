/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.FrameTimeInfo;
import org.mitre.mpf.wfm.util.MediaTypeUtils;
import org.mitre.mpf.wfm.util.PngDefry;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class used to determine if media inspection can be skipped when media metadata properties are provided in the
 * job request. Performs some basic property validation.
 *
 * Every attempt is made to avoid performing media inspection. When one or more required media metadata properties are
 * missing we fall back to another data type in this order: VIDEO --> AUDIO --> UNKNOWN. This is to handle cases where
 * a video container format can contain zero or more video/audio/subtitle/attachment/data streams.
 *
 * There is no fallback for the IMAGE data type. "image/*" MIME types are not containers like "video/*" MIME types.
 *
 * If one invalid media metadata property is identified then we do not trust any of the properties. In that case fallback
 * is not performed and media inspection cannot be skipped.
 */
@Component
public class MediaMetadataValidator {
    private static final Logger LOG = LoggerFactory.getLogger(MediaMetadataValidator.class);

    // MIME_TYPE and MEDIA_HASH are common and checked first
    private static final Set<String> REQUIRED_AUDIO_METADATA = Set.of("DURATION");

    private static final Set<String> REQUIRED_VIDEO_METADATA = Set.of(
            "FRAME_WIDTH", "FRAME_HEIGHT", "FRAME_COUNT", "FPS", "DURATION");

    private static final Set<String> REQUIRED_IMAGE_METADATA
            = Set.of("FRAME_WIDTH", "FRAME_HEIGHT");

    private final InProgressBatchJobsService _inProgressJobs;

    private final MediaTypeUtils _mediaTypeUtils;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;


    @Inject
    public MediaMetadataValidator(
            InProgressBatchJobsService inProgressJobs,
            MediaTypeUtils mediaTypeUtils,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        _inProgressJobs = inProgressJobs;
        _mediaTypeUtils = mediaTypeUtils;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }

    public boolean skipInspection(long jobId, Media media) {
        if (media.isDerivative()) {
            return false;
        }

        var job = _inProgressJobs.getJob(jobId);
        var shouldSkipInspection = Boolean.parseBoolean(
                _aggregateJobPropertiesUtil.getValue(
                        MpfConstants.SKIP_MEDIA_INSPECTION, job, media));
        if (!shouldSkipInspection) {
            return false;
        }

        long mediaId = media.getId();

        try {
            var mediaMetadata = media.getProvidedMetadata();
            checkForMissingMetadata(mediaMetadata, Set.of("MIME_TYPE", "MEDIA_HASH"), null);

            String mimeType = TextUtils.trim(mediaMetadata.get("MIME_TYPE"));
            if (isConversionNeeded(mimeType, media, jobId)) {
                return false;
            }

            int length = -1;
            String sha = mediaMetadata.get("MEDIA_HASH");
            MediaType mediaType = _mediaTypeUtils.parse(mimeType);

            switch (mediaType) {
                case IMAGE:
                    checkForMissingMetadata(mediaMetadata, REQUIRED_IMAGE_METADATA, "image");
                    checkMetadataTypes(mediaMetadata, REQUIRED_IMAGE_METADATA, false);
                    length = 1;
                    break;

                case VIDEO:
                    boolean missingVideoMetadata = false;
                    try {
                        checkForMissingMetadata(mediaMetadata, REQUIRED_VIDEO_METADATA, "video");
                    } catch (WfmProcessingException e) {
                        _inProgressJobs.addWarning(jobId, mediaId, IssueCodes.MEDIA_INSPECTION, e.getMessage());
                        missingVideoMetadata = true;
                        mediaType = MediaType.AUDIO;
                    }
                    if (!missingVideoMetadata) {
                        checkMetadataTypes(mediaMetadata, REQUIRED_VIDEO_METADATA, true);
                        length = Integer.parseInt(mediaMetadata.get("FRAME_COUNT"));
                        _inProgressJobs.addFrameTimeInfo(
                                jobId, mediaId, getFrameTimeInfo(mediaMetadata, length));
                        break;
                    }
                    // fall through

                case AUDIO:
                    boolean missingAudioMetadata = false;
                    try {
                        checkForMissingMetadata(mediaMetadata, REQUIRED_AUDIO_METADATA, "audio");
                    } catch (WfmProcessingException e) {
                        _inProgressJobs.addWarning(jobId, mediaId, IssueCodes.MEDIA_INSPECTION, e.getMessage());
                        missingAudioMetadata = true;
                        mediaType = MediaType.UNKNOWN;
                    }
                    if (!missingAudioMetadata) {
                        checkMetadataTypes(mediaMetadata, REQUIRED_AUDIO_METADATA, true);
                        length = -1;
                        break;
                    }
                    // fall through

                default:
                    LOG.warn("Treating job {}'s media {} as UNKNOWN data type.", jobId, mediaId);
                    break;
            }

            LOG.info("Skipping media inspection for job {}'s media {}.", jobId, mediaId);
            _inProgressJobs.addMediaInspectionInfo(jobId, mediaId, sha, mediaType, mimeType, length, mediaMetadata);
            return true;
        } catch (WfmProcessingException e) {
            _inProgressJobs.addWarning(jobId, mediaId, IssueCodes.MEDIA_INSPECTION,
                                       e.getMessage() + " Cannot skip media inspection.");
        }

        return false;
    }

    private boolean isConversionNeeded(String mimeType, Media media, long jobId) {
        if (mimeType.equalsIgnoreCase("image/heic") || mimeType.equalsIgnoreCase("image/avif")) {
            _inProgressJobs.addWarning(jobId, media.getId(), IssueCodes.MEDIA_INSPECTION,
                                       String.format("Cannot skip media inspection for media id %s because it is a " +
                                  "HEIF image and requires conversion before further processing.",
                                  media.getId()));
            return true;
        }

        if (mimeType.equalsIgnoreCase("image/png") && PngDefry.isCrushed(media.getLocalPath())) {
            _inProgressJobs.addWarning(jobId, media.getId(), IssueCodes.MEDIA_INSPECTION,
                                       String.format("Cannot skip media inspection for media id %s because it is " +
                                  "an Apple-optimized PNG and requires conversion before further " +
                                  "processing.", media.getId()));
            return true;
        }

        return false;
    }

    private static void checkForMissingMetadata(Map<String, String> mediaMetadata, Set<String> requiredMetadata,
                                                String type) {
        String missingError = requiredMetadata.stream()
                .filter(key -> StringUtils.isBlank(mediaMetadata.get(key)))
                .collect(Collectors.joining(", "));

        if (!missingError.isEmpty()) {
            throw new WfmProcessingException("Missing required " + (type != null ? type + ' ' : "" ) + "metadata: " +
                    missingError + '.');
        }
    }

    private static void checkMetadataTypes(Map<String, String> mediaMetadata, Set<String> numericMetadata,
                                           boolean validateOrientation) {
        String invalidError = "";

        String numericError = numericMetadata.stream()
                .filter(key -> mediaMetadata.containsKey(key) && !isPositive(mediaMetadata.get(key)))
                .map(key -> key + '=' + mediaMetadata.get(key))
                .collect(Collectors.joining(", "));
        if (!numericError.isEmpty()) {
            invalidError += " The following must be a valid number > 0: " + numericError + '.';
        }

        if (validateOrientation) {
            String horzFlip = mediaMetadata.get("HORIZONTAL_FLIP");
            if (horzFlip != null && !isBoolean(horzFlip)) {
                invalidError += " The following must be \"TRUE\" or \"FALSE\": " + horzFlip + '.';
            }

            String rotation = mediaMetadata.get("ROTATION");
            if (rotation != null && !isValidRotation(rotation)) {
                invalidError += " Invalid ROTATION of " + rotation + ". Must be in the range [0, 360).";
            }

            // don't care about EXIF_ORIENTATION
        }

        if (!invalidError.isEmpty()) {
            throw new WfmProcessingException("Provided media metadata not valid: " + invalidError.trim());
        }
    }

    private static boolean isValidRotation(String value) {
        try {
            double rotation = Double.parseDouble(value.trim());
            if (rotation >= 0 && rotation < 360) {
                return true;
            }
        } catch (NumberFormatException ex) {
            LOG.warn("Not a valid number: " + value);
        }
        return false;
    }

    private static boolean isPositive(String value) {
        try {
            return Double.parseDouble(value.trim()) > 0;
        } catch (NumberFormatException ex) {
            LOG.warn("Not a valid number: " + value);
            return false;
        }
    }

    private static boolean isBoolean(String value) {
        return value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("FALSE");
    }


    private static FrameTimeInfo getFrameTimeInfo(
            Map<String, String> mediaMetadata, int frameCount) {
        boolean hasConstantFrameRate = Boolean.parseBoolean(
                mediaMetadata.get("HAS_CONSTANT_FRAME_RATE"));
        double fps = Double.parseDouble(mediaMetadata.get("FPS"));
        if (hasConstantFrameRate) {
            return FrameTimeInfo.forConstantFrameRate(fps, OptionalInt.of(0), frameCount);
        }
        else {
            return FrameTimeInfo.forVariableFrameRateWithEstimatedTimes(fps, frameCount);
        }
    }
}
