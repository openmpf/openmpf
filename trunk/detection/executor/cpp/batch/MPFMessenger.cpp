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

#include <memory>
#include "MPFMessenger.h"

using std::string;
using std::auto_ptr;
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



//-----------------------------------------------------------------------------
MPFMessenger::MPFMessenger(const log4cxx::LoggerPtr &logger) :
    initialized(false),
    connection_factory_(NULL),
    connection_(NULL),
    session_(NULL),
    request_destination_(NULL),
    request_consumer_(NULL),
    response_producer_(NULL),
    main_logger_(logger) {}

//-----------------------------------------------------------------------------
MPFMessenger::~MPFMessenger() {
    /* Shutdown is called in the destructor so that resources can be
       released in the event of abnormal termination. */
    try {
        Shutdown();
    } catch (CMSException& e) {
        /* Swallow any exceptions here, but print a stacktrace
         just in case anyone is looking. */
        e.printStackTrace();
    }
    catch (std::exception& e) {
        std::cerr << "exception caught in MPFMessenger destructor"
                  << e.what() << '\n';
    }
}

//-----------------------------------------------------------------------------
void MPFMessenger::Startup(
        const string& broker_uri, const string& request_queue) {

    try {
        // This call will generate a runtime error if it fails
        ActiveMQCPP::initializeLibrary();

        // Create an ActiveMQ ConnectionFactory
        connection_factory_ =
                new ActiveMQConnectionFactory(broker_uri);

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
        connection_ = connection_factory_->createConnection();

        // Create an ActiveMQ session
        session_ = connection_->createSession(Session::SESSION_TRANSACTED);

        // Create an ActiveMQ destination referring to the request queue
        request_destination_ = session_->createQueue(request_queue);

        // Create an ActiveMQ MessageConsumer for requests
        request_consumer_ = session_->createConsumer(request_destination_);
        connection_->start();
    } catch (InvalidDestinationException& e) {
        LOG4CXX_ERROR(main_logger_, "InvalidDestinationException in MPFMessenger::Startup: " << e.getMessage() << "\n" << e.getStackTraceString());
        throw;
    } catch (CMSException& e) {
        LOG4CXX_ERROR(main_logger_, "CMSException in MPFMessenger::Startup: " << e.getMessage() << "\n" << e.getStackTraceString());
        throw;
    } catch (std::exception& e) {
        // When thrown, this will be caught and logged by the main program
    } catch (...) {
        LOG4CXX_ERROR(main_logger_, "Unknown Exception occurred in MPFMessenger::Startup");
        throw;
    }
    initialized = true;
}


//-----------------------------------------------------------------------------
unsigned char* MPFMessenger::ReceiveMessage(
        MPFMessageMetadata* msg_metadata, int* msg_body_length) {
    // Caller has to delete returned buffer

    unsigned char* request_contents = NULL;

    try {
        // Pull a message off of the request queue
        const BytesMessage* request_bytes = NULL;
        // The call to receiveNoWait()  does not block
        auto_ptr<Message> request_message(request_consumer_->receiveNoWait());
        request_bytes = static_cast<const BytesMessage*>(request_message.get());

        // Parse and handle header info
        if (request_bytes != NULL) {
            // Create a producer to send response to reply-to queue
            response_producer_ = session_->createProducer(
                        request_bytes->getCMSReplyTo()->clone());
            // Capture header properties needed for response message
            msg_metadata->correlation_id = "";
            msg_metadata->job_id = 0;

            msg_metadata->bread_crumb_id = "";
            msg_metadata->split_size = 0;

            try {
                msg_metadata->correlation_id = request_bytes->getStringProperty("CorrelationId");
                msg_metadata->job_id = request_bytes->getLongProperty("JobId");
                msg_metadata->bread_crumb_id = request_bytes->getStringProperty("breadcrumbId");
                msg_metadata->split_size = request_bytes->getIntProperty("SplitSize");
            } catch (MessageFormatException& e) {
                LOG4CXX_ERROR(main_logger_, "MessageFormatException in MPFMessenger::ReceiveMessage: " << e.getMessage() << "\n"
                              << e.getStackTraceString());
            } catch (CMSException& e) {
                LOG4CXX_ERROR(main_logger_, "CMSException in MPFMessenger::ReceiveMessage: " << e.getMessage() << "\n" << e.getStackTraceString());
            }
            LOG4CXX_DEBUG(main_logger_, "Received request message with CorrelationId: " << msg_metadata->correlation_id);
            LOG4CXX_DEBUG(main_logger_, "SplitSize: " << msg_metadata->split_size);
            LOG4CXX_DEBUG(main_logger_, "JobId: " << msg_metadata->job_id);
            LOG4CXX_DEBUG(main_logger_, "breadcrumbId: " << msg_metadata->bread_crumb_id);
            // Get message body
            request_contents = new unsigned char[request_bytes->getBodyLength()];
            request_bytes->readBytes(
                        request_contents, request_bytes->getBodyLength());
            *msg_body_length = request_bytes->getBodyLength();
        }
    } catch (CMSException& e) {
        if (response_producer_ != NULL) {
            response_producer_->close();
            delete response_producer_;
            response_producer_ = NULL;
        }
        if (request_contents) {
            delete[] request_contents;
            request_contents = NULL;
        }
        LOG4CXX_ERROR(main_logger_, "CMSException in MPFMessenger::ReceiveMessage: " << e.getMessage() << "\n" << e.getStackTraceString());
        throw;
    } catch (std::exception& e) {
        // When thrown, this will be caught and logged by the main program
        if (response_producer_ != NULL) {
            response_producer_->close();
            delete response_producer_;
            response_producer_ = NULL;
        }
        if (request_contents) delete[] request_contents;
        throw;
    } catch (...) {
        if (response_producer_ != NULL) {
            response_producer_->close();
            delete response_producer_;
            response_producer_ = NULL;
        }
        if (request_contents) delete[] request_contents;
        LOG4CXX_ERROR(main_logger_, "Unknown Exception occurred in MPFMessenger::ReceiveMessage");
        throw;
    }
    return request_contents;
}

//-----------------------------------------------------------------------------
void MPFMessenger::SendMessage(const unsigned char* const packed_msg, const MPFMessageMetadata* const msg_metadata, const int packed_length,
                                   const std::string job_name) {

    BytesMessage* response_bytes = NULL;

    try {
        // Put response in outgoing BytesMessage
        response_bytes = session_->createBytesMessage();
        try {
            response_bytes->setStringProperty("CorrelationId", msg_metadata->correlation_id);
            response_bytes->setLongProperty("JobId", msg_metadata->job_id);
            response_bytes->setStringProperty("breadcrumbId", msg_metadata->bread_crumb_id);
            response_bytes->setIntProperty("SplitSize", msg_metadata->split_size);
        } catch (MessageNotWriteableException& e) {
            LOG4CXX_ERROR(main_logger_, "[" << job_name << "] MessageNotWriteableException: " << e.getMessage());
        } catch (CMSException& e) {
            LOG4CXX_ERROR(main_logger_, "[" << job_name << "] CMSException: " << e.getMessage());
        }
        response_bytes->setBodyBytes(packed_msg, packed_length);
        response_producer_->send(response_bytes);
        session_->commit();
        LOG4CXX_DEBUG(main_logger_, "[" << job_name << "] Sent response message for CamelCorrelationId: " << msg_metadata->correlation_id);
        LOG4CXX_DEBUG(main_logger_, "[" << job_name << "] SplitSize " << msg_metadata->split_size);
        LOG4CXX_DEBUG(main_logger_, "[" << job_name << "] JobId " << msg_metadata->job_id);
        LOG4CXX_DEBUG(main_logger_, "[" << job_name << "] BreadcrumbId: " << msg_metadata->bread_crumb_id);

        delete response_bytes;
        response_producer_->close();
        delete response_producer_;
        response_producer_ = NULL;
    } catch (cms::IllegalStateException& e) {  // Only applies to session->commit() call
        delete response_bytes;
        if (response_producer_ != NULL) {
            response_producer_->close();
            delete response_producer_;
            response_producer_ = NULL;
        }
        LOG4CXX_ERROR(main_logger_, "[" << job_name << "] IllegalStateException: " << e.getMessage());
        throw;
    } catch (CMSException& e) {
        delete response_bytes;
        if (response_producer_ != NULL) {
            response_producer_->close();
            delete response_producer_;
            response_producer_ = NULL;
        }
        LOG4CXX_ERROR(main_logger_,  "[" << job_name << "] CMSException:" << e.getMessage());
        throw;
    } catch (std::exception& e) {
        // When thrown, this will be caught and logged by the main program
        delete response_bytes;
        if (response_producer_ != NULL) {
            response_producer_->close();
            delete response_producer_;
            response_producer_ = NULL;
        }
        throw;
    } catch (...) {
        delete response_bytes;
        if (response_producer_ != NULL) {
            response_producer_->close();
            delete response_producer_;
            response_producer_ = NULL;
        }
        LOG4CXX_ERROR(main_logger_, "[" << job_name << "] Unknown Exception occurred");
        throw;
    }

}

//-----------------------------------------------------------------------------
void MPFMessenger::Shutdown() {
    if (initialized) {
        /* Calling close() on the connection object also closes
         any sessions created from it, and their consumers and
         producers as well.  No need to call close() on those
         objects separately. */
        if (connection_ != NULL) {
            connection_->close();
            delete connection_;
            connection_ = NULL;
        }
        if (response_producer_ != NULL) {
            delete response_producer_;
            response_producer_ = NULL;
        }

        if (request_destination_ != NULL) {
            delete request_destination_;
            request_destination_ = NULL;
        }

        if (request_consumer_ != NULL) {
            delete request_consumer_;
            request_consumer_ = NULL;
        }

        if (session_ != NULL) {
            delete session_;
            session_ = NULL;
        }

        if (connection_factory_ != NULL) {
            delete connection_factory_;
            connection_factory_ = NULL;
        }

        // Shut down ActiveMQ library
        ActiveMQCPP::shutdownLibrary();
    }
    initialized = false;
}

