/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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
using org::mitre::mpf::wfm::buffers::DetectionRequest_DataType;
using org::mitre::mpf::wfm::buffers::DetectionResponse_DataType;
using namespace MPF;
using namespace COMPONENT;


MPFDetectionDataType
translateProtobufDataType( const DetectionRequest_DataType &dataType) {

    switch (dataType) {
        case DetectionRequest_DataType::DetectionRequest_DataType_UNKNOWN:
            return MPFDetectionDataType::UNKNOWN;
        case DetectionRequest_DataType::DetectionRequest_DataType_VIDEO:
            return MPFDetectionDataType::VIDEO;
        case DetectionRequest_DataType::DetectionRequest_DataType_IMAGE:
            return MPFDetectionDataType::IMAGE;
        case DetectionRequest_DataType::DetectionRequest_DataType_AUDIO:
            return MPFDetectionDataType::AUDIO;
        default:
            return MPFDetectionDataType::UNKNOWN;
    }
}

DetectionResponse_DataType
translateMPFDetectionDataType(const MPFDetectionDataType &dataType) {

    switch (dataType) {
        case UNKNOWN:
            return DetectionResponse_DataType::DetectionResponse_DataType_UNKNOWN;
        case VIDEO:
            return DetectionResponse_DataType::DetectionResponse_DataType_VIDEO;
        case IMAGE:
            return DetectionResponse_DataType::DetectionResponse_DataType_IMAGE;
        case AUDIO:
            return DetectionResponse_DataType::DetectionResponse_DataType_AUDIO;
        default:
            return DetectionResponse_DataType::DetectionResponse_DataType_UNKNOWN;
    }
}

DetectionError translateMPFDetectionError(
    const MPF::COMPONENT::MPFDetectionError err) {

    switch (err) {
        case MPF_DETECTION_SUCCESS:
            return DetectionError::NO_DETECTION_ERROR;
        case MPF_DETECTION_NOT_INITIALIZED:
            return DetectionError::DETECTION_NOT_INITIALIZED;
        case MPF_UNRECOGNIZED_DATA_TYPE:
            return DetectionError::UNRECOGNIZED_DATA_TYPE;
        case MPF_UNSUPPORTED_DATA_TYPE:
            return DetectionError::UNSUPPORTED_DATA_TYPE;
        case MPF_INVALID_DATAFILE_URI:
            return DetectionError::INVALID_DATAFILE_URI;
        case MPF_COULD_NOT_OPEN_DATAFILE:
            return DetectionError::COULD_NOT_OPEN_DATAFILE;
        case MPF_COULD_NOT_READ_DATAFILE:
            return DetectionError::COULD_NOT_READ_DATAFILE;
        case MPF_FILE_WRITE_ERROR:
            return DetectionError::FILE_WRITE_ERROR;
        case MPF_IMAGE_READ_ERROR:
            return DetectionError::IMAGE_READ_ERROR;
        case MPF_BAD_FRAME_SIZE:
            return DetectionError::BAD_FRAME_SIZE;
        case MPF_BOUNDING_BOX_SIZE_ERROR:
            return DetectionError::BOUNDING_BOX_SIZE_ERROR;
        case MPF_INVALID_FRAME_INTERVAL:
            return DetectionError::INVALID_FRAME_INTERVAL;
        case MPF_INVALID_START_FRAME:
            return DetectionError::INVALID_START_FRAME;
        case MPF_INVALID_STOP_FRAME:
            return DetectionError::INVALID_STOP_FRAME;
        case MPF_DETECTION_FAILED:
            return DetectionError::DETECTION_FAILED;
        case MPF_DETECTION_TRACKING_FAILED:
            return DetectionError::DETECTION_TRACKING_FAILED;
        case MPF_MISSING_PROPERTY:
            return DetectionError::MISSING_PROPERTY;
        case MPF_INVALID_PROPERTY:
            return DetectionError::INVALID_PROPERTY;
        case MPF_PROPERTY_IS_NOT_INT:
            return DetectionError::PROPERTY_IS_NOT_INT;
        case MPF_PROPERTY_IS_NOT_FLOAT:
            return DetectionError::PROPERTY_IS_NOT_FLOAT;
        case MPF_INVALID_ROTATION:
            return DetectionError::INVALID_ROTATION;
        case MPF_MEMORY_ALLOCATION_FAILED:
            return DetectionError::MEMORY_ALLOCATION_FAILED;
        case MPF_OTHER_DETECTION_ERROR_TYPE:
            return DetectionError::UNRECOGNIZED_DETECTION_ERROR;
        case MPF_GPU_ERROR:
            return DetectionError::GPU_ERROR;
        case MPF_NETWORK_ERROR:
            return DetectionError::NETWORK_ERROR;
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
        case DetectionError::UNRECOGNIZED_DATA_TYPE:
            return MPF_UNRECOGNIZED_DATA_TYPE;
        case DetectionError::UNSUPPORTED_DATA_TYPE:
            return MPF_UNSUPPORTED_DATA_TYPE;
        case DetectionError::INVALID_DATAFILE_URI:
            return MPF_INVALID_DATAFILE_URI;
        case DetectionError::COULD_NOT_OPEN_DATAFILE:
            return MPF_COULD_NOT_OPEN_DATAFILE;
        case DetectionError::COULD_NOT_READ_DATAFILE:
            return MPF_COULD_NOT_READ_DATAFILE;
        case DetectionError::FILE_WRITE_ERROR:
            return MPF_FILE_WRITE_ERROR;
        case DetectionError::IMAGE_READ_ERROR:
            return MPF_IMAGE_READ_ERROR;
        case DetectionError::BAD_FRAME_SIZE:
            return MPF_BAD_FRAME_SIZE;
        case DetectionError::BOUNDING_BOX_SIZE_ERROR:
            return MPF_BOUNDING_BOX_SIZE_ERROR;
        case DetectionError::INVALID_FRAME_INTERVAL:
            return MPF_INVALID_FRAME_INTERVAL;
        case DetectionError::INVALID_START_FRAME:
            return MPF_INVALID_START_FRAME;
        case DetectionError::INVALID_STOP_FRAME:
            return MPF_INVALID_STOP_FRAME;
        case DetectionError::DETECTION_FAILED:
            return MPF_DETECTION_FAILED;
        case DetectionError::DETECTION_TRACKING_FAILED:
            return MPF_DETECTION_TRACKING_FAILED;
        case DetectionError::MISSING_PROPERTY:
            return MPF_MISSING_PROPERTY;
        case DetectionError::INVALID_PROPERTY:
            return MPF_INVALID_PROPERTY;
        case DetectionError::PROPERTY_IS_NOT_INT:
            return MPF_PROPERTY_IS_NOT_INT;
        case DetectionError::PROPERTY_IS_NOT_FLOAT:
            return MPF_PROPERTY_IS_NOT_FLOAT;
        case DetectionError::INVALID_ROTATION:
            return MPF_INVALID_ROTATION;
        case DetectionError::MEMORY_ALLOCATION_FAILED:
            return MPF_MEMORY_ALLOCATION_FAILED;
        case DetectionError::UNRECOGNIZED_DETECTION_ERROR:
            return MPF_OTHER_DETECTION_ERROR_TYPE;
        case DetectionError::GPU_ERROR:
            return MPF_GPU_ERROR;
        case DetectionError::NETWORK_ERROR:
            return MPF_NETWORK_ERROR;
        case DetectionError::COULD_NOT_READ_MEDIA:
            return MPF_COULD_NOT_READ_MEDIA;
        default:
            return MPF_OTHER_DETECTION_ERROR_TYPE;
    }
}
