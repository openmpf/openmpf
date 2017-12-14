/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

#ifndef MPF_DETECTION_EXECUTOR_CPP_COMMON_MESSAGEUTILS_H_
#define MPF_DETECTION_EXECUTOR_CPP_COMMON_MESSAGEUTILS_H_
#include <MPFDetectionComponent.h>
#include "detection.pb.h"

// Translate a protobu data type to the corresponding
// MPFDetectionDataType. This is used for batch processing detection
// requests.
MPF::COMPONENT::MPFDetectionDataType
translateProtobufDataType(const org::mitre::mpf::wfm::buffers::DetectionRequest_DataType &dataType);

// Translate an MPFDetectionDataType to the corresponding protobuf
// data type. This used for batch processing detection responses.
org::mitre::mpf::wfm::buffers::DetectionResponse_DataType
translateMPFDetectionDataType(const MPF::COMPONENT::MPFDetectionDataType &dataType);

// Translate an MPFDetectionError to the corresponding detection
// protobuf error. This is used in both batch and streaming processing jobs.
org::mitre::mpf::wfm::buffers::DetectionError
translateMPFDetectionError(const MPF::COMPONENT::MPFDetectionError err);

// Translate a detection protobuf error to the corresponding
// MPFDetectionError. This is currently only used in the streaming
// message unit tests.
MPF::COMPONENT::MPFDetectionError
translateProtobufError(org::mitre::mpf::wfm::buffers::DetectionError err);

#endif  // MPF_DETECTION_EXECUTOR_CPP_COMMON_MESSAGEUTILS_H_
