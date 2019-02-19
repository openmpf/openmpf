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
#include "MPFMessage.h"
#include "ExecutorErrors.h"


using org::mitre::mpf::wfm::buffers::StreamingDetectionResponse;

namespace MPF {


template<>
void BasicAmqMessageSender<MPFSegmentReadyMessage>::SendMsg(const MPFSegmentReadyMessage &msg) {
     std::unique_ptr<cms::Message> message(session_->createMessage());
     message->setLongProperty("JOB_ID", msg.job_id);
     message->setIntProperty("SEGMENT_NUMBER", msg.segment_number);
     message->setIntProperty("FRAME_WIDTH", msg.frame_width);
     message->setIntProperty("FRAME_HEIGHT", msg.frame_height);
     message->setIntProperty("FRAME_DATA_TYPE", msg.cvType);
     message->setIntProperty("FRAME_BYTES_PER_PIXEL", msg.bytes_per_pixel);
     producer_->send(message.get());
 }

template<>
 void BasicAmqMessageSender<MPFFrameReadyMessage>::SendMsg(const MPFFrameReadyMessage &msg) {
     std::unique_ptr<cms::Message> message(session_->createMessage());
     message->setLongProperty("JOB_ID", msg.job_id);
     message->setIntProperty("SEGMENT_NUMBER", msg.segment_number);
     message->setIntProperty("FRAME_INDEX", msg.frame_index);
     message->setLongProperty("FRAME_READ_TIMESTAMP", msg.frame_timestamp);
     message->setBooleanProperty("PROCESS_FRAME_FLAG", msg.process_this_frame);
     producer_->send(message.get());
}


template<>
void BasicAmqMessageSender<MPFJobStatusMessage>::SendMsg(const MPFJobStatusMessage &msg) {
    std::unique_ptr<cms::Message> message(session_->createMessage());
    message->setLongProperty("JOB_ID", msg.job_id);
    message->setStringProperty("JOB_STATUS", msg.status_message);
    message->setLongProperty("STATUS_CHANGE_TIMESTAMP", msg.status_change_time);
    producer_->send(message.get());
}


template<>
void BasicAmqMessageSender<MPFActivityAlertMessage>::SendMsg(const MPFActivityAlertMessage &msg) {
    std::unique_ptr<cms::Message> message(session_->createMessage());
    message->setLongProperty("JOB_ID", msg.job_id);
    message->setIntProperty("FRAME_INDEX", msg.frame_index);
    message->setLongProperty("ACTIVITY_DETECTION_TIMESTAMP", msg.activity_time);
    producer_->send(message.get());
}

template<>
void BasicAmqMessageSender<MPFSegmentSummaryMessage>::SendMsg(const MPFSegmentSummaryMessage &msg) {
    StreamingDetectionResponse protobuf_response;

    protobuf_response.set_segment_number(msg.segment_number);
    protobuf_response.set_segment_start_frame(msg.segment_start_frame);
    protobuf_response.set_segment_stop_frame(msg.segment_stop_frame);
    protobuf_response.set_detection_type(msg.detection_type);
    if (!msg.segment_error.empty())  {
        protobuf_response.set_error(msg.segment_error);
    }

    for (const auto &track : msg.tracks) {
        auto protobuf_track = protobuf_response.add_video_tracks();
        protobuf_track->set_start_frame(track.start_frame);
        protobuf_track->set_start_time(msg.timestamps.at(track.start_frame));

        protobuf_track->set_stop_frame(track.stop_frame);
        protobuf_track->set_stop_time(msg.timestamps.at(track.stop_frame));

        protobuf_track->set_confidence(track.confidence);

        for (const auto &property : track.detection_properties) {
            auto protobuf_property = protobuf_track->add_detection_properties();
            protobuf_property->set_key(property.first);
            protobuf_property->set_value(property.second);
        }

        for (const auto &frame_location_pair : track.frame_locations) {
            auto protobuf_detection = protobuf_track->add_detections();
            protobuf_detection->set_frame_number(frame_location_pair.first);
            protobuf_detection->set_time(msg.timestamps.at(frame_location_pair.first));

            const auto &frame_location = frame_location_pair.second;
            protobuf_detection->set_x_left_upper(frame_location.x_left_upper);
            protobuf_detection->set_y_left_upper(frame_location.y_left_upper);
            protobuf_detection->set_width(frame_location.width);
            protobuf_detection->set_height(frame_location.height);
            protobuf_detection->set_confidence(frame_location.confidence);
            for (const auto &property : frame_location.detection_properties) {
                auto protobuf_property = protobuf_detection->add_detection_properties();
                protobuf_property->set_key(property.first);
                protobuf_property->set_value(property.second);
            }
        }
    }

    int proto_bytes_size = protobuf_response.ByteSize();
    std::unique_ptr<uchar[]> proto_bytes(new uchar[proto_bytes_size]);
    protobuf_response.SerializeWithCachedSizesToArray(proto_bytes.get());

    std::unique_ptr<cms::BytesMessage> message(session_->createBytesMessage(proto_bytes.get(), proto_bytes_size));
    message->setLongProperty("JOB_ID", msg.job_id);

    producer_->send(message.get());
}

} // end of namespace MPF
