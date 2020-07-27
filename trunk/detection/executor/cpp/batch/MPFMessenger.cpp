/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

#include "MPFMessenger.h"

#include <cstdlib>
#include <memory>
#include <stdexcept>

#include <boost/algorithm/string.hpp>

#include "PythonComponentHandle.h"
#include "CppComponentHandle.h"
#include "LazyLoggerWrapper.h"

using std::string;
using cms::Session;
using cms::Destination;
using cms::Connection;
using cms::ConnectionFactory;
using cms::Message;
using cms::BytesMessage;
using cms::MessageConsumer;
using cms::MessageProducer;
using cms::CMSException;
using cms::IllegalStateException;
using cms::InvalidDestinationException;
using cms::MessageFormatException;
using cms::MessageNotWriteableException;
using activemq::library::ActiveMQCPP;
using activemq::core::ActiveMQConnectionFactory;
using activemq::core::policies::DefaultPrefetchPolicy;
using activemq::core::PrefetchPolicy;

AmqLibraryManager::AmqLibraryManager() {
    ActiveMQCPP::initializeLibrary();
};

AmqLibraryManager::~AmqLibraryManager() {
    ActiveMQCPP::shutdownLibrary();
}

//-----------------------------------------------------------------------------

template <typename Logger>
MPFMessenger<Logger>::MPFMessenger(Logger &logger, const string& broker_uri, const string& request_queue)
        : logger_(logger) {

    try {
        // Create an ActiveMQ ConnectionFactory
        connection_factory_.reset(new ActiveMQConnectionFactory(broker_uri));

        // Check whether a prefetch policy has been set on the broker
        // URI. Explicitly set it to 0 if it was not set on the URI,
        // or if it is set to 0. This second condition works around a
        // bug in ActiveMQ.
        bool hasPrefetch = broker_uri.find("jms.prefetchPolicy.all") != std::string::npos;
        bool hasPrefetchSetToZero = broker_uri.find("jms.prefetchPolicy.all=0") != std::string::npos;
        if (!hasPrefetch || hasPrefetchSetToZero) {
            PrefetchPolicy *policy = new DefaultPrefetchPolicy();
            policy->setQueuePrefetch(0);
            policy->setTopicPrefetch(0);
            connection_factory_->setPrefetchPolicy(policy);
        }

        connection_factory_->setOptimizeAcknowledge(true);

        // Create an ActiveMQ Connection
        connection_.reset(connection_factory_->createConnection());

        // Create an ActiveMQ session
        session_.reset(connection_->createSession(Session::SESSION_TRANSACTED));

        // Create an ActiveMQ destination referring to the request queue
        request_destination_.reset(session_->createQueue(request_queue));

        // Create an ActiveMQ MessageConsumer for requests
        std::string media_type_selector = GetMediaTypeSelector();
        if (media_type_selector.empty()) {
            logger_.Info("Creating ActiveMQ consumer for queue: ", request_queue);
            request_consumer_.reset(session_->createConsumer(request_destination_.get()));
        }
        else {
            logger_.Info("Creating ActiveMQ consumer for queue ", request_queue, " with selector: ",
                         media_type_selector);
            request_consumer_.reset(session_->createConsumer(
                    request_destination_.get(), media_type_selector));
        }

        connection_->start();
    } catch (InvalidDestinationException& e) {
        logger_.Error("InvalidDestinationException in MPFMessenger::Startup: ",
                      e.getMessage(), '\n', e.getStackTraceString());
        throw;
    } catch (CMSException& e) {
        logger_.Error("CMSException in MPFMessenger::Startup: ", e.getMessage(), '\n',
                      e.getStackTraceString());
        throw;
    } catch (std::exception& e) {
        // When thrown, this will be caught and logged by the main program
        throw;
    } catch (...) {
        logger_.Error("Unknown Exception occurred in MPFMessenger::Startup");
        throw;
    }
}


//-----------------------------------------------------------------------------
template <typename Logger>
std::vector<unsigned char> MPFMessenger<Logger>::ReceiveMessage(MPFMessageMetadata& msg_metadata) {
    try {
        // Pull a message off of the request queue
        // The call to receiveNoWait()  does not block
        std::shared_ptr<Message> request_message(request_consumer_->receiveNoWait());
        if (request_message == nullptr) {
            return { };
        }
        auto request_bytes_message = std::dynamic_pointer_cast<BytesMessage>(request_message);

        if (request_bytes_message == nullptr) {
            throw std::runtime_error(
                    "Error: Expected an ActiveMQ BytesMessage, but a different message type was received.");
        }

        // Create a producer to send response to reply-to queue
        response_producer_.reset(session_->createProducer(request_bytes_message->getCMSReplyTo()));
        // Capture header properties needed for response message
        msg_metadata.correlation_id = "";
        msg_metadata.job_id = 0;

        msg_metadata.bread_crumb_id = "";
        msg_metadata.split_size = 0;

        try {
            msg_metadata.correlation_id = request_bytes_message->getStringProperty("CorrelationId");
            msg_metadata.job_id = request_bytes_message->getLongProperty("JobId");
            msg_metadata.bread_crumb_id = request_bytes_message->getStringProperty("breadcrumbId");
            msg_metadata.split_size = request_bytes_message->getIntProperty("SplitSize");
        } catch (MessageFormatException& e) {
            logger_.Error("MessageFormatException in MPFMessenger::ReceiveMessage: ",
                          e.getMessage(), '\n', e.getStackTraceString());
        } catch (CMSException& e) {
            logger_.Error("CMSException in MPFMessenger::ReceiveMessage: ", e.getMessage(), '\n',
                          e.getStackTraceString());
        }
        logger_.Debug("Received request message with CorrelationId: ", msg_metadata.correlation_id);
        logger_.Debug("SplitSize: ", msg_metadata.split_size);
        logger_.Debug("JobId: ", msg_metadata.job_id);
        logger_.Debug("breadcrumbId: ", msg_metadata.bread_crumb_id);
        // Get message body
        std::vector<unsigned char> request_contents(request_bytes_message->getBodyLength());
        request_bytes_message->readBytes(request_contents);
        return request_contents;
    } catch (CMSException& e) {
        response_producer_ = nullptr;
        logger_.Error("CMSException in MPFMessenger::ReceiveMessage: ", e.getMessage(), '\n',
                      e.getStackTraceString());
        throw;
    } catch (std::exception& e) {
        // When thrown, this will be caught and logged by the main program
        response_producer_ = nullptr;
        throw;
    } catch (...) {
        response_producer_ = nullptr;
        logger_.Error("Unknown Exception occurred in MPFMessenger::ReceiveMessage");
        throw;
    }
}

//-----------------------------------------------------------------------------
template <typename Logger>
void MPFMessenger<Logger>::SendMessage(
        const std::vector<unsigned char> &packed_msg, const MPFMessageMetadata &msg_metadata,
        const std::string &job_name) {
    try {
        // Put response in outgoing BytesMessage
        std::unique_ptr<BytesMessage> response_bytes(session_->createBytesMessage());
        try {
            response_bytes->setStringProperty("CorrelationId", msg_metadata.correlation_id);
            response_bytes->setLongProperty("JobId", msg_metadata.job_id);
            response_bytes->setStringProperty("breadcrumbId", msg_metadata.bread_crumb_id);
            response_bytes->setIntProperty("SplitSize", msg_metadata.split_size);
        } catch (MessageNotWriteableException& e) {
            logger_.Error('[', job_name, "] MessageNotWriteableException: ", e.getMessage());
        } catch (CMSException& e) {
            logger_.Error('[', job_name, "] CMSException: ", e.getMessage());
        }
        response_bytes->writeBytes(packed_msg);
        response_producer_->send(response_bytes.get());
        session_->commit();
        logger_.Debug('[', job_name, "] Sent response message for CamelCorrelationId: ",
                      msg_metadata.correlation_id);
        logger_.Debug('[', job_name, "] SplitSize ", msg_metadata.split_size);
        logger_.Debug('[', job_name, "] JobId ", msg_metadata.job_id);
        logger_.Debug('[', job_name, "] BreadcrumbId: ", msg_metadata.bread_crumb_id);

        response_producer_ = nullptr;
    } catch (cms::IllegalStateException& e) {  // Only applies to session->commit() call
        response_producer_ = nullptr;
        logger_.Error('[', job_name, "] IllegalStateException: ", e.getMessage());
        throw;
    } catch (CMSException& e) {
        response_producer_ = nullptr;
        logger_.Error('[', job_name, "] CMSException:", e.getMessage());
        throw;
    } catch (std::exception& e) {
        // When thrown, this will be caught and logged by the main program
        response_producer_ = nullptr;
        throw;
    } catch (...) {
        response_producer_ = nullptr;
        logger_.Error('[', job_name, "] Unknown Exception occurred");
        throw;
    }
}


// Converts "VIDEO, IMAGE" to "MediaType in ('VIDEO', 'IMAGE')"
// Converts "VIDEO" to "MediaType in ('VIDEO')"
template <typename Logger>
std::string MPFMessenger<Logger>::GetMediaTypeSelector() {
    const char * const env_val = std::getenv(MPFMessenger::RESTRICT_MEDIA_TYPES_ENV_NAME);
    if (env_val == nullptr || env_val[0] == '\0') {
        return "";
    }

    std::vector<std::string> tokens;
    boost::split(tokens, env_val, boost::is_any_of(","), boost::algorithm::token_compress_on);

    std::vector<std::string> quoted_tokens;
    for (const std::string &raw_token : tokens) {
        std::string cleaned_token = boost::trim_copy(raw_token);
        boost::to_upper(cleaned_token);

        if (cleaned_token.empty()) {
            continue;
        }
        if (cleaned_token != "VIDEO" && cleaned_token != "IMAGE" && cleaned_token != "AUDIO"
                && cleaned_token != "UNKNOWN") {
            throw std::invalid_argument(
                    "Expected the RESTRICT_MEDIA_TYPES environment variable contain a comma-separated list "
                    "containing one or more of: VIDEO, IMAGE, AUDIO, UNKNOWN");
        }
        quoted_tokens.emplace_back('\'' + cleaned_token + '\'');
    }

    if (quoted_tokens.empty()) {
        return "";
    }

    std::string joined_tokens = boost::join(quoted_tokens, ", ");
    return "MediaType in (" + joined_tokens + ')';
}

// Explicitly instantiate the only two possible template parameters.
template class MPFMessenger<LazyLoggerWrapper<MPF::COMPONENT::PythonLogger>>;
template class MPFMessenger<LazyLoggerWrapper<MPF::COMPONENT::CppLogger>>;