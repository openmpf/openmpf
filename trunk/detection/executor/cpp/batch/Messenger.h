/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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
#include <string>
#include <string_view>
#include <vector>

#include <cstdlib>
#include <cstring>
#include <stdexcept>

#include <boost/algorithm/string.hpp>

#include <activemq/library/ActiveMQCPP.h>
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
#include <activemq/core/ActiveMQConnectionFactory.h>
#pragma GCC diagnostic pop
#include <activemq/core/PrefetchPolicy.h>
#include <activemq/core/policies/DefaultPrefetchPolicy.h>
#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/MessageConsumer.h>
#include <cms/MessageProducer.h>

#include "JobContext.h"

namespace MPF::COMPONENT {


class AmqConnectionInitializationException : public std::runtime_error {
    using std::runtime_error::runtime_error;
};


template <typename Logger>
class Messenger {
public:

    Messenger(Logger& logger, std::string_view broker_uri, std::string_view request_queue)
    try
            : logger_(logger)
            , connection_{CreateConnection(logger_, broker_uri)}
            , session_{connection_->createSession(cms::Session::SESSION_TRANSACTED)}
            , request_consumer_{CreateRequestConsumer(logger, *session_, request_queue)}
            , response_producer_{session_->createProducer()} {
        connection_->start();
    }
    catch (const std::exception& e) {
        throw AmqConnectionInitializationException(e.what());
    }


    std::unique_ptr<cms::BytesMessage> ReceiveMessage() {
        while (true) {
            std::unique_ptr<cms::Message> message{request_consumer_->receive()};
            if (auto bytes_message = dynamic_cast<cms::BytesMessage*>(message.get())) {
                // The cast was successful, so message and bytes_message point to the same object.
                // message.release() is called so that message's destructor does not delete the
                // message object.
                message.release();
                return std::unique_ptr<cms::BytesMessage>{bytes_message};
            }
            logger_.Error(
                "Error: Expected an ActiveMQ BytesMessage, but a different message type was received.");
            Rollback();
        }
    }

    void SendResponse(
            const JobContext& job_context,
            const std::vector<unsigned char>& response_bytes) {
        std::unique_ptr<cms::BytesMessage> message{session_->createBytesMessage()};
        message->setStringProperty("CorrelationId", job_context.correlation_id);
        message->setLongProperty("JobId", job_context.job_id);
        message->setStringProperty("breadcrumbId", job_context.bread_crumb_id);
        message->setIntProperty("SplitSize", job_context.split_size);
        message->writeBytes(response_bytes);

        response_producer_->send(
                job_context.response_queue.get(),
                message.get(),
                cms::Message::DEFAULT_DELIVERY_MODE,
                job_context.cms_priority,
                cms::Message::DEFAULT_TIME_TO_LIVE);
        session_->commit();
    }


    /**
     * Returns the job request message to the ActiveMQ broker. The broker will decide whether to
     * redeliver the message or to send it to the dead letter queue. Rollback is only used when
     * errors occur before the job is passed to the component or while reporting job results.
     * Rollback is NOT used when the component's GetDetections method raises an exception unless
     * sending the error response fails.
    */
    void Rollback() {
        session_->rollback();
    }


    static constexpr const char* RESTRICT_MEDIA_TYPES_ENV_NAME = "RESTRICT_MEDIA_TYPES";

    static std::string GetMediaTypeSelector() {
        auto selector = std::getenv(RESTRICT_MEDIA_TYPES_ENV_NAME);
        if (selector == nullptr || std::strlen(selector) == 0) {
            return "";
        }

        std::vector<std::string> tokens;
        boost::split(tokens, selector, boost::is_any_of(","), boost::algorithm::token_compress_on);

        std::vector<std::string> quoted_tokens;
        for (const auto &raw_token : tokens) {
            auto cleaned_token = boost::trim_copy(raw_token);
            boost::to_upper(cleaned_token);
            if (cleaned_token.empty()) {
                continue;
            }
            if (cleaned_token != "VIDEO" && cleaned_token != "IMAGE" && cleaned_token != "AUDIO"
                    && cleaned_token != "UNKNOWN") {
                throw std::invalid_argument(
                        "Expected the RESTRICT_MEDIA_TYPES environment variable contain"
                        " a comma-separated list containing one or more of: VIDEO, IMAGE, AUDIO,"
                        " UNKNOWN");
            }
            quoted_tokens.emplace_back('\'' + cleaned_token + '\'');
        }

        if (quoted_tokens.empty()) {
            return "";
        }
        std::string joined_tokens = boost::join(quoted_tokens, ", ");
        return "MediaType in (" + joined_tokens + ')';
    }

private:
    Logger logger_;
    std::unique_ptr<cms::Connection> connection_;
    std::unique_ptr<cms::Session> session_;
    std::unique_ptr<cms::MessageConsumer> request_consumer_;
    std::unique_ptr<cms::MessageProducer> response_producer_;

    static std::unique_ptr<cms::Connection> CreateConnection(
                Logger &logger, std::string_view broker_uri) {
        activemq::library::ActiveMQCPP::initializeLibrary();
        logger.Info("Connecting to ActiveMQ broker at: ", broker_uri);
        activemq::core::ActiveMQConnectionFactory connection_factory{std::string{broker_uri}};

        bool hasPrefetch = broker_uri.find("jms.prefetchPolicy.all") != std::string_view::npos;
        bool hasPrefetchSetToZero
            = broker_uri.find("jms.prefetchPolicy.all=0") != std::string_view::npos;
        if (!hasPrefetch || hasPrefetchSetToZero) {
            auto policy = std::make_unique<activemq::core::policies::DefaultPrefetchPolicy>();
            policy->setQueuePrefetch(0);
            policy->setTopicPrefetch(0);
            connection_factory.setPrefetchPolicy(policy.release());
        }
        return std::unique_ptr<cms::Connection>{connection_factory.createConnection()};
    }


    static std::unique_ptr<cms::MessageConsumer> CreateRequestConsumer(
            Logger &logger,
            cms::Session &session,
            std::string_view request_queue_name) {
        std::unique_ptr<cms::Destination> request_queue{
            session.createQueue(std::string{request_queue_name})};

        if (auto media_type_selector = GetMediaTypeSelector(); media_type_selector.empty()) {
            logger.Info("Creating ActiveMQ consumer for queue: ", request_queue_name);
            return std::unique_ptr<cms::MessageConsumer>{
                    session.createConsumer(request_queue.get())};
        }
        else {
            logger.Info("Creating ActiveMQ consumer for queue ", request_queue_name,
                         " with selector: ", media_type_selector);
            return std::unique_ptr<cms::MessageConsumer>{
                    session.createConsumer(request_queue.get(), media_type_selector)};
        }
    }
};
};
