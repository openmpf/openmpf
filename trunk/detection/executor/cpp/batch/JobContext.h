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

#pragma once

#include <string>
#include <memory>
#include <variant>

#include <cms/Destination.h>

#include <MPFDetectionComponent.h>

#include "LoggerWrapper.h"


namespace MPF::COMPONENT {

using job_variant_t = std::variant<MPFVideoJob, MPFImageJob, MPFAudioJob, MPFGenericJob>;


struct ProtobufMetadata {
    const long request_id;

    const long media_id;

    const int task_index;

    const std::string task_name;

    const int action_index;

    const std::string action_name;
};


struct AmqMetadata {
    const std::unique_ptr<cms::Destination> response_queue;

    const int cms_priority;

    const std::string correlation_id;

    const std::string bread_crumb_id;

    const int split_size;
};


struct JobContext {
    const long job_id;

    const std::string job_name;

    const job_variant_t job;

    const MPFDetectionDataType job_type;

    const std::string job_type_name;

    const JobLogContext job_log_context;

    const AmqMetadata amq_metadata;

    const ProtobufMetadata protobuf_metadata;
};
}