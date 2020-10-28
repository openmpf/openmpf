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

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.util.MediaTypeUtils;
import org.mitre.mpf.wfm.util.PngDefry;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class used to determine if media inspection should be skipped when media metadata properties are provided in the
 * job request. Performs some basic property validation.
 */
@Component
public class MediaMetadataValidator {
    private static final Logger log = LoggerFactory.getLogger(MediaMetadataValidator.class);

    private final InProgressBatchJobsService inProgressJobs;

    @Inject
    public MediaMetadataValidator(InProgressBatchJobsService inProgressJobs) {
        this.inProgressJobs = inProgressJobs;
    }

    public boolean skipInspection(long jobId, Media media) {
        var mediaId = media.getId();
        var providedMetadata = media.getProvidedMetadata();

        if (providedMetadata.isEmpty()) {
            return false;
        }

        try {
            var requiredMetadata = Set.of("MIME_TYPE", "MEDIA_HASH");
            validateMetadata(providedMetadata, requiredMetadata, Set.of(), false);

            String sha = providedMetadata.get("MEDIA_HASH");
            String mimeType = TextUtils.trim(providedMetadata.get("MIME_TYPE"));
            if (isConversionNeeded(mimeType, media, jobId)) {
                return false;
            }

            int length = -1;
            switch (MediaTypeUtils.parse(mimeType)) {
                case AUDIO:
                    validateAudioMetadata(providedMetadata);
                    break;
                case VIDEO:
                    validateVideoMetadata(providedMetadata);
                    length = Integer.parseInt(providedMetadata.get("FRAME_COUNT"));
                    break;
                case IMAGE:
                    validateImageMetadata(providedMetadata);
                    length = 1;
                    break;
                default:
                    // UNKNOWN data type processed generically
                    break;
            }

            log.info("Skipping media inspection for job {}'s media {}.", jobId, mediaId);
            inProgressJobs.addMediaInspectionInfo(jobId, mediaId, sha, mimeType, length, providedMetadata);
            return true;
        }
        catch (WfmProcessingException e) {
            inProgressJobs.addWarning(jobId, mediaId, IssueCodes.MEDIA_INSPECTION,
                    "Provided media metadata not valid: " + e.getMessage() + " Cannot skip media inspection.");
            return false;
        }
    }

    private boolean isConversionNeeded(String mimeType, Media media, long jobId) {
        if (mimeType.equalsIgnoreCase("image/heic")) {
            inProgressJobs.addWarning(jobId, media.getId(), IssueCodes.MEDIA_INSPECTION,
                String.format("Cannot skip media inspection for media id %s because it is a " +
                              "HEIC image and requires conversion before further processing.",
                              media.getId()));
            return true;
        }

        if (mimeType.equalsIgnoreCase("image/png") && PngDefry.isCrushed(media.getLocalPath())) {
            inProgressJobs.addWarning(jobId, media.getId(), IssueCodes.MEDIA_INSPECTION,
                String.format("Cannot skip media inspection for media id %s because it is " +
                              "an Apple-optimized PNG and requires conversion before further " +
                              "processing.", media.getId()));
            return true;
        }
        return false;
    }

    private static void validateAudioMetadata(Map<String, String> mediaMetadata) {
        var requiredMetadata = Set.of("DURATION");
        validateMetadata(mediaMetadata, requiredMetadata, requiredMetadata, false);
    }

    private static void validateVideoMetadata(Map<String, String> mediaMetadata) {
        var requiredMetadata = Set.of("FRAME_WIDTH", "FRAME_HEIGHT", "FRAME_COUNT", "FPS", "DURATION");
        validateMetadata(mediaMetadata, requiredMetadata, requiredMetadata, true);
    }

    private static void validateImageMetadata(Map<String, String> mediaMetadata) {
        var requiredMetadata = Set.of("FRAME_WIDTH", "FRAME_HEIGHT");
        validateMetadata(mediaMetadata, requiredMetadata, requiredMetadata, true);
    }

    private static void validateMetadata(Map<String, String> mediaMetadata, Set<String> requiredMetadata,
                                         Set<String> numericMetadata, boolean validateOrientation) {
        String missingError = requiredMetadata.stream()
                .filter(key -> !mediaMetadata.containsKey(key) || mediaMetadata.get(key).isBlank())
                .collect(Collectors.joining(", "));

        if (!missingError.isEmpty()) {
            throw new WfmProcessingException("Missing required metadata: " + missingError + '.');
        }

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
            throw new WfmProcessingException(invalidError.trim());
        }
    }

    private static boolean isValidRotation(String value) {
        try {
            double rotation = Double.parseDouble(value.trim());
            if (rotation >= 0 && rotation < 360) {
                return true;
            }
        } catch (NumberFormatException ex) {
            log.warn("Not a valid number: " + value);
        }
        return false;
    }

    private static boolean isPositive(String value) {
        try {
            return Double.parseDouble(value.trim()) > 0;
        } catch (NumberFormatException ex) {
            log.warn("Not a valid number: " + value);
            return false;
        }
    }

    private static boolean isBoolean(String value) {
        return value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("FALSE");
    }
}
