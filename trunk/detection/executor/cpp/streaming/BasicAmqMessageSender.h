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


#ifndef MPF_BASICAMQMESSAGESENDER_H
#define MPF_BASICAMQMESSAGESENDER_H

#include <memory>
#include <string>

#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/MessageProducer.h>

#include <MPFDetectionComponent.h>

#include "JobSettings.h"

namespace MPF { namespace COMPONENT {

    class BasicAmqMessageSender {

    public:
        explicit BasicAmqMessageSender(const JobSettings &job_settings);

        void SendJobStatus(const std::string &job_status);

        void SendActivityAlert(int frame_index);


        void SendSummaryReport(
                int frame_index, const std::string &detection_type, MPF::COMPONENT::MPFDetectionError segment_error,
                const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks);

    private:
        const long job_id_;
        const int segment_size_;

        std::unique_ptr<cms::Connection> connection_;
        std::unique_ptr<cms::Session> session_;

        std::unique_ptr<cms::MessageProducer> job_status_producer_;
        std::unique_ptr<cms::MessageProducer> activity_alert_producer_;
        std::unique_ptr<cms::MessageProducer> summary_report_producer_;


        static std::unique_ptr<cms::Connection> Connect(const std::string &broker_uri);

        static std::unique_ptr<cms::MessageProducer>
        CreateProducer(const std::string &queue_name, cms::Session &session);

        static long GetTimestampMillis();

    };
}}

#endif //MPF_MYMESSAGESENDER_H
