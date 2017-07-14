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

#ifndef MPF_AMQ_MESSENGER_H_
#define MPF_AMQ_MESSENGER_H_

#include <memory>

#include <activemq/library/ActiveMQCPP.h>
#include <activemq/core/ActiveMQConnectionFactory.h>
#include <activemq/core/PrefetchPolicy.h>
#include <activemq/core/policies/DefaultPrefetchPolicy.h>
#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/MessageConsumer.h>
#include <cms/MessageProducer.h>
#include <log4cxx/logger.h>

#include "MPFMessenger.h"
#include "MPFAMQMessage.h"

namespace MPF {

class MPFAMQMessenger : public MPFMessenger {

 public:
    MPFAMQMessenger() = default;
    ~MPFAMQMessenger() { Shutdown(); }

    MPFMessengerError Connect(const std::string &broker_name,
                              const MPF::COMPONENT::Properties &properties) override;
    MPFMessengerError CreateReceiver(const std::string &queue_name,
                                     const MPF::COMPONENT::Properties &queue_properties,
                                     MPF::MPFReceiver *receiver) override;
    MPFMessengerError CreateSender(const std::string &queue_name,
                                   const MPF::COMPONENT::Properties &queue_properties,
                                   MPF::MPFSender *sender) override;
    MPFMessengerError Start() override;
    MPFMessengerError SendMessage(const MPF::MPFMessage *msg) override;
    MPFMessengerError ReceiveMessage(MPF::MPFMessage *msg) override;
    MPFMessengerError CloseReceiver(MPFReceiver *receiver) override;
    MPFMessengerError CloseSender(MPFSender *sender) override;
    MPFMessengerError Shutdown() override;

  private:
    bool initialized_;
    std::unique_ptr<cms::Connection> connection_;
    std::unique_ptr<cms::Session> session_;

};

} // namespace MPF

#endif // MPF_AMQ_MESSENGER_H_
