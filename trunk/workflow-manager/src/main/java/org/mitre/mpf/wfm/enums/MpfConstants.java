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

package org.mitre.mpf.wfm.enums;

public class MpfConstants {
    public static final String
            FRAME_RATE_CAP_PROPERTY = "FRAME_RATE_CAP",
            MEDIA_SAMPLING_INTERVAL_PROPERTY = "FRAME_INTERVAL",
            QUALITY_SELECTION_PROPERTY = "QUALITY_SELECTION_PROPERTY",
            QUALITY_THRESHOLD_PROPERTY = "QUALITY_SELECTION_THRESHOLD",
            MINIMUM_GAP_BETWEEN_SEGMENTS = "MIN_GAP_BETWEEN_SEGMENTS",
            TARGET_SEGMENT_LENGTH_PROPERTY = "TARGET_SEGMENT_LENGTH",
            VFR_TARGET_SEGMENT_LENGTH_PROPERTY = "VFR_TARGET_SEGMENT_LENGTH",
            MINIMUM_SEGMENT_LENGTH_PROPERTY = "MIN_SEGMENT_LENGTH",
            VFR_MINIMUM_SEGMENT_LENGTH_PROPERTY = "VFR_MIN_SEGMENT_LENGTH",
            SEGMENT_LENGTH_SPECIFICATION = "SEGMENT_LENGTH_SPECIFICATION",
            MERGE_TRACKS_PROPERTY = "MERGE_TRACKS",
            MIN_GAP_BETWEEN_TRACKS = "MIN_GAP_BETWEEN_TRACKS",
            MIN_TRACK_LENGTH = "MIN_TRACK_LENGTH",
            MIN_TRACK_OVERLAP = "MIN_OVERLAP",
            SEARCH_REGION_ENABLE_DETECTION_PROPERTY = "SEARCH_REGION_ENABLE_DETECTION",
            SEARCH_REGION_TOP_LEFT_X_DETECTION_PROPERTY = "SEARCH_REGION_TOP_LEFT_X_DETECTION",
            SEARCH_REGION_TOP_LEFT_Y_DETECTION_PROPERTY = "SEARCH_REGION_TOP_LEFT_Y_DETECTION",
            SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION_PROPERTY = "SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION",
            SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION_PROPERTY = "SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION",
            ROTATION_PROPERTY = "ROTATION",
            HORIZONTAL_FLIP_PROPERTY = "HORIZONTAL_FLIP",
            AUTO_ROTATE_PROPERTY = "AUTO_ROTATE",
            AUTO_FLIP_PROPERTY = "AUTO_FLIP",
            REPORT_ERROR = "REPORT_ERROR",
            OUTPUT_ARTIFACTS_AND_EXEMPLARS_ONLY_PROPERTY = "OUTPUT_ARTIFACTS_AND_EXEMPLARS_ONLY",
            OUTPUT_LAST_TASK_ONLY_PROPERTY = "OUTPUT_LAST_TASK_ONLY",
            OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY = "OUTPUT_MERGE_WITH_PREVIOUS_TASK",
            ARTIFACT_EXTRACTION_POLICY_PROPERTY = "ARTIFACT_EXTRACTION_POLICY",
            ARTIFACT_EXTRACTION_POLICY_CROPPING = "ARTIFACT_EXTRACTION_POLICY_CROPPING",
            ARTIFACT_EXTRACTION_POLICY_EXEMPLAR_FRAME_PLUS_PROPERTY = "ARTIFACT_EXTRACTION_POLICY_EXEMPLAR_FRAME_PLUS",
            ARTIFACT_EXTRACTION_POLICY_FIRST_FRAME_PROPERTY = "ARTIFACT_EXTRACTION_POLICY_FIRST_FRAME",
            ARTIFACT_EXTRACTION_POLICY_MIDDLE_FRAME_PROPERTY = "ARTIFACT_EXTRACTION_POLICY_MIDDLE_FRAME",
            ARTIFACT_EXTRACTION_POLICY_LAST_FRAME_PROPERTY = "ARTIFACT_EXTRACTION_POLICY_LAST_FRAME",
            ARTIFACT_EXTRACTION_POLICY_TOP_QUALITY_COUNT_PROPERTY = "ARTIFACT_EXTRACTION_POLICY_TOP_QUALITY_COUNT",
            REQUEST_CANCELLED = "REQUEST_CANCELLED",
            S3_ACCESS_KEY = "S3_ACCESS_KEY",
            S3_SECRET_KEY = "S3_SECRET_KEY",
            S3_SESSION_TOKEN = "S3_SESSION_TOKEN",
            S3_RESULTS_BUCKET = "S3_RESULTS_BUCKET",
            S3_UPLOAD_ONLY = "S3_UPLOAD_ONLY",
            S3_REGION = "S3_REGION",
            S3_USE_VIRTUAL_HOST = "S3_USE_VIRTUAL_HOST",
            S3_HOST = "S3_HOST",
            S3_UPLOAD_OBJECT_KEY_PREFIX = "S3_UPLOAD_OBJECT_KEY_PREFIX",
            TIES_DB_S3_COPY_ENABLED = "TIES_DB_S3_COPY_ENABLED",
            TIES_DB_COPY_SRC_ = "TIES_DB_COPY_SRC_",
            TIES_DB_COPY_SRC_S3_ACCESS_KEY = "TIES_DB_COPY_SRC_S3_ACCESS_KEY",
            TIES_DB_COPY_SRC_S3_ACCESS_KEY2 = TIES_DB_COPY_SRC_ + S3_ACCESS_KEY,
            TIES_DB_COPY_SRC_S3_SECRET_KEY = TIES_DB_COPY_SRC_ + S3_SECRET_KEY,
            TIES_DB_COPY_SRC_S3_SESSION_TOKEN = TIES_DB_COPY_SRC_ + S3_SESSION_TOKEN,
            TIES_DB_COPY_SRC_S3_REGION = TIES_DB_COPY_SRC_ + S3_REGION,
            TIES_DB_COPY_SRC_S3_USE_VIRTUAL_HOST = TIES_DB_COPY_SRC_ + S3_USE_VIRTUAL_HOST,
            TIES_DB_COPY_SRC_S3_HOST = TIES_DB_COPY_SRC_ + S3_HOST,
            TIES_DB_COPY_SRC_S3_UPLOAD_OBJECT_KEY_PREFIX = TIES_DB_COPY_SRC_ + S3_UPLOAD_OBJECT_KEY_PREFIX,
            DETECTION_PADDING_X = "DETECTION_PADDING_X",
            DETECTION_PADDING_Y = "DETECTION_PADDING_Y",
            MOVING_TRACK_LABELS_ENABLED = "MOVING_TRACK_LABELS_ENABLED",
            MOVING_TRACKS_ONLY = "MOVING_TRACKS_ONLY",
            MOVING_TRACK_MAX_IOU = "MOVING_TRACK_MAX_IOU",
            MOVING_TRACK_MIN_DETECTIONS = "MOVING_TRACK_MIN_DETECTIONS",
            MARKUP_LABELS_FROM_DETECTIONS = "MARKUP_LABELS_FROM_DETECTIONS",
            MARKUP_LABELS_TRACK_INDEX_ENABLED = "MARKUP_LABELS_TRACK_INDEX_ENABLED",
            MARKUP_LABELS_TEXT_PROP_TO_SHOW = "MARKUP_LABELS_TEXT_PROP_TO_SHOW",
            MARKUP_LABELS_NUMERIC_PROP_TO_SHOW = "MARKUP_LABELS_NUMERIC_PROP_TO_SHOW",
            MARKUP_VIDEO_ENCODER = "MARKUP_VIDEO_ENCODER",
            DERIVATIVE_MEDIA_TEMP_PATH = "DERIVATIVE_MEDIA_TEMP_PATH",
            DERIVATIVE_MEDIA_ID = "DERIVATIVE_MEDIA_ID",
            IS_DERIVATIVE_MEDIA = "IS_DERIVATIVE_MEDIA",
            TIES_DB_URL = "TIES_DB_URL",
            SKIP_TIES_DB_CHECK = "SKIP_TIES_DB_CHECK",
            MARKUP_ANIMATION_ENABLED = "MARKUP_ANIMATION_ENABLED",
            MARKUP_TEXT_LABEL_MAX_LENGTH = "MARKUP_TEXT_LABEL_MAX_LENGTH",
            SKIP_MEDIA_INSPECTION = "SKIP_MEDIA_INSPECTION",
            LINKED_MEDIA_HASH = "LINKED_MEDIA_HASH",
            FFPROBE_IGNORE_STDERR = "FFPROBE_IGNORE_STDERR",
            FFPROBE_STDERR_NUM_LINES = "FFPROBE_STDERR_NUM_LINES",
            TRIGGER = "TRIGGER",
            CALLBACK_USE_OIDC = "CALLBACK_USE_OIDC",
            TIES_DB_USE_OIDC = "TIES_DB_USE_OIDC",
            ROLL_UP_FILE = "ROLL_UP_FILE";

    private MpfConstants() {
    }
}
