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

#ifndef MPF_MESSENGER_H_
#define MPF_MESSENGER_H_

#include <string>

#include "MPFMessage.h"

namespace MPF {

enum MPFMessengerError {
    MESSENGER_SUCCESS = 0,
    MESSENGER_NOT_INITIALIZED,
    MESSENGER_MISSING_PROPERTY,
    MESSENGER_INVALID_PROPERTY,
    MESSENGER_PROPERTY_IS_NOT_INT,
    MESSENGER_PROPERTY_IS_NOT_FLOAT,
    MESSENGER_OTHER_ERROR
};

class MPFMessenger {

 public:

    virtual ~MPFMessenger() = default;

    virtual MPFMessengerError Connect(const std::string &broker_name,
                                      const MPF::COMPONENT::Properties &properties) = 0;
    virtual MPFMessengerError CreateReceiver(const std::string &queue_name,
                                             const MPF::COMPONENT::Properties &queue_properties,
                                             MPF::MPFReceiver &receiver) = 0;
    virtual MPFMessengerError CreateSender(const std::string &queue_name,
                                           const MPF::COMPONENT::Properties &queue_properties,
                                           MPF::MPFSender &sender) = 0;
    virtual MPFMessengerError Start() = 0;
    virtual MPFMessengerError CloseReceiver(MPF::MPFReceiver *receiver) = 0;
    virtual MPFMessengerError CloseSender(MPF::MPFSender *sender) = 0;
    virtual MPFMessengerError Shutdown() = 0;

  protected:
    MPFMessenger() = default;

};

} // namespace MPF

#define MPF_MESSENGER_CREATOR(name) \
  extern "C" MPF::COMPONENT::MPFMessenger* messenger_creator() { \
      return new (name);                                  \
  }

#define MPF_MESSENGER_DELETER() \
  extern "C" MPF::COMPONENT::MPFMessenger* messenger_deleter(MPFMessenger *messenger_P_) { \
    delete messenger_P_; \
  }

#endif // MPF_MESSAGE_QUEUE_H_
