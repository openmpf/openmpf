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

#include <chrono>
#include <string>
#include <memory>
#include <variant>

#include <cms/Destination.h>

#include <MPFDetectionComponent.h>

#include "LoggerWrapper.h"


namespace MPF::COMPONENT {

using job_variant_t = std::variant<MPFVideoJob, MPFImageJob, MPFAudioJob, MPFGenericJob>;


struct ProtobufMetadata {
    const long media_id;

    const int task_index;

    const int action_index;
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

    using clock_t = std::chrono::steady_clock;
    clock_t::time_point job_start_time;

    const JobLogContext job_log_context;

    const AmqMetadata amq_metadata;

    const ProtobufMetadata protobuf_metadata;

    void OnJobStarted() {
        job_start_time = clock_t::now();
    }

    long GetMillisSinceStart() const {
        if (job_start_time == clock_t::time_point{}) {
            // The job was never started.
            return -1;
        }
        auto duration = clock_t::now() - job_start_time;
        auto duration_ms = std::chrono::round<std::chrono::milliseconds>(duration);
        return duration_ms.count();
    }

    std::string get_mime_type() const {
        return std::visit([](const auto& job) -> std::string {
            const auto& properties = job.media_properties;
            auto it = properties.find("MIME_TYPE");
            if (it != properties.end()) {
                return it->second;
            }

            return "UNKNOWN_MIME";
        }, job);
    }
};
}
