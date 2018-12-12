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


#ifndef MPF_BASICAMQMESSAGEREADER_H
#define MPF_BASICAMQMESSAGEREADER_H

#include <memory>
#include <string>
#include <unordered_map>

#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/MessageConsumer.h>

#include "JobSettings.h"


namespace MPF {

// Attaches a consumer to a queue. The consumer may recreate itself with a
// selector if necessary.
template<typename T>
class BasicAmqMessageReader {

  public:
    BasicAmqMessageReader(const MPF::COMPONENT::JobSettings &job_settings,
                          const std::string &queue_name,
                          std::shared_ptr<cms::Connection> connection_ptr)
        : job_id_(job_settings.job_id)
        , segment_size_(job_settings.segment_size)
        , connection_(connection_ptr)
        , session_(connection_->createSession())
        , queue_(session_->createQueue(queue_name))
        , consumer_(session_->createConsumer(queue_.get())) {}

    BasicAmqMessageReader<T> &RecreateConsumerWithSelector(const std::string &selector) {
        consumer_.reset(session_->createConsumer(queue_.get(), selector));
        return *this;
    }

    bool GetMsgNoWait(T &msg);

  private:

    const long job_id_;
    const int segment_size_;

    std::shared_ptr<cms::Connection> connection_;
    std::unique_ptr<cms::Session> session_;
    std::unique_ptr<cms::Queue> queue_;
    std::unique_ptr<cms::MessageConsumer> consumer_;
};
}


#endif //MPF_BASICAMQMESSAGESENDER_H
