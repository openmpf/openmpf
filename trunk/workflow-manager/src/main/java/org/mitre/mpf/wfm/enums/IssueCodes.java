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

import java.util.Objects;

public enum IssueCodes {
    OTHER,
    FAILED_CALLBACK,
    REMOTE_STORAGE_DOWNLOAD,
    REMOTE_STORAGE_UPLOAD,
    ARTIFACT_EXTRACTION,
    MEDIA_INSPECTION,
    FRAME_COUNT,
    MISSING_VIDEO_STREAM,
    MISSING_AUDIO_STREAM,
    MEDIA_INITIALIZATION,
    MARKUP,
    PADDING,
    INVALID_DETECTION,
    LOCAL_STORAGE,
    TIES_DB_BEFORE_JOB_CHECK;

    public static final IssueCodes DEFAULT = OTHER;

    public static String toString(IssueCodes errorCode) {
        return Objects.requireNonNullElse(errorCode, DEFAULT).toString();
    }
}
