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
#include <iostream>
#include <memory>

#include "BasicMessenger.h"
#include "ComponentLoadError.h"
#include "ComponentRegistration.h"
#include "ExecutorConfig.h"
#include "JobReceiver.h"
#include "LoggerWrapper.h"
#include "PythonComponent.h"
#include "PythonLogger.h"
#include "QuitReceived.h"
#include "subject.pb.h"

#include "ComponentExecutor.h"

namespace MPF::SUBJECT::ComponentExecutor {

namespace {
    LoggerWrapper GetLogger(const ExecutorConfig& config);
    int RunJobsWithLogging(const LoggerWrapper& logger, const ExecutorConfig& config);
}


int RunJobs(int argc, char* argv[]) {
    try {
        auto config = MPF::SUBJECT::GetConfig(argc, argv);
        auto logger = GetLogger(config);
        return RunJobsWithLogging(logger, config);
    }
    catch (const std::exception& e) {
        std::cerr << "An exception occurred before logging could be configured: "
                  << e.what() << '\n';
        return 1;
    }
}

namespace {
    JobReceiver InitJobReceiver(const LoggerWrapper& logger, const ExecutorConfig& config);

    template <typename TComponent>
    void RunJobsWithComponent(
            const LoggerWrapper& logger,
            JobReceiver& job_receiver,
            TComponent& component);


    LoggerWrapper GetLogger(const ExecutorConfig& config) {
        if (config.is_python) {
            return {
                config.log_level,
                std::make_unique<MPF::PythonLogger>(config.log_level, "org.mitre.mpf.subject")
            };
        }
        else {
            throw std::runtime_error{"Only Python components are currently supported."};
        }
    }

    int RunJobsWithLogging(const LoggerWrapper& logger, const ExecutorConfig& config) {
        try {
            auto job_receiver = InitJobReceiver(logger, config);
            if (config.is_python) {
                PythonComponent python_component{logger, config.lib};
                RunJobsWithComponent(logger, job_receiver, python_component);
                return 0;
            }
            else {
                throw std::runtime_error{"Only Python components are currently supported."};
            }
        }
        catch (const QuitReceived&) {
            logger.Info("Received shutdown request.");
            return 0;
        }
        catch (const AmqConnectionInitializationException& e) {
            logger.Fatal("Failed to connect to ActiveMQ broker due to: ", e.what());
            return 37;
        }
        catch (const ComponentRegistrationError& e) {
            logger.Fatal("An error occurred while trying to register component: ", e.what());
            return 38;
        }
        catch (const ComponentLoadError& e) {
            logger.Fatal("An error occurred while trying to load component: ", e.what());
            return 39;
        }
        catch (const std::exception& e) {
            logger.Fatal("A fatal error occurred: ", e.what());
            return 1;
        }
    }

    JobReceiver InitJobReceiver(const LoggerWrapper& logger, const ExecutorConfig& config) {
        BasicMessenger messenger{logger, config.amq_uri, config.job_queue};
        ComponentRegistration::Register(logger, config.descriptor_string, messenger);
        return { logger, std::move(messenger) };
    }

    int GetNumEntities(const mpf_buffers::SubjectTrackingResult& job_results) {
        int count = 0;
        for (const auto& [k, v] : job_results.entity_groups()) {
            count += v.entities_size();
        }
        return count;
    }

    template <typename TComponent>
    void RunJobsWithComponent(
            const LoggerWrapper& logger,
            JobReceiver& job_receiver,
            TComponent& component) {
        logger.Info("Component loaded successfully.");
        while (true) {
            logger.Info("Waiting for next job.");
            auto job_context = job_receiver.GetJob();

            logger.Info("Received job named: ", job_context.job.job_name());
            auto results = component.GetSubjects(job_context.job);

            logger.Info("Component found ", GetNumEntities(results), " entities.");
            job_receiver.CompleteJob(job_context, results);
            logger.Info("Sent job response.");
        }
    }
} // namespace
} // namespace MPF::SUBJECT::ComponentExecutor
