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

#include "BasicAmqMessageReader.h"
#include "ExecutorErrors.h"


using namespace MPF;
using namespace COMPONENT;

BasicAmqMessageReader::BasicAmqMessageReader(const JobSettings &job_settings,
                                             std::shared_ptr<cms::Connection> connection_ptr)
        : job_id_(job_settings.job_id)
        , segment_size_(job_settings.segment_size)
        , connection_(connection_ptr)
        , session_(connection_->createSession())
        , frame_ready_queue_(session_->createQueue(job_settings.frame_ready_queue))
        , segment_ready_consumer_(CreateConsumer(job_settings.segment_ready_queue, *session_))
        , release_frame_consumer_(CreateConsumer(job_settings.release_frame_queue, *session_)) {

    connection_->start();
}


std::unique_ptr<cms::MessageConsumer> BasicAmqMessageReader::CreateConsumer(const std::string &queue_name,
                                                                            cms::Session &session) {
    std::unique_ptr<cms::Queue> queue(session.createQueue(queue_name));
    return std::unique_ptr<cms::MessageConsumer>(session.createConsumer(queue.get()));
}

BasicAmqMessageReader &BasicAmqMessageReader::CreateFrameReadyConsumer(const long segment_number) {
    std::string msg_selector = "SEGMENT_NUMBER = " + std::to_string(segment_number);
    frame_ready_consumer_.reset(session_->createConsumer(frame_ready_queue_.get(), msg_selector));
    return *this;
}

MPFSegmentReadyMessage BasicAmqMessageReader::GetSegmentReadyMsg() {
    std::unique_ptr<cms::Message> msg(segment_ready_consumer_->receive());
    return MPFSegmentReadyMessage(
        msg->getIntProperty("JOB_ID"),
        msg->getIntProperty("SEGMENT_NUMBER"),
        msg->getIntProperty("FRAME_WIDTH"),
        msg->getIntProperty("FRAME_HEIGHT"),
        msg->getIntProperty("FRAME_DATA_TYPE"),
        msg->getIntProperty("FRAME_BYTES_PER_PIXEL"));
}


MPFFrameReadyMessage BasicAmqMessageReader::GetFrameReadyMsg() {
    std::unique_ptr<cms::Message> msg(frame_ready_consumer_->receive());
    return MPFFrameReadyMessage(
        msg->getIntProperty("JOB_ID"),
        msg->getIntProperty("SEGMENT_NUMBER"),
        msg->getIntProperty("FRAME_INDEX"),
        msg->getLongProperty("FRAME_TIMESTAMP"));
}


MPFReleaseFrameMessage BasicAmqMessageReader::GetReleaseFrameMsg() {
    std::unique_ptr<cms::Message> msg(release_frame_consumer_->receive());
    return MPFReleaseFrameMessage(
        msg->getIntProperty("JOB_ID"),
        msg->getIntProperty("FRAME_INDEX"));
}


bool BasicAmqMessageReader::GetReleaseFrameMsgNoWait(MPFReleaseFrameMessage &msg) {
    std::unique_ptr<cms::Message> tmp_msg(release_frame_consumer_->receiveNoWait());
    if (tmp_msg.get() != NULL) {
        msg.job_id = tmp_msg->getIntProperty("JOB_ID");
        msg.frame_index = tmp_msg->getIntProperty("FRAME_INDEX");
        return true;
    }
    else {
        return false;
    }
}
