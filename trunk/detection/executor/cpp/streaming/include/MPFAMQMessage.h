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

#ifndef MPF_AMQ_MESSAGE_H_
#define MPF_AMQ_MESSAGE_H_

#include <memory>

#include <cms/Session.h>
#include <cms/Message.h>
#include <cms/TextMessage.h>
#include <cms/BytesMessage.h>

#include "MPFMessage.h"


namespace MPF {

struct AMQMessage {
    virtual ~AMQMessage() = default;

    virtual void Clear() {
        if (NULL != msg_) msg_->clearProperties();
    }

    virtual cms::Message *GetMessagePtr() {
        return msg_.get();
    }

    virtual void InitMessage(cms::Session *session) = 0;

    virtual void ReceiveMessageContent() = 0;

    virtual void Receive(cms::Message *message) {
        if (NULL != message) {
            msg_.reset(message);
            ReceiveMessageContent();
        }
    }

  protected:
    AMQMessage() = default;
    std::unique_ptr<cms::Message> msg_;

};


struct AMQSegmentSummaryMessage : AMQMessage, MPFSegmentSummaryMessage {

    AMQSegmentSummaryMessage(const std::string &job_name,
                             const uint32_t job_number,
                             const uint32_t seg_num,
                             const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks)
            : MPFSegmentSummaryMessage(job_name, job_number, seg_num, tracks) {}

    void InitMessage(cms::Session *session) {
        msg_.reset(session->createBytesMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setIntProperty("SEGMENT_NUMBER", segment_number_);
            //TODO: Need to pack the tracks vector into a protobuf and
            //      set the body of the message.
        }
    }

    void ReceiveMessageContent() {
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        segment_number_ = msg_->getIntProperty("SEGMENT_NUMBER");
        //TODO: Need to unpack the tracks vector from the protobuf
        //      in the message body.
    }
};

struct AMQActivityAlertMessage : AMQMessage, MPFActivityAlertMessage {
    AMQActivityAlertMessage(const std::string &job_name,
                            const uint32_t job_number,
                            const uint32_t seg_num,
                            const uint32_t frame_num,
                            const double time)
            : MPFActivityAlertMessage(job_name, job_number,seg_num, frame_num, time) {}


    void InitMessage(cms::Session *session) {
        msg_.reset(session->createMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setIntProperty("SEGMENT_NUMBER", segment_number_);
            msg_->setIntProperty("FRAME_INDEX", frame_index_);
            msg_->setDoubleProperty("ACTIVITY_DETECT_TIME", activity_time_);
        }
    }

    virtual void ReceiveMessageContent() {
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        segment_number_ = msg_->getIntProperty("SEGMENT_NUMBER");
        frame_index_ = msg_->getIntProperty("FRAME_INDEX");
        activity_time_ = msg_->getDoubleProperty("ACTIVITY_DETECT_TIME");
    }
};

struct AMQJobStatusMessage : AMQMessage, MPFJobStatusMessage {

    AMQJobStatusMessage(const std::string &job_name,
                        const uint32_t job_number,
                        const std::string &msg)
            : MPFJobStatusMessage(job_name, job_number, msg) {}

    void InitMessage(cms::Session *session) {
        msg_.reset(session->createMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setStringProperty("JOB_STATUS", status_message_);
        }
    }

    virtual void ReceiveMessageContent() {
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        status_message_ = msg_->getStringProperty("JOB_STATUS");
    }
};

// Not used in single process, single pipeline stage, architecture
struct AMQSegmentReadyMessage : AMQMessage, MPFSegmentReadyMessage {

    AMQSegmentReadyMessage() = default;
    AMQSegmentReadyMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint32_t seg_num)
            : MPFSegmentReadyMessage(job_name, job_number, seg_num) {}

    void InitMessage(cms::Session *session) {
        msg_.reset(session->createMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setIntProperty("SEGMENT_NUMBER", segment_number_);
        }
    }

    virtual void ReceiveMessageContent() {
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        segment_number_ = msg_->getIntProperty("SEGMENT_NUMBER");
    }
};

// Not used in single process, single pipeline stage, architecture
struct AMQFrameReadyMessage : AMQMessage, MPFFrameReadyMessage {

    AMQFrameReadyMessage() {};
    AMQFrameReadyMessage(const std::string &job_name,
                         const uint32_t job_number,
                         const uint32_t seg_num,
                         const uint32_t index,
                         const uint64_t offset)
            : MPFFrameReadyMessage(job_name, job_number, seg_num, index, offset) {}

    void InitMessage(cms::Session *session) {
        msg_.reset(session->createMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setIntProperty("SEGMENT_NUMBER", segment_number_);
            msg_->setIntProperty("FRAME_INDEX", frame_index_);
            msg_->setLongProperty("FRAME_STORE_OFFSET", frame_offset_bytes_);
        }
    }

    virtual void ReceiveMessageContent() {
        std::vector<std::string> prop_names = msg_->getPropertyNames();
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        segment_number_ = msg_->getIntProperty("SEGMENT_NUMBER");
        frame_index_ = msg_->getIntProperty("FRAME_INDEX");
        frame_offset_bytes_ = msg_->getLongProperty("FRAME_STORE_OFFSET");
    }

};  

// Not used in single process, single pipeline stage, architecture
struct AMQReleaseFrameMessage : AMQMessage, MPFReleaseFrameMessage {

    AMQReleaseFrameMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint64_t offset)
            : MPFReleaseFrameMessage(job_name, job_number, offset) {}


    void InitMessage(cms::Session *session) {
        msg_.reset(session->createMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setLongProperty("FRAME_STORE_OFFSET", frame_offset_bytes_);
        }
    }

    virtual void ReceiveMessageContent() {
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        frame_offset_bytes_ = msg_->getLongProperty("FRAME_STORE_OFFSET");
    }

};

// No longer needed: replaced by ActivityAlert message
// struct AMQNewTrackAlertMessage : AMQMessage, MPFNewTrackAlertMessage {

//     AMQNewTrackAlertMessage(const std::string &job_name,
//                              const uint32_t job_number,
//                              uint32_t seg_num,
//                              uint32_t frame_num,
//                              const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks)
//             : MPFNewTrackAlertMessage(job_name, job_number, seg_num, frame_num, tracks) {}
//     ~AMQNewTrackAlertMessage() = default;

//     void InitMessage(cms::Session *session) {
//         msg_.reset(session->createBytesMessage());
//         if (NULL != msg_) {
//             msg_->setStringProperty("JOB_NAME", job_name_);
//             msg_->setIntProperty("JOB_NUMBER", job_number_);
//             msg_->setIntProperty("SEGMENT_NUMBER", segment_number_);
//             msg_->setIntProperty("FRAME_INDEX", frame_index_);
//             //TODO: Need to pack the tracks vector into a protobuf and
//             //      set the body of the message.
//         }
//     }

//     virtual void ReceiveMessageContent() {
//         job_name_ = msg_->getStringProperty("JOB_NAME");
//         job_number_ = msg_->getIntProperty("JOB_NUMBER");
//         segment_number_ = msg_->getIntProperty("SEGMENT_NUMBER");
//         frame_index_ = msg_->getIntProperty("FRAME_INDEX");
//         //TODO: Need to unpack the tracks vector from the protobuf
//         //      in the message body.
//     }

// };

// Not used in single process, single pipeline stage, architecture
struct AMQVideoWrittenMessage : AMQMessage, MPFVideoWrittenMessage {

    AMQVideoWrittenMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint32_t seg_num,
                           const std::string &path)
            : MPFVideoWrittenMessage(job_name, job_number, seg_num, path) {}

    void InitMessage(cms::Session *session) {
        msg_.reset(session->createMessage());
        if (NULL != msg_) {
            msg_->setStringProperty("JOB_NAME", job_name_);
            msg_->setIntProperty("JOB_NUMBER", job_number_);
            msg_->setIntProperty("SEGMENT_NUMBER", segment_number_);
            msg_->setStringProperty("VIDEO_OUTPUT_PATHNAME", video_output_pathname_);
        }
    }

    virtual void ReceiveMessageContent() {
        job_name_ = msg_->getStringProperty("JOB_NAME");
        job_number_ = msg_->getIntProperty("JOB_NUMBER");
        segment_number_ = msg_->getIntProperty("SEGMENT_NUMBER");
        video_output_pathname_ = msg_->getStringProperty("VIDEO_OUTPUT_PATHNAME");
    }

};

} // namespace MPF

#endif // MPF_AMQ_MESSAGE_H_
