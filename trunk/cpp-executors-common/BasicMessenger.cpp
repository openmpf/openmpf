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

#include <exception>
#include <vector>
#include <utility>

#include <activemq/library/ActiveMQCPP.h>
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
#include <activemq/core/ActiveMQConnectionFactory.h>
#pragma GCC diagnostic pop
#include <activemq/core/policies/DefaultPrefetchPolicy.h>

#include <boost/algorithm/string.hpp>

#include "ExecutorUtil.h"
#include "ExitSignalBlocker.h"
#include "Shutdown.h"

#include "BasicMessenger.h"


namespace MPF {

using ExecutorUtil::AsUniquePtr;

BasicMessenger::BasicMessenger(
        LoggerWrapper logger, std::string_view broker_uri, std::string_view request_queue,
        bool enable_media_type_filter)
try
        : logger_{std::move(logger)}
        , connection_{CreateConnection(logger_, broker_uri)}
        , session_{connection_->createSession(cms::Session::SESSION_TRANSACTED)}
        , request_consumer_{CreateRequestConsumer(
                logger_, *session_, request_queue, enable_media_type_filter)}
        , response_producer_{session_->createProducer()} {
    // Using a weak_ptr ensures that if this is destructed prior to the shutdown action running,
    // the shutdown action will not keep the connection alive.
    Shutdown::AddShutdownAction([weak_conn=std::weak_ptr{connection_}] {
        if (auto conn = weak_conn.lock()) {
            // Closing the connection will unblock threads calling cms::MessageConsumer::receive.
            conn->close();
        }
        else {
            // Our destructor already closed and destructed the connection, so there is nothing for
            // the shutdown action to do.
        }
    });
    connection_->start();
}
catch (const std::exception& e) {
    throw AmqConnectionInitializationException(e.what());
}


BasicMessenger::BasicMessenger(
        LoggerWrapper logger,
        std::string_view broker_uri,
        std::string_view request_queue,
        WithMediaTypeFilter)
    : BasicMessenger(std::move(logger), broker_uri, request_queue, true) {
}

void BasicMessenger::SendResponse(
        const cms::Destination& destination, cms::Message& message, int priority) {
    response_producer_->send(
            &destination,
            &message,
            cms::Message::DEFAULT_DELIVERY_MODE,
            priority,
            cms::Message::DEFAULT_TIME_TO_LIVE);
    session_->commit();
}


void BasicMessenger::Rollback() {
    session_->rollback();
}

std::unique_ptr<cms::Message> BasicMessenger::SendRequestReply(
            std::string_view queue_name,
            cms::Message& request_message) {
    auto response_queue = AsUniquePtr(session_->createTemporaryQueue());
    auto response_consumer = AsUniquePtr(session_->createConsumer(response_queue.get()));
    request_message.setCMSReplyTo(response_queue.get());

    auto request_queue = AsUniquePtr(session_->createQueue(std::string{queue_name}));
    response_producer_->send(request_queue.get(), &request_message);
    session_->commit();

    auto response = AsUniquePtr(response_consumer->receive());
    session_->commit();
    return response;
}


std::unique_ptr<cms::Message> BasicMessenger::Receive() {
    // Block signals while waiting for the next message. If a signal does arrive during the call
    // to receive, receive will return null. Then, CheckQuit will throw an exception.
    // The destructor for ExitSignalBlocker unblocks the signals. This ensures that if a signal is
    // received while a component is running, the program will exit immediately.
    ExitSignalBlocker blocker;
    auto result = AsUniquePtr(request_consumer_->receive());
    Shutdown::CheckQuit();
    return result;
}


std::unique_ptr<cms::Connection> BasicMessenger::CreateConnection(
        const LoggerWrapper& logger, std::string_view broker_uri)  {
    activemq::library::ActiveMQCPP::initializeLibrary();
    logger.Info("Connecting to ActiveMQ broker at: ", broker_uri);
    activemq::core::ActiveMQConnectionFactory connection_factory{std::string{broker_uri}};
    connection_factory.setWatchTopicAdvisories(false);

    bool hasPrefetch = broker_uri.find("jms.prefetchPolicy.all") != std::string_view::npos;
    bool hasPrefetchSetToZero
        = broker_uri.find("jms.prefetchPolicy.all=0") != std::string_view::npos;
    if (!hasPrefetch || hasPrefetchSetToZero) {
        // Since the consumer is slow, it is best to set the prefetch size to 0. When the
        // connection string sets prefetch to 0, the CMS library ends up setting it to 1, unless we
        // create the DefaultPrefetchPolicy object. If the connection string omits the prefetch
        // policy or sets it to 0, we use the DefaultPrefetchPolicy object. If the connection
        // string sets the prefetch policy to something other than 0, we allow the CMS library to
        // handle it, because it handles non-zero prefetch policies correctly.
        auto policy = std::make_unique<activemq::core::policies::DefaultPrefetchPolicy>();
        policy->setQueuePrefetch(0);
        policy->setTopicPrefetch(0);
        connection_factory.setPrefetchPolicy(policy.release());
    }
    // Block signals while ActiveMQ threads start so that they inherit the correct mask.
    ExitSignalBlocker blocker;
    return AsUniquePtr(connection_factory.createConnection());
}


std::unique_ptr<cms::MessageConsumer> BasicMessenger::CreateRequestConsumer(
        const LoggerWrapper& logger,
        cms::Session& session,
        std::string_view request_queue_name,
        bool enable_media_type_filter) {

    auto request_queue = AsUniquePtr(session.createQueue(std::string{request_queue_name}));
    if (enable_media_type_filter) {
        if (auto media_type_selector = GetMediaTypeSelector()) {
            logger.Info("Creating ActiveMQ consumer for queue ", request_queue_name,
                            " with selector: ", *media_type_selector);
            return AsUniquePtr(session.createConsumer(request_queue.get(), *media_type_selector));
        }
    }
    logger.Info("Creating ActiveMQ consumer for queue: ", request_queue_name);
    return AsUniquePtr(session.createConsumer(request_queue.get()));
}


std::optional<std::string> BasicMessenger::GetMediaTypeSelector() {
    auto selector = ExecutorUtil::GetEnv(RESTRICT_MEDIA_TYPES_ENV_NAME);
    if (!selector) {
        return {};
    }

    std::vector<std::string> tokens;
    boost::split(tokens, *selector, boost::is_any_of(","), boost::algorithm::token_compress_on);

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
        return {};
    }
    std::string joined_tokens = boost::join(quoted_tokens, ", ");
    return "MediaType in (" + joined_tokens + ')';
}

} // namespace MPF
