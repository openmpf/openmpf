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

#include <chrono>

#include <activemq/library/ActiveMQCPP.h>
#include <activemq/core/ActiveMQConnectionFactory.h>

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


    void BasicAmqMessageSender::SendJobStatus(const std::string &job_status) {
        std::unique_ptr<cms::Message> message(session_->createMessage());
        message->setLongProperty("JOB_ID", job_id_);
        message->setStringProperty("JOB_STATUS", job_status);
        message->setLongProperty("STATUS_CHANGE_TIMESTAMP", GetTimestampMillis());
        job_status_producer_->send(message.get());
    }


    void BasicAmqMessageSender::SendActivityAlert(int frame_number) {
        std::unique_ptr<cms::Message> message(session_->createMessage());
        message->setLongProperty("JOB_ID", job_id_);
        message->setIntProperty("FRAME_NUMBER", frame_number);
        message->setLongProperty("ACTIVITY_DETECTION_TIMESTAMP", GetTimestampMillis());
        activity_alert_producer_->send(message.get());
    }


    void BasicAmqMessageSender::SendSummaryReport(int frame_number,
                                                  const std::string &detection_type,
                                                  const std::vector<MPFVideoTrack> &tracks,
                                                  const std::string &error_message) {
        StreamingDetectionResponse protobuf_response;

        int segment_number = frame_number / segment_size_;
        protobuf_response.set_segment_number(segment_number);
        protobuf_response.set_start_frame(segment_size_ * segment_number);
        protobuf_response.set_stop_frame(frame_number);
        protobuf_response.set_detection_type(detection_type);
        if (!error_message.empty())  {
            protobuf_response.set_error(error_message);
        }

        for (const auto &track : tracks) {
            auto protobuf_track = protobuf_response.add_video_tracks();
            protobuf_track->set_start_frame(track.start_frame);
            protobuf_track->set_stop_frame(track.stop_frame);
            protobuf_track->set_confidence(track.confidence);

            for (const auto &property : track.detection_properties) {
                auto protobuf_property = protobuf_track->add_detection_properties();
                protobuf_property->set_key(property.first);
                protobuf_property->set_value(property.second);
            }

            for (const auto &frame_location_pair : track.frame_locations) {
                auto protobuf_frame_loc_map = protobuf_track->add_frame_locations();
                protobuf_frame_loc_map->set_frame(frame_location_pair.first);

                auto protobuf_img_loc = protobuf_frame_loc_map->mutable_image_location();
                const auto &frame_location = frame_location_pair.second;
                protobuf_img_loc->set_x_left_upper(frame_location.x_left_upper);
                protobuf_img_loc->set_y_left_upper(frame_location.y_left_upper);
                protobuf_img_loc->set_width(frame_location.width);
                protobuf_img_loc->set_confidence(frame_location.confidence);

                for (const auto &property : frame_location.detection_properties) {
                    auto protobuf_property = protobuf_img_loc->add_detection_properties();
                    protobuf_property->set_key(property.first);
                    protobuf_property->set_value(property.second);
                }
            }
        }

        int proto_bytes_size = protobuf_response.ByteSize();
        std::unique_ptr<uchar[]> proto_bytes(new uchar[proto_bytes_size]);
        protobuf_response.SerializeWithCachedSizesToArray(proto_bytes.get());

        std::unique_ptr<cms::BytesMessage> message(session_->createBytesMessage(proto_bytes.get(), proto_bytes_size));
        message->setLongProperty("JOB_ID", job_id_);

        summary_report_producer_->send(message.get());
    }


    long BasicAmqMessageSender::GetTimestampMillis() {
        using namespace std::chrono;
        auto time_since_epoch = system_clock::now().time_since_epoch();
        return duration_cast<milliseconds>(time_since_epoch).count();
    }
}}