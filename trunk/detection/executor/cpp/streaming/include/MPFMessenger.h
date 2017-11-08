/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
#include <stdexcept>

#include "MPFMessage.h"

namespace MPF {

class MPFMessageException : std::runtime_error {
  public:

    enum MPFMessageError {
        UNRECOGNIZED_ERROR,
        UNSPECIFIED_ERROR,
        MESSENGER_NOT_INITIALIZED,
        MESSENGER_MISSING_PROPERTY,
        MESSENGER_INVALID_PROPERTY
    };

    explicit MPFMessageException(const char *msg, MPFMessageError e) 
            : std::runtime_error(msg), error_type_(e) {}
    explicit MPFMessageException(const std::string &msg, MPFMessageError e) 
            : std::runtime_error(msg), error_type_(e) {}
    virtual ~MPFMessageException() = default;

    MPFMessageError getErrorType() {
        return error_type_;
    }

  protected:
    MPFMessageError error_type_;
};

class MPFMessenger {
  public: 
    virtual ~MPFMessenger() = default;

    // Connect to the message passing system
    virtual void Connect(const std::string &broker_name,
                         const MPF::COMPONENT::Properties &properties) = 0;
    virtual void Start() = 0;
    virtual void Stop() = 0;
    virtual void Shutdown() = 0;

  protected:
    bool connected_;
    MPFMessenger() : connected_(false) {}

};

class MPFInputMessenger : MPFMessenger {
  public: 
    virtual ~MPFInputMessenger() = default;
    MPFInputMessenger() = default;

    virtual void SetInputQueue(const std::string &queue_name,
                               const MPF::COMPONENT::Properties &queue_properties) = 0;

    //blocking receive
    virtual std::unique_ptr<MPFMessage> GetMessage() = 0;
    // blocking receive with timeout
    virtual std::unique_ptr<MPFMessage> GetMessage(const uint32_t timeout_msec) = 0;
    // non-blocking receive
    virtual std::unique_ptr<MPFMessage> TryGetMessage() = 0;

};

class MPFOutputMessenger : MPFMessenger {
  public: 
    virtual ~MPFOutputMessenger() = default;
    MPFOutputMessenger() = default;

    virtual void SetOutputQueue(const std::string &queue_name,
                           const MPF::COMPONENT::Properties &queue_properties) = 0;

    // blocking send
    virtual void PutMessage(const MPFMessage *msg) = 0;
};

} // namespace MPF

#endif // MPF_MESSENGER_H_
