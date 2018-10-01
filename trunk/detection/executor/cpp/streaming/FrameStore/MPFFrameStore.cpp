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


#include <errno.h>
#include "MPFFrameStore.h"
#include "detectionComponentUtils.h"

using namespace MPF;
using namespace COMPONENT;
using namespace DetectionComponentUtils;


MPFFrameStore::MPFFrameStore(const Properties &props)
        : frame_byte_size_(0)
        , context_({redisConnect((GetProperty<std::string>(props,"FRAME_STORE_HOSTNAME", "localhost")).c_str(),
                    GetProperty<int>(props,"FRAME_STORE_PORT_NUMBER", 6379)), redisFree})
        , key_prefix_(GetProperty<std::string>(props,"JOB_NAME", "") + ":frame:") {}


// Convert the frame index to a key and copy the data from the
// frame into the database.
void MPFFrameStore::StoreFrame(const cv::Mat &frame, const size_t frame_index) {
    if (0 == frame_byte_size_) {
        frame_byte_size_ = frame.rows*frame.cols*frame.elemSize();
    }
    std::string frame_key = CreateKey(frame_index);
    redis_reply_ptr reply{static_cast<redisReply *>(redisCommand(context_.get(), "SET key:%s %b", frame_key.c_str(), frame.data, frame_byte_size_)), freeReplyObject};
    CheckReply(reply, std::string("SET key:" + frame_key));
}



// Convert the frame index to a key and copy the data from the
// database into the frame.
void MPFFrameStore::GetFrame(cv::Mat &frame, const size_t frame_index) {

    if (0 == frame_byte_size_) {
        frame_byte_size_ = frame.rows*frame.cols*frame.elemSize();
    }
    std::string frame_key = CreateKey(frame_index);
    redis_reply_ptr reply{static_cast<redisReply *>(redisCommand(context_.get(), "GET key:%s", frame_key.c_str())), freeReplyObject};
    // Check for errors
    CheckReply(reply, std::string("GET key:" + frame_key));

    if (reply->type == REDIS_REPLY_STRING) {
        // Copy the data out of the reply string
        assert(reply->len == frame_byte_size_);
        memcpy(frame.data, reply->str, reply->len);
    }
    else {
        std::string err_str = "Redis command Get key:" + frame_key + " returned unrecognized type: " + std::to_string(reply->type);
        throw std::runtime_error(err_str);
    }

}



void MPFFrameStore::DeleteFrame(const size_t frame_index) {
    // Convert the frame index to a key and delete the frame data from
    // the database.
    std::string frame_key = CreateKey(frame_index);
    redis_reply_ptr reply{static_cast<redisReply *>(redisCommand(context_.get(), "DEL %s", frame_key.c_str())), freeReplyObject};
    // Check for errors
    CheckReply(reply, std::string("DEL key:" + frame_key));

}


std::string MPFFrameStore::CreateKey(const size_t index) {
    return key_prefix_ + std::to_string(index);
}


void MPFFrameStore::CheckReply(const redis_reply_ptr &reply, const std::string &redis_cmd) {
    if ((!reply)|| (reply->type == REDIS_REPLY_ERROR)) {
        std::string err_str = "Redis command " + redis_cmd + " failed: " + std::string(strerror(errno));
        if (reply) err_str += ": " + std::string(reply->str);
        throw std::runtime_error(err_str);
    }
}
