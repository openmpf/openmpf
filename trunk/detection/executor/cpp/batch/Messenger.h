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
#include <vector>

#include <cms/BytesMessage.h>
#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/Message.h>
#include <cms/MessageConsumer.h>
#include <cms/MessageProducer.h>

#include "BatchExecutorUtil.h"
#include "JobContext.h"
#include "LoggerWrapper.h"


namespace MPF::COMPONENT {

class AmqConnectionInitializationException : public std::runtime_error {
    using std::runtime_error::runtime_error;
};


class Messenger {
public:
    Messenger(LoggerWrapper logger, std::string_view broker_uri, std::string_view request_queue);

    std::unique_ptr<cms::BytesMessage> ReceiveMessage();

    static AmqMetadata GetAmqMetadata(const cms::Message& message);

    void SendResponse(
            const JobContext& job_context,
            const std::vector<unsigned char>& response_bytes);


    /**
     * Returns the job request message to the ActiveMQ broker. The broker will decide whether to
     * redeliver the message or to send it to the dead letter queue. Rollback is only used when
     * errors occur before the job is passed to the component or while reporting job results.
     * Rollback is NOT used when the component's GetDetections method raises an exception unless
     * sending the error response fails.
    */
    void Rollback();


    static constexpr const char* RESTRICT_MEDIA_TYPES_ENV_NAME = "RESTRICT_MEDIA_TYPES";

    static std::optional<std::string> GetMediaTypeSelector();

private:
    LoggerWrapper logger_;
    std::unique_ptr<cms::Connection> connection_;
    std::unique_ptr<cms::Session> session_;
    std::unique_ptr<cms::MessageConsumer> request_consumer_;
    std::unique_ptr<cms::MessageProducer> response_producer_;

    static std::unique_ptr<cms::Connection> CreateConnection(
            const LoggerWrapper& logger, std::string_view broker_uri);


    static std::unique_ptr<cms::MessageConsumer> CreateRequestConsumer(
            const LoggerWrapper& logger,
            cms::Session& session,
            std::string_view request_queue_name);
};
};
