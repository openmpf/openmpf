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

#ifndef MPF_AMQ_MESSAGE_H_
#define MPF_AMQ_MESSAGE_H_

#include <memory>

#include <cms/MessageConsumer.h>
#include <cms/MessageProducer.h>

#include "MPFMessage.h"


namespace MPF {

class MPFAMQReceiver : MPFReceiver {
    MPFAMQReceiver(cms::MessageConsumer);
    ~MPFAMQReceiver() = default;
    MPFMessage* getMessage() override;
  private:
    std::unique_ptr<cms::Destination> request_destination_;
    std::unique_ptr<cms::MessageConsumer> request_consumer_;
    
};


class MPFAMQSender : MPFSender {
    MPFAMQSender(const std::string queue_name);
    ~MPFAMQSender() = default;
    void putMessage(MPFMessage *msg) override;
  private:
    std::unique_ptr<cms::Destination> request_destination_;
    std::unique_ptr<cms::MessageProducer> response_producer_;

};


} // namespace MPF

#endif // MPF_AMQ_MESSAGE_H_
