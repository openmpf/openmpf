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


#include "JobContext.h"


namespace MPF::COMPONENT {
    namespace {
        MPFDetectionDataType GetJobType(const job_variant_t &job) {
            if (std::holds_alternative<MPFVideoJob>(job)) {
                return MPFDetectionDataType::VIDEO;
            }
            else if (std::holds_alternative<MPFImageJob>(job)) {
                return MPFDetectionDataType::IMAGE;
            }
            else if (std::holds_alternative<MPFAudioJob>(job)) {
                return MPFDetectionDataType::AUDIO;
            }
            else {
                return MPFDetectionDataType::UNKNOWN;
            }
        }
    }

    JobContext::JobContext(
        long job_id,
        std::string job_name,
        std::string detection_type,
        std::unique_ptr<cms::Destination> response_queue,
        int cms_priority,
        std::string correlation_id,
        std::string bread_crumb_id,
        int split_size,
        const org::mitre::mpf::wfm::buffers::DetectionRequest &detection_request,
        job_variant_t job,
        std::shared_ptr<void> job_log_context)
            : job_id{job_id}
            , job_name{std::move(job_name)}
            , detection_type{std::move(detection_type)}
            , response_queue{std::move(response_queue)}
            , cms_priority{cms_priority}
            , correlation_id{std::move(correlation_id)}
            , bread_crumb_id{std::move(bread_crumb_id)}
            , split_size{split_size}
            , request_id{detection_request.request_id()}
            , media_id{detection_request.media_id()}
            , task_index{detection_request.task_index()}
            , task_name{detection_request.task_name()}
            , action_index{detection_request.action_index()}
            , action_name{detection_request.action_name()}
            , job{std::move(job)}
            , job_type{MPF::COMPONENT::GetJobType(this->job)}
            , job_log_context{std::move(job_log_context)}
    {
    }


    const MPFVideoJob& JobContext::GetVideoJob() const {
        return std::get<MPFVideoJob>(job);
    }

    const MPFImageJob& JobContext::GetImageJob() const {
        return std::get<MPFImageJob>(job);

    }

    const MPFAudioJob& JobContext::GetAudioJob() const {
        return std::get<MPFAudioJob>(job);
    };

    const MPFGenericJob& JobContext::GetGenericJob() const {
        return std::get<MPFGenericJob>(job);
    }

}
