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

#include "BasicAmqMessageReader.h"
#include "MPFMessage.h"

namespace MPF {

template<>
bool BasicAmqMessageReader<MPFSegmentReadyMessage>::GetMsgNoWait(MPFSegmentReadyMessage &msg) {
    std::unique_ptr<cms::Message> tmp_msg(consumer_->receiveNoWait());
    if (NULL != tmp_msg.get()) {
        msg.job_id = tmp_msg->getLongProperty("JOB_ID");
        msg.segment_number = tmp_msg->getIntProperty("SEGMENT_NUMBER");
        msg.frame_width = tmp_msg->getIntProperty("FRAME_WIDTH");
        msg.frame_height = tmp_msg->getIntProperty("FRAME_HEIGHT");
        msg.cvType = tmp_msg->getIntProperty("FRAME_DATA_TYPE");
        msg.bytes_per_pixel = tmp_msg->getIntProperty("FRAME_BYTES_PER_PIXEL");
        return true;
    }
    else {
        return false;
    }
}


template<>
bool BasicAmqMessageReader<MPFFrameReadyMessage>::GetMsgNoWait(MPFFrameReadyMessage &msg) {
    std::unique_ptr<cms::Message> tmp_msg(consumer_->receiveNoWait());
    if (NULL != tmp_msg.get()) {
        msg.job_id = tmp_msg->getLongProperty("JOB_ID");
        msg.segment_number = tmp_msg->getIntProperty("SEGMENT_NUMBER");
        msg.frame_index = tmp_msg->getIntProperty("FRAME_INDEX");
        msg.frame_timestamp = tmp_msg->getLongProperty("FRAME_READ_TIMESTAMP");
        msg.process_this_frame = tmp_msg->getBooleanProperty("PROCESS_FRAME_FLAG");
        return true;
    }
    else {
        return false;
    }
}

}
