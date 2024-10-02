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

#include <random>
#include <string>

#include "ComponentRegistration.h"


namespace MPF::SUBJECT::ComponentRegistration {
    namespace {
        std::string GetCorrelationId() {
            std::random_device device;
            std::default_random_engine engine{device()};
            std::uniform_int_distribution distribution;
            int random_value = distribution(engine);
            return "SUBJECT_" + std::to_string(random_value);
        }
    }

    void Register(
                const LoggerWrapper& logger,
                const std::string& descriptor,
                BasicMessenger& messenger) {
        logger.Info("Attempting to register component.");
        auto registration_request = messenger.NewMessage<cms::TextMessage>();
        registration_request->setText(descriptor);
        registration_request->setCMSCorrelationID(GetCorrelationId());

        auto response = messenger.SendRequestReply(
                "MPF.SUBJECT_COMPONENT_REGISTRATION", *registration_request);
        if (response->getBooleanProperty("success")) {
            logger.Info(
                "Successfully registered component. Response from server: ",
                response->getStringProperty("detail"));
        }
        else {
            throw ComponentRegistrationError{
                "Registration failed with response: " + response->getStringProperty("detail")};
        }
    }
} // namespace MPF::SUBJECT::ComponentRegistration
