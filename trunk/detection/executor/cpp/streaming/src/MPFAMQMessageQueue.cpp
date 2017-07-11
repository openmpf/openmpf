/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

#include "MPFAMQMessageQueue.h"

using namespace MPF;
 

MPFMessageQueueError MPFAMQMessageQueue::Startup(const std::string &broker_name) {

    try {
        // This call will generate a runtime error if it fails
        ActiveMQCPP::initializeLibrary();
        connection_ = new ActiveMQConnectionFactory(broker_name);
    } catch (CMSException& e) {
        LOG4CXX_ERROR(main_logger_, "CMSException in MPFAMQMessageQueue Startup: " << e.getMessage() << "\n" << e.getStackTraceString());
        throw;
    } catch (std::exception& e) {
        // When thrown, this will be caught and logged by the main program
    } catch (...) {
        LOG4CXX_ERROR(main_logger_, "Unknown Exception occurred in MPFMessenger::Startup");
        throw;
    }

}

MPFMessageQueueError MPFAMQMessageQueue::Connect(const std::string &queue_name) {}

MPFMessageQueueError MPFAMQMessageQueue::Disconnect() {}

MPFMessageQueueError MPFAMQMessageQueue::Shutdown() {}

MPFMessageQueueError MPFAMQMessageQueue::SendMessage(MPFMessage *msg, Properties &msg_properties) {}

MPFMessageQueueError MPFAMQMessageQueue::ReceiveMessage(MPFMessage *msg, Properties &msg_properties) {}

MPFMessageQueueError MPFAMQMessageQueue::TryReceiveMessage(MPFMessage *msg, Properties &msg_properties) {}
