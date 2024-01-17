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

#include <utility>

#include <activemq/library/ActiveMQCPP.h>
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
#include <activemq/core/ActiveMQConnectionFactory.h>
#pragma GCC diagnostic pop
#include <activemq/core/PrefetchPolicy.h>
#include <activemq/core/policies/DefaultPrefetchPolicy.h>

#include <boost/algorithm/string.hpp>

#include "Messenger.h"


namespace MPF::COMPONENT {

Messenger::Messenger(
        LoggerWrapper logger, std::string_view broker_uri, std::string_view request_queue)
    try
    : logger_(std::move(logger))
    , connection_{CreateConnection(logger_, broker_uri)}
    , session_{connection_->createSession(cms::Session::SESSION_TRANSACTED)}
    , request_consumer_{CreateRequestConsumer(logger_, *session_, request_queue)}
    , response_producer_{session_->createProducer()} {
        connection_->start();
    }
    catch (const std::exception& e) {
        throw AmqConnectionInitializationException(e.what());
    }


std::unique_ptr<cms::BytesMessage> Messenger::ReceiveMessage() {
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


AmqMetadata Messenger::GetAmqMetadata(const cms::Message& message) {
    return {
        std::unique_ptr<cms::Destination>{message.getCMSReplyTo()->clone()},
        message.getCMSPriority(),
        message.getStringProperty("CorrelationId"),
        message.getStringProperty("breadcrumbId"),
        message.getIntProperty("SplitSize")
    };
}


void Messenger::Rollback() {
    session_->rollback();
}

void Messenger::SendResponse(
        const JobContext& job_context,
        const std::vector<unsigned char>& response_bytes) {
    std::unique_ptr<cms::BytesMessage> message{session_->createBytesMessage()};
    const auto &amq_meta = job_context.amq_metadata;
    message->setStringProperty("CorrelationId", amq_meta.correlation_id);
    message->setLongProperty("JobId", job_context.job_id);
    message->setStringProperty("breadcrumbId", amq_meta.bread_crumb_id);
    message->setIntProperty("SplitSize", amq_meta.split_size);
    message->writeBytes(response_bytes);

    response_producer_->send(
            amq_meta.response_queue.get(),
            message.get(),
            cms::Message::DEFAULT_DELIVERY_MODE,
            amq_meta.cms_priority,
            cms::Message::DEFAULT_TIME_TO_LIVE);
    session_->commit();
    logger_.Info("Job response comitted.");
}


std::optional<std::string> Messenger::GetMediaTypeSelector() {
    auto selector = BatchExecutorUtil::GetEnv(RESTRICT_MEDIA_TYPES_ENV_NAME);
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


std::unique_ptr<cms::Connection> Messenger::CreateConnection(
        const LoggerWrapper& logger, std::string_view broker_uri)  {
    activemq::library::ActiveMQCPP::initializeLibrary();
    logger.Info("Connecting to ActiveMQ broker at: ", broker_uri);
    activemq::core::ActiveMQConnectionFactory connection_factory{std::string{broker_uri}};

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
    return std::unique_ptr<cms::Connection>{connection_factory.createConnection()};
}


std::unique_ptr<cms::MessageConsumer> Messenger::CreateRequestConsumer(
        const LoggerWrapper& logger,
        cms::Session& session,
        std::string_view request_queue_name) {
    std::unique_ptr<cms::Destination> request_queue{
        session.createQueue(std::string{request_queue_name})};

    if (auto media_type_selector = GetMediaTypeSelector()) {
        logger.Info("Creating ActiveMQ consumer for queue ", request_queue_name,
                        " with selector: ", *media_type_selector);
        return std::unique_ptr<cms::MessageConsumer>{
                session.createConsumer(request_queue.get(), *media_type_selector)};
    }
    else {
        logger.Info("Creating ActiveMQ consumer for queue: ", request_queue_name);
        return std::unique_ptr<cms::MessageConsumer>{
                session.createConsumer(request_queue.get())};
    }
}

}
