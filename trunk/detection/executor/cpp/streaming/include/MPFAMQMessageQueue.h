/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

#ifndef MPF_AMQ_MESSAGE_QUEUE_H_
#define MPF_AMQ_MESSAGE_QUEUE_H_

#include <string>

#include <activemq/library/ActiveMQCPP.h>
#include <activemq/core/ActiveMQConnectionFactory.h>
#include <activemq/core/PrefetchPolicy.h>
#include <activemq/core/policies/DefaultPrefetchPolicy.h>
#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/MessageConsumer.h>
#include <cms/MessageProducer.h>

#include "MPFMessageQueue.h"

namespace MPF {
 

class MPFAMQMessageQueue : public MPFMessageQueue {

 public:

    MPFAMQMessageQueue() = default;
    virtual ~MPFAMQMessageQueue() = default;

    MPFMessageQueueError Startup(const std::string &broker_name) override;
    MPFMessageQueueError Connect(const std::string &queue_name) override;
    MPFMessageQueueError Disconnect() override;
    MPFMessageQueueError Shutdown() override;

    MPFMessageQueueError SendMessage(MPFMessage *msg,
                                     Properties &msg_properties) override;
    MPFMessageQueueError ReceiveMessage(MPFMessage *msg,
                                        Properties &msg_properties) override;
    MPFMessageQueueError TryReceiveMessage(MPFMessage *msg,
                                           Properties &msg_properties) override;

  private:
  bool initialized;
  activemq::core::ActiveMQConnectionFactory* connection_factory_;
  cms::Connection* connection_;
  cms::Session* session_;
  cms::Destination* request_destination_;
  cms::MessageConsumer* request_consumer_;
  cms::MessageProducer* response_producer_;
  const log4cxx::LoggerPtr &main_logger_;

};

} // namespace MPF

#endif // MPF_AMQ_MESSAGE_QUEUE_H_
