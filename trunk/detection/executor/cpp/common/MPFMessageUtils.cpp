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

#include "MPFMessageUtils.h"

using org::mitre::mpf::wfm::buffers::DetectionError;
using namespace MPF;
using namespace COMPONENT;



DetectionError translateMPFDetectionError(
    const MPF::COMPONENT::MPFDetectionError err) {

    switch (err) {
        case MPF_DETECTION_SUCCESS:
            return DetectionError::NO_DETECTION_ERROR;
        case MPF_DETECTION_NOT_INITIALIZED:
            return DetectionError::DETECTION_NOT_INITIALIZED;
        case MPF_UNSUPPORTED_DATA_TYPE:
            return DetectionError::UNSUPPORTED_DATA_TYPE;
        case MPF_COULD_NOT_OPEN_DATAFILE:
            return DetectionError::COULD_NOT_OPEN_DATAFILE;
        case MPF_COULD_NOT_READ_DATAFILE:
            return DetectionError::COULD_NOT_READ_DATAFILE;
        case MPF_FILE_WRITE_ERROR:
            return DetectionError::FILE_WRITE_ERROR;
        case MPF_BAD_FRAME_SIZE:
            return DetectionError::BAD_FRAME_SIZE;
        case MPF_DETECTION_FAILED:
            return DetectionError::DETECTION_FAILED;
        case MPF_MISSING_PROPERTY:
            return DetectionError::MISSING_PROPERTY;
        case MPF_INVALID_PROPERTY:
            return DetectionError::INVALID_PROPERTY;
        case MPF_MEMORY_ALLOCATION_FAILED:
            return DetectionError::MEMORY_ALLOCATION_FAILED;
        case MPF_OTHER_DETECTION_ERROR_TYPE:
            return DetectionError::UNRECOGNIZED_DETECTION_ERROR;
        case MPF_GPU_ERROR:
            return DetectionError::GPU_ERROR;
        case MPF_NETWORK_ERROR:
            return DetectionError::NETWORK_ERROR;
        case MPF_COULD_NOT_OPEN_MEDIA:
            return DetectionError::COULD_NOT_OPEN_MEDIA;
        case MPF_COULD_NOT_READ_MEDIA:
            return DetectionError::COULD_NOT_READ_MEDIA;
        default:
            return DetectionError::UNRECOGNIZED_DETECTION_ERROR;
    }
}


MPFDetectionError translateProtobufError(DetectionError err) {

    switch (err) {
        case DetectionError::NO_DETECTION_ERROR:
            return MPF_DETECTION_SUCCESS;
        case DetectionError::DETECTION_NOT_INITIALIZED:
            return MPF_DETECTION_NOT_INITIALIZED;
        case DetectionError::UNSUPPORTED_DATA_TYPE:
            return MPF_UNSUPPORTED_DATA_TYPE;
        case DetectionError::COULD_NOT_OPEN_DATAFILE:
            return MPF_COULD_NOT_OPEN_DATAFILE;
        case DetectionError::COULD_NOT_READ_DATAFILE:
            return MPF_COULD_NOT_READ_DATAFILE;
        case DetectionError::FILE_WRITE_ERROR:
            return MPF_FILE_WRITE_ERROR;
        case DetectionError::BAD_FRAME_SIZE:
            return MPF_BAD_FRAME_SIZE;
        case DetectionError::DETECTION_FAILED:
            return MPF_DETECTION_FAILED;
        case DetectionError::MISSING_PROPERTY:
            return MPF_MISSING_PROPERTY;
        case DetectionError::INVALID_PROPERTY:
            return MPF_INVALID_PROPERTY;
        case DetectionError::MEMORY_ALLOCATION_FAILED:
            return MPF_MEMORY_ALLOCATION_FAILED;
        case DetectionError::UNRECOGNIZED_DETECTION_ERROR:
            return MPF_OTHER_DETECTION_ERROR_TYPE;
        case DetectionError::GPU_ERROR:
            return MPF_GPU_ERROR;
        case DetectionError::NETWORK_ERROR:
            return MPF_NETWORK_ERROR;
        case DetectionError::COULD_NOT_OPEN_MEDIA:
            return MPF_COULD_NOT_OPEN_MEDIA;
        case DetectionError::COULD_NOT_READ_MEDIA:
            return MPF_COULD_NOT_READ_MEDIA;
        default:
            return MPF_OTHER_DETECTION_ERROR_TYPE;
    }
}
