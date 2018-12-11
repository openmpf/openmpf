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
#include <hiredis.h>
#include "MPFFrameStore.h"
#include "detectionComponentUtils.h"

using namespace MPF;
using namespace COMPONENT;
using namespace DetectionComponentUtils;

using redis_context_ptr = std::unique_ptr<redisContext, decltype(&redisFree)>;
using redis_reply_ptr = std::unique_ptr<redisReply, decltype(&freeReplyObject)>;

class MPF::FrameStoreImpl {
  public:

    explicit FrameStoreImpl(const JobSettings &settings) 
            : context_({redisConnect(settings.frame_store_server_hostname.c_str(),
                        settings.frame_store_server_portnum), redisFree}) {}

    redisContext *GetContext() { return context_.get(); }
    void CheckReply(const redis_reply_ptr &reply, const std::string &redis_cmd);

  private:
    redis_context_ptr context_;

};


MPFFrameStore::MPFFrameStore(const JobSettings &settings)
        : frame_byte_size_(0)
        , capacity_(settings.frame_store_capacity)
        , frames_in_store_(0)
        , impl_ptr_(new FrameStoreImpl(settings))
        , key_prefix_("job" + std::to_string(settings.job_id) + ":frame:") {}


// Convert the frame index to a key and copy the data from the
// frame into the database.
void MPFFrameStore::StoreFrame(const cv::Mat &frame, const size_t frame_index) {
    if (0 == frame_byte_size_) {
        frame_byte_size_ = frame.rows*frame.cols*frame.elemSize();
    }
    std::string frame_key = CreateKey(frame_index);
    redis_reply_ptr reply{static_cast<redisReply *>(redisCommand(impl_ptr_->GetContext(), "SET %s %b", frame_key.c_str(), frame.data, frame_byte_size_)), freeReplyObject};
    impl_ptr_->CheckReply(reply, std::string("SET " + frame_key));
    ++frames_in_store_;
}



// Convert the frame index to a key and copy the data from the
// database into the frame.
void MPFFrameStore::GetFrame(cv::Mat &frame, const size_t frame_index) {

    if (0 == frame_byte_size_) {
        frame_byte_size_ = frame.rows*frame.cols*frame.elemSize();
    }
    std::string frame_key = CreateKey(frame_index);
    redis_reply_ptr reply{static_cast<redisReply *>(redisCommand(impl_ptr_->GetContext(), "GET %s", frame_key.c_str())), freeReplyObject};
    // Check for errors
    impl_ptr_->CheckReply(reply, std::string("GET " + frame_key));

    if (reply->type == REDIS_REPLY_STRING) {
        // Copy the data out of the reply string
        assert(reply->len == frame_byte_size_);
        memcpy(frame.data, reply->str, reply->len);
    }
    else {
        std::string err_str = "Redis command Get " + frame_key + " returned unrecognized type: " + std::to_string(reply->type);
        throw std::runtime_error(err_str);
    }

}



void MPFFrameStore::DeleteFrame(const size_t frame_index) {
    // Convert the frame index to a key and delete the frame data from
    // the database.
    std::string frame_key = CreateKey(frame_index);
    redis_reply_ptr reply{static_cast<redisReply *>(redisCommand(impl_ptr_->GetContext(), "DEL %s", frame_key.c_str())), freeReplyObject};
    // Check for errors
    impl_ptr_->CheckReply(reply, std::string("DEL " + frame_key));
    --frames_in_store_;

}

void MPFFrameStore::DeleteMultipleFrames(const std::vector<size_t> &frame_indices) {

    std::string key_string;
    for (const auto &index : frame_indices) {
        key_string = CreateKey(index);
        redis_reply_ptr reply{static_cast<redisReply *>(redisCommand(impl_ptr_->GetContext(), "DEL %s", key_string.c_str())), freeReplyObject};
        impl_ptr_->CheckReply(reply, std::string("DELETE " + key_string));
    }
    frames_in_store_ -= frame_indices.size();
}



std::string MPFFrameStore::CreateKey(const size_t index) {
    return key_prefix_ + std::to_string(index);
}


void FrameStoreImpl::CheckReply(const redis_reply_ptr &reply, const std::string &redis_cmd) {
    if ((NULL == reply.get()) || (reply->type == REDIS_REPLY_ERROR)) {
        std::string err_str = "Redis command " + redis_cmd + " failed: " + std::string(strerror(errno));
        if (reply) err_str += ": " + std::string(reply->str);
        throw std::runtime_error(err_str);
    }
}
