/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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


#ifndef MPF_JOBSETTINGS_H
#define MPF_JOBSETTINGS_H

#include <map>
#include <string>
#include <chrono>

namespace MPF { namespace COMPONENT {

    enum class RetryStrategy {
        NEVER_RETRY,
        NO_ALERT_NO_TIMEOUT,
        NO_ALERT_WITH_TIMEOUT,
        ALERT_NO_TIMEOUT,
        ALERT_WITH_TIMEOUT
    };


    struct JobSettings {
        const long job_id;
        const std::string stream_uri;
        const int num_pipeline_stages;    // must be 1 or 2
        const int pipeline_stage_number;  // must be 1 or 2
        const int segment_size;
        const RetryStrategy retry_strategy;
        const std::chrono::milliseconds stall_timeout;
        const std::chrono::milliseconds stall_alert_threshold;
        const std::chrono::milliseconds message_receive_retry_interval;
        const std::string component_lib_path;
        const std::string message_broker_uri;
        const std::string frame_store_server_hostname;
        const int frame_store_server_portnum;
        const int frame_store_capacity;
        const std::string job_status_queue;
        const std::string activity_alert_queue;
        const std::string summary_report_queue;
        const std::string segment_ready_queue;
        const std::string frame_ready_queue_stage1;
        const std::string frame_ready_queue_stage2;
        const std::string release_frame_queue;

        const std::map<std::string, std::string> job_properties;
        const std::map<std::string, std::string> media_properties;

        static JobSettings FromIniFile(const std::string &ini_path);

        static RetryStrategy GetRetryStrategy(std::chrono::milliseconds &stall_timeout,
                                              const std::chrono::milliseconds &alert_threshold);
    };
    
}}

#endif //MPF_JOBSETTINGS_H
