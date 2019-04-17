/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

//TODO: For future use.
namespace MPF {

enum MPFMessageError {
    MESSENGER_UNRECOGNIZED_ERROR,
    MESSENGER_UNSPECIFIED_ERROR,
    MESSENGER_NOT_INITIALIZED,
    MESSENGER_MISSING_PROPERTY,
    MESSENGER_INVALID_PROPERTY,
    MESSENGER_CONNECTION_FAILURE,
    MESSENGER_START_FAILURE,
    MESSENGER_STOP_FAILURE,
    MESSENGER_SHUTDOWN_FAILURE,
    MESSENGER_NOT_CONNECTED,
    MESSENGER_QUEUE_NOT_INITIALIZED,
    MESSENGER_INIT_QUEUE_FAILURE,
    MESSENGER_CREATE_CONSUMER_FAILURE,
    MESSENGER_CREATE_PRODUCER_FAILURE,
    MESSENGER_GET_MESSAGE_FAILURE,
    MESSENGER_PUT_MESSAGE_FAILURE,
    MESSENGER_CLOSE_FAILURE
};

// This exception is thrown when a system or other library exception
// is caught, to capture an error type that can be returned the the
// MPF system in the job status message.
class MPFMessageException : public std::runtime_error {
  public:

    virtual ~MPFMessageException() = default;

    explicit MPFMessageException(const char *msg, MPFMessageError e) 
            : std::runtime_error(msg), error_type_(e) {}
    explicit MPFMessageException(const std::string &msg, MPFMessageError e) 
            : std::runtime_error(msg), error_type_(e) {}

    MPFMessageError getErrorType() {
        return error_type_;
    }

  protected:
    MPFMessageError error_type_;
};

class MPFMessagingManager {
  public: 
    virtual ~MPFMessagingManager() = default;

    // Connect to the message passing system
    virtual void Connect(const std::string &broker_name,
                         const MPF::COMPONENT::Properties &properties) = 0;
    virtual void Start() = 0;
    virtual void Stop() = 0;
    virtual void Shutdown() = 0;

  protected:
    MPFMessagingManager() = default;
};

} // namespace MPF

#endif // MPF_MESSENGER_H_
