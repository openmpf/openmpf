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

#include <iostream>

#include <activemq/library/ActiveMQCPP.h>
#include <activemq/core/ActiveMQConnectionFactory.h>

#include "MPFMessagingConnection.h"

using namespace std;
using namespace cms;
using activemq::library::ActiveMQCPP;
using activemq::core::ActiveMQConnectionFactory;

using namespace MPF;

MPFMessagingConnection::MPFMessagingConnection(const MPF::COMPONENT::JobSettings &job_settings) 
        : connection_(Connect(job_settings.message_broker_uri)) {

    connection_->start();
}

shared_ptr<cms::Connection> MPFMessagingConnection::Connect(const string &broker_uri) {

    ActiveMQCPP::initializeLibrary();

    unique_ptr<ActiveMQConnectionFactory> factory(new ActiveMQConnectionFactory(broker_uri));
    factory->setUseAsyncSend(true);

    return shared_ptr<cms::Connection>(factory->createConnection());
}

MPFMessagingConnection::~MPFMessagingConnection() {
    try {
        // This call to stop() will prevent any further events from occurring.
        connection_->stop();
        // This call to close() will close any sessions created from
        // this connection, as well as those sessions' consumers and producers.
        connection_->close();
    }
    catch (const CMSException &e) {
        e.printStackTrace();
    }
}





