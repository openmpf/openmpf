/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

#pragma once

#include <memory>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>

#include <cms/Connection.h>
#include <cms/Destination.h>
#include <cms/Message.h>
#include <cms/MessageConsumer.h>
#include <cms/MessageProducer.h>
#include <cms/Session.h>

#include "AmqMessageType.h"
#include "LoggerWrapper.h"

namespace MPF {

class AmqConnectionInitializationException : public std::runtime_error {
    using std::runtime_error::runtime_error;
};

class BasicMessenger {
public:
    static struct WithMediaTypeFilter {} with_media_type_filter;

    BasicMessenger(
        LoggerWrapper logger, std::string_view broker_uri, std::string_view request_queue,
        bool enable_media_type_filter = false);

    BasicMessenger(
        LoggerWrapper logger,
        std::string_view broker_uri,
        std::string_view request_queue,
        WithMediaTypeFilter);


    template <typename TMsg>
    std::unique_ptr<TMsg> ReceiveMessage() {
        while (true) {
            auto message = Receive();
            if (auto casted_message = dynamic_cast<TMsg*>(message.get())) {
                // The cast was successful, so message and casted_message point to the same object.
                // message.release() is called so that message's destructor does not delete the
                // message object.
                (void) message.release();
                return std::unique_ptr<TMsg>{casted_message};
            }
            logger_.Error(
                "Error: Expected an ActiveMQ ", AmqMessageType<TMsg>::name,
                ", but a different message type was received.");
            Rollback();
        }
    }

    template <typename TMsg>
    std::unique_ptr<TMsg> NewMessage() {
        return std::unique_ptr<TMsg>{AmqMessageType<TMsg>::create(*session_)};
    }


    void SendResponse(const cms::Destination& destination, cms::Message& message, int priority);

    void Rollback();

    std::unique_ptr<cms::Message> SendRequestReply(
            std::string_view queue_name, cms::Message& request_message);


    static constexpr const char* RESTRICT_MEDIA_TYPES_ENV_NAME = "RESTRICT_MEDIA_TYPES";

    static std::optional<std::string> GetMediaTypeSelector();

private:
    LoggerWrapper logger_;

    // Needs to be a shared_ptr, rather than unique_ptr, because this class and a shutdown action
    // both need access to the connection.
    std::shared_ptr<cms::Connection> connection_;

    std::unique_ptr<cms::Session> session_;

    std::unique_ptr<cms::MessageConsumer> request_consumer_;

    std::unique_ptr<cms::MessageProducer> response_producer_;

    std::unique_ptr<cms::Message> Receive();

    static std::unique_ptr<cms::Connection> CreateConnection(
            const LoggerWrapper& logger, std::string_view broker_uri);

    static std::unique_ptr<cms::MessageConsumer> CreateRequestConsumer(
            const LoggerWrapper& logger,
            cms::Session& session,
            std::string_view request_queue_name,
            bool enable_media_type_filter);
};

} // namespace MPF
