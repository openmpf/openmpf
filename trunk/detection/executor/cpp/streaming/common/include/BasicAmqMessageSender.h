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


#ifndef MPF_BASICAMQMESSAGESENDER_H
#define MPF_BASICAMQMESSAGESENDER_H

#include <memory>
#include <string>
#include <unordered_map>

#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/MessageProducer.h>

#include <MPFDetectionComponent.h>

#include "MPFMessagingConnection.h"
#include "JobSettings.h"


namespace MPF {

    class BasicAmqMessageSender {

    public:
        explicit BasicAmqMessageSender(const MPF::COMPONENT::JobSettings &job_settings,
                                       MPFMessagingConnection &connection);

        void SendSegmentReady(const int segment_number,
                              const int frame_width,
                              const int frame_height,
                              const int cvType,
                              const int bytes_per_pixel);

        void SendFrameReady(const int segment_number,
                            const int frame_index,
                            const long frame_timestamp);

        void SendJobStatus(const std::string &job_status, long timestamp);

        void SendStallAlert(long timestamp);

        void SendInProgressNotification(long timestamp);

        void SendActivityAlert(int frame_number, long timestamp);

        void SendSummaryReport(const int frame_index,
                               const int segment_number,
                               const std::string &detection_type,
                               const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks,
                               const std::unordered_map<int, long> &frame_timestamps,
                               const std::string &error_message = {});

        void SendReleaseFrame(const int frame_index);

    private:
        const long job_id_;
        const int segment_size_;

        std::shared_ptr<cms::Connection> connection_;
        std::shared_ptr<cms::Session> session_;

        std::unique_ptr<cms::MessageProducer> segment_ready_producer_;
        std::unique_ptr<cms::MessageProducer> frame_ready_producer_;
        std::unique_ptr<cms::MessageProducer> job_status_producer_;
        std::unique_ptr<cms::MessageProducer> activity_alert_producer_;
        std::unique_ptr<cms::MessageProducer> summary_report_producer_;
        std::unique_ptr<cms::MessageProducer> release_frame_producer_;

        static std::unique_ptr<cms::MessageProducer>
        CreateProducer(const std::string &queue_name, cms::Session &session);
    };
}

#endif //MPF_BASICAMQMESSAGESENDER_H
