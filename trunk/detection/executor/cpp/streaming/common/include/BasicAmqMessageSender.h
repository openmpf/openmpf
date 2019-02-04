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

#include <cms/Session.h>
#include <cms/MessageProducer.h>

#include <MPFDetectionComponent.h>

#include "MPFMessagingConnection.h"


namespace MPF {
// Creates a producer of a message type T.
template<typename T>
class BasicAmqMessageSender {

  public:
    explicit BasicAmqMessageSender(const std::string &queue_name,
                                   MPFMessagingConnection &connection)
            : session_(connection.GetSession())
            , queue_(session_->createQueue(queue_name))
            , producer_(session_->createProducer(queue_.get())) {}

    void SendMsg(const T &msg);


  private:
    std::shared_ptr<cms::Session> session_;
    std::unique_ptr<cms::Queue> queue_;
    std::unique_ptr<cms::MessageProducer> producer_;

};
}

#endif //MPF_BASICAMQMESSAGESENDER_H
