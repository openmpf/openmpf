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

#ifndef MPF_MESSAGING_CONNECTION_H_
#define MPF_MESSAGING_CONNECTION_H_

#include <memory>

#include <cms/Connection.h>
#include <cms/Session.h>

#include "JobSettings.h"

namespace MPF {

class MPFMessagingConnection {
  public:

    explicit MPFMessagingConnection(const MPF::COMPONENT::JobSettings &job_settings);
    
    ~MPFMessagingConnection();

    std::shared_ptr<cms::Connection> GetConnection() {
        return connection_;
    }

    std::shared_ptr<cms::Session> GetSession() {
        return session_;
    }

  private:

    std::shared_ptr<cms::Connection> connection_;
    std::shared_ptr<cms::Session> session_;
    std::shared_ptr<cms::Connection> Connect(const std::string &broker_uri);
};



} // namespace MPF

#endif // MPF_AMQ_MESSENGER_H_
