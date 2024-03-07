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

#include <activemq/library/ActiveMQCPP.h>

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
#include <activemq/core/ActiveMQConnectionFactory.h>
#pragma GCC diagnostic pop

#include "detection.pb.h"

#include "BasicAmqMessageSender.h"
#include "ExecutorErrors.h"


using org::mitre::mpf::wfm::buffers::StreamingDetectionResponse;

namespace MPF { namespace COMPONENT {

    BasicAmqMessageSender::BasicAmqMessageSender(const JobSettings &job_settings)
            : job_id_(job_settings.job_id)
            , segment_size_(job_settings.segment_size)
            , connection_(Connect(job_settings.message_broker_uri))
            , session_(connection_->createSession())
            , job_status_producer_(CreateProducer(job_settings.job_status_queue, *session_))
            , activity_alert_producer_(CreateProducer(job_settings.activity_alert_queue, *session_))
            , summary_report_producer_(CreateProducer(job_settings.summary_report_queue, *session_)) {

        connection_->start();
    }

    std::unique_ptr<cms::Connection> BasicAmqMessageSender::Connect(const std::string &broker_uri) {
        activemq::library::ActiveMQCPP::initializeLibrary();
        activemq::core::ActiveMQConnectionFactory connection_factory(broker_uri);
        connection_factory.setUseAsyncSend(true);

        return std::unique_ptr<cms::Connection>(connection_factory.createConnection());
    }


    std::unique_ptr<cms::MessageProducer> BasicAmqMessageSender::CreateProducer(const std::string &queue_name,
                                                                                cms::Session &session) {
        std::unique_ptr<cms::Queue> queue(session.createQueue(queue_name));
        return std::unique_ptr<cms::MessageProducer>(session.createProducer(queue.get()));
    }


    void BasicAmqMessageSender::SendJobStatus(const std::string &job_status, long timestamp) {
        std::unique_ptr<cms::Message> message(session_->createMessage());
        message->setLongProperty("JOB_ID", job_id_);
        message->setStringProperty("JOB_STATUS", job_status);
        message->setLongProperty("STATUS_CHANGE_TIMESTAMP", timestamp);
        job_status_producer_->send(message.get());
    }


    void BasicAmqMessageSender::SendStallAlert(long timestamp) {
        SendJobStatus("STALLED", timestamp);
    }

    void BasicAmqMessageSender::SendInProgressNotification(long timestamp) {
        SendJobStatus("IN_PROGRESS", timestamp);
    }


    void BasicAmqMessageSender::SendActivityAlert(int frame_number, long timestamp) {
        std::unique_ptr<cms::Message> message(session_->createMessage());
        message->setLongProperty("JOB_ID", job_id_);
        message->setIntProperty("FRAME_NUMBER", frame_number);
        message->setLongProperty("ACTIVITY_DETECTION_TIMESTAMP", timestamp);
        activity_alert_producer_->send(message.get());
    }


    void BasicAmqMessageSender::SendSummaryReport(int frame_number,
                                                  const std::vector<MPFVideoTrack> &tracks,
                                                  const std::unordered_map<int, long> &frame_timestamps,
                                                  const std::string &error_message) {
        StreamingDetectionResponse protobuf_response;

        int segment_number = frame_number / segment_size_;
        protobuf_response.set_segment_number(segment_number);
        protobuf_response.set_segment_start_frame(segment_size_ * segment_number);
        protobuf_response.set_segment_stop_frame(frame_number);
        if (!error_message.empty())  {
            protobuf_response.set_error(error_message);
        }

        for (const auto &track : tracks) {
            auto protobuf_track = protobuf_response.add_video_tracks();
            protobuf_track->set_start_frame(track.start_frame);
            protobuf_track->set_start_time(frame_timestamps.at(track.start_frame));

            protobuf_track->set_stop_frame(track.stop_frame);
            protobuf_track->set_stop_time(frame_timestamps.at(track.stop_frame));

            protobuf_track->set_confidence(track.confidence);

            protobuf_track->mutable_detection_properties()->insert(
                    track.detection_properties.begin(),
                    track.detection_properties.end());

            for (const auto& [detection_frame_number, frame_location] : track.frame_locations) {
                auto protobuf_detection = protobuf_track->add_detections();
                protobuf_detection->set_frame_number(detection_frame_number);
                protobuf_detection->set_time(frame_timestamps.at(detection_frame_number));

                protobuf_detection->set_x_left_upper(frame_location.x_left_upper);
                protobuf_detection->set_y_left_upper(frame_location.y_left_upper);
                protobuf_detection->set_width(frame_location.width);
                protobuf_detection->set_height(frame_location.height);
                protobuf_detection->set_confidence(frame_location.confidence);
                protobuf_detection->mutable_detection_properties()->insert(
                        frame_location.detection_properties.begin(),
                        frame_location.detection_properties.end());
            }
        }

        int proto_bytes_size = protobuf_response.ByteSize();
        std::unique_ptr<unsigned char[]> proto_bytes(new unsigned char[proto_bytes_size]);
        protobuf_response.SerializeWithCachedSizesToArray(proto_bytes.get());

        std::unique_ptr<cms::BytesMessage> message(session_->createBytesMessage(proto_bytes.get(), proto_bytes_size));
        message->setLongProperty("JOB_ID", job_id_);

        summary_report_producer_->send(message.get());
    }
}}
