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
  public:
    MPFAMQReceiver() = default;
    MPFAMQReceiver(cms::MessageConsumer *consumer,
                   cms::Destination *destination)
            : destination_(destination)
            , consumer_(consumer) {}
    ~MPFAMQReceiver() = default;

    void SetConsumer(cms::MessageConsumer *consumer) {
        consumer_.reset(consumer);
    }
    void SetDestination(cms::Destination *destination) {
        destination_.reset(destination);
    }

    MPFMessage* getMessage() override;
  private:
    std::unique_ptr<cms::Destination> destination_;
    std::unique_ptr<cms::MessageConsumer> consumer_;
    
};


class MPFAMQSender : MPFSender {
  public:
    MPFAMQSender() = default;
    MPFAMQSender(cms::MessageProducer *producer,
                 cms::Destination *destination)
            : destination_(destination)
            , producer_(producer) {}

    void SetProducer(cms::MessageProducer *producer) {
        producer_.reset(producer);
    }
    void SetDestination(cms::Destination *destination) {
        destination_.reset(destination);
    }

    ~MPFAMQSender() = default;
    void putMessage(MPFMessage *msg) override;
  private:
    std::unique_ptr<cms::Destination> destination_;
    std::unique_ptr<cms::MessageProducer> producer_;

};


} // namespace MPF

#endif // MPF_AMQ_MESSAGE_H_
