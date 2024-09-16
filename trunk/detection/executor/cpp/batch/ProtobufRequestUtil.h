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

#pragma once

#include <string>
#include <string_view>
#include <vector>

#include <MPFDetectionComponent.h>

#include "detection.pb.h"
#include "JobContext.h"


namespace MPF::COMPONENT::ProtobufRequestUtil {
    namespace mpf_buffers = org::mitre::mpf::wfm::buffers;

    mpf_buffers::DetectionRequest ParseRequest(const std::vector<unsigned char>& bytes);

    std::string GetJobName(
                long job_id, const mpf_buffers::DetectionRequest& detection_request);

    job_variant_t CreateComponentJob(
            std::string_view job_name,
            const Properties& environment_job_properties,
            const mpf_buffers::DetectionRequest& detection_request);

    ProtobufMetadata GetMetadata(const mpf_buffers::DetectionRequest& detection_request);
}
