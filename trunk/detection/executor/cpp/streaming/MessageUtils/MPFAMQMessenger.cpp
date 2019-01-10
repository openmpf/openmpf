/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

#include <cassert>

#include <activemq/library/ActiveMQCPP.h>
#include <activemq/core/ActiveMQConnectionFactory.h>

#include "MPFAMQMessenger.h"

using namespace std;
using namespace cms;
using activemq::library::ActiveMQCPP;
using activemq::core::ActiveMQConnectionFactory;
using namespace MPF;
using namespace COMPONENT;

#undef NDEBUG
//TODO: For future use.

void AMQMessagingManager::Connect(const string &broker_name,
                                  const Properties &properties) {

    if (!connected_) {
        try {
            // This call will generate a runtime error if it fails
            ActiveMQCPP::initializeLibrary();

            // Create an ActiveMQ ConnectionFactory
            unique_ptr<ActiveMQConnectionFactory> factory(new ActiveMQConnectionFactory(broker_name));

            // Create an ActiveMQ Connection
            connection_.reset(factory->createConnection());
            assert(NULL != connection_.get());
            if (!properties.empty()) {
                // At this point, we should apply any properties that may
                // have been passed in, to modify how the connection
                // operates.
                LOG4CXX_WARN(logger_, __FUNCTION__ << ": Note: Connection properties not yet used, but the properties map was non-empty.");
            }
            connected_ = true;

        } catch (CMSException& e) {
            string msg = "CMSException caught in AMQMessagingManager::Connect: " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MPFMessageError::MESSENGER_CONNECTION_FAILURE;
            MPFMessageException exc(msg.c_str(), err);
            throw(exc);
        } catch (std::exception& e) {
            string msg = "std::exception caught in AMQMessagingManager::Connect: " + string(e.what());
            MPFMessageError err = MPFMessageError::MESSENGER_CONNECTION_FAILURE;
            MPFMessageException exc(msg.c_str(), err);
            throw(exc);
        }
    }
}


void AMQMessagingManager::Start() {

    if (connected_ && !started_) {
        assert(NULL != connection_.get());
        try {
            connection_->start();
            started_ = true;
        } catch (CMSException& e) {
            string msg = "CMSException caught in AMQMessagingManager::Start: " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MESSENGER_START_FAILURE;
            MPFMessageException exc(msg.c_str(), err);
            throw(exc);
        } catch (std::exception& e) {
            string msg = "std::exception caught in AMQMessagingManager::Start: " + string(e.what());
            MPFMessageError err = MPFMessageError::MESSENGER_START_FAILURE;
            MPFMessageException exc(msg.c_str(), err);
            throw(exc);
        }
    }
}


void AMQMessagingManager::Stop() {

    if (connected_ && started_) {
        try {
            connection_->stop();
            started_ = false;
        } catch (CMSException& e) {
            string msg = "CMSException caught in AMQMessagingManager::Stop: " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MESSENGER_STOP_FAILURE;
            MPFMessageException exc(msg.c_str(), err);
            throw(exc);
        }
    }
}


void AMQMessagingManager::Shutdown() {
    if (connected_) {
        try {
            if (started_) {
                Stop();
            }
            if (connection_ != NULL) {
                connection_->close();
                connection_.release();
            }
            connected_ = false;
            // Shut down ActiveMQ library
            ActiveMQCPP::shutdownLibrary();
        } catch (CMSException& e) {
            string msg = "CMSException caught in AMQMessagingManager::Shutdown: " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MESSENGER_START_FAILURE;
            MPFMessageException exc(msg.c_str(), err);
            throw(exc);
        }
    }
}



