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

#include <algorithm>
#include <cctype>
#include <exception>
#include <filesystem>
#include <iostream>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <variant>

#include <MPFDetectionException.h>
#include <MPFDetectionObjects.h>

#include "BatchExecutorUtil.h"
#include "ComponentLoadError.h"
#include "CppComponentHandle.h"
#include "HealthCheck.h"
#include "JobReceiver.h"
#include "LoggerWrapper.h"
#include "Messenger.h"
#include "PythonComponentHandle.h"

namespace fs = std::filesystem;
using namespace MPF::COMPONENT;


std::string get_app_dir(const char * const argv0);
std::string get_component_name_and_set_env_var();
std::string get_log_level_and_set_env_var();
bool is_python(int argc, const char * argv[]);

template <typename ComponentHandle>
int run_jobs(LoggerWrapper& logger, std::string_view broker_uri, std::string_view request_queue,
             std::string_view app_dir, ComponentHandle& component);


int main(int argc, const char* argv[]) {
    if (argc < 4) {
        std::cerr << "ERROR: Too few arguments.\nUsage: " << argv[0]
                  << " <broker-uri> <library-path> <request-queue> <language>\n";
        return 1;
    }

    auto app_dir = get_app_dir(argv[0]);
    auto broker_uri = argv[1];
    auto lib_path = argv[2];
    auto request_queue = argv[3];

    std::optional<LoggerWrapper> logger;
    bool is_python_component = false;
    try {
        auto component_name = get_component_name_and_set_env_var();
        auto log_level = get_log_level_and_set_env_var();
        is_python_component = is_python(argc, argv);
        if (is_python_component) {
            logger.emplace(log_level, std::make_unique<PythonLogger>(log_level, component_name));
        }
        else {
            logger.emplace(log_level, std::make_unique<CppLogger>(app_dir));
        }
    }
    catch (const std::exception& e) {
        std::cerr << "An exception occurred before logging could be configured: "
                  << e.what() << '\n';
        return 1;
    }

    try {
        if (is_python_component) {
            PythonComponentHandle component_handle{*logger, lib_path};
            return run_jobs(*logger, broker_uri, request_queue, app_dir, component_handle);
        }
        else {
            CppComponentHandle component_handle{lib_path};
            return run_jobs(*logger, broker_uri, request_queue, app_dir, component_handle);
        }
    }
    catch (const AmqConnectionInitializationException& e) {
        logger->Fatal("Failed to connect to ActiveMQ broker due to: ", e.what());
        return 37;
    }
    catch (const ComponentLoadError& e) {
        logger->Fatal("An error occurred while trying to load component: ", e.what());
        return 38;
    }
    catch (const FailedHealthCheck& e) {
        logger->Fatal("Exiting because the component failed too many health checks. ", e.what());
        return 39;
    }
    catch (const std::exception& e) {
        logger->Fatal("A fatal error occurred: ", e.what());
        return 1;
    }
}


std::string get_app_dir(const char * const argv0) {
    try {
        return fs::canonical("/proc/self/exe").parent_path();
    }
    catch (const fs::filesystem_error&) {
        fs::path argv0_path(argv0);
        if (auto argv0_parent = argv0_path.parent_path(); argv0_parent != argv0_path) {
            return argv0_parent;
        }
    }
    try {
        return fs::current_path();
    }
    catch (const fs::filesystem_error&) {
        return ".";
    }
}

std::string get_component_name_and_set_env_var() {
    if (auto component_name = BatchExecutorUtil::GetEnv("COMPONENT_NAME")) {
        return *component_name;
    }
    std::string component_name = "detection";
    // Need to make sure COMPONENT_NAME is set because it is used to determine the name of the log file.
    setenv("COMPONENT_NAME", component_name.c_str(), 1);

    std::cerr << "Expected the COMPONENT_NAME environment variable to be set to the "
                 "component's name, but it was empty. Using " << component_name << " instead.\n";
    return component_name;
}

std::string get_log_level_and_set_env_var() {
    auto env_level = BatchExecutorUtil::GetEnv("LOG_LEVEL");
    if (!env_level) {
        std::string level_name = "INFO";
        setenv("LOG_LEVEL", level_name.c_str(), 1);
        return level_name;
    }

    std::string level_name = *env_level;
    std::transform(level_name.begin(), level_name.end(), level_name.begin(),
                   [](auto c) { return std::toupper(c); });

    if (level_name == "WARNING") {
        // Python logging accepts either WARNING or WARN, but Log4CXX requires it be WARN.
        setenv("LOG_LEVEL", "WARN", 1);
        return "WARN";
    }
    else if (level_name == "CRITICAL") {
        // Python logging accepts either CRITICAL or FATAL, but Log4CXX requires it be FATAL.
        setenv("LOG_LEVEL", "FATAL", 1);
        return "FATAL";
    }
    else if (level_name == "TRACE" || level_name == "DEBUG" || level_name == "INFO"
            || level_name == "WARN" || level_name == "ERROR" || level_name == "FATAL") {
        return level_name;
    }
    else {
        level_name = "DEBUG";
        std::cerr << "The LOG_LEVEL environment variable is set to " << *env_level
                  << " but that isn't a valid log level. Using " << level_name << " instead.\n";

        setenv("LOG_LEVEL", level_name.c_str(), 1);
        return level_name;
    }
}

bool is_python(int argc, const char * argv[]) {
    if (argc > 4) {
        auto provided_language = argv[4];
        if (BatchExecutorUtil::EqualsIgnoreCase("python", provided_language)) {
            return true;
        }
        if (BatchExecutorUtil::EqualsIgnoreCase("c++", provided_language)) {
            return false;
        }
        std::cerr << R"(Expected the fifth command line argument to either be "c++" or "python", but ")"
                  << provided_language << "\" was provided.\n";
    }
    else {
        std::cerr << R"(Expected the fifth command line argument to either be "c++" or "python", )"
                     "but no value was provided.\n";
    }

    std::string lib_extension = fs::path(argv[2]).extension();
    if (BatchExecutorUtil::EqualsIgnoreCase(".so", lib_extension)) {
        std::cerr << "Assuming \"" << argv[2]
                  << "\" is a C++ component because it has the .so extension.\n";
        return false;
    }
    else {
        std::cerr << "Assuming \"" << argv[2]
                  << "\" is a Python component because it does not have the .so extension.\n";
        return true;
    }
}

std::string get_service_name() {
    return BatchExecutorUtil::GetEnv("SERVICE_NAME")
        .value_or(BatchExecutorUtil::GetEnv("COMPONENT_NAME")
        .value_or("UNKNOWN_SERVICE"));
}


template <typename ComponentHandle>
int run_jobs(LoggerWrapper& logger, std::string_view broker_uri, std::string_view request_queue,
             std::string_view app_dir, ComponentHandle& component) {
    component.SetRunDirectory(std::string{app_dir} + "/../plugins");
    if (!component.Init()) {
        logger.Error("Detection component initialization failed, exiting.");
        return 1;
    }

    JobReceiver job_receiver{logger, broker_uri, request_queue};
    HealthCheck health_check{logger};
    auto service_name = get_service_name();
    logger.Info("Completed initialization of ", service_name, '.');

    while (true) {
        logger.Info("Waiting for next job.");
        auto job_context = job_receiver.GetJob();

        bool can_process_job = health_check.Check(
            component, [&job_receiver] { job_receiver.RejectJob(); });
        if (!can_process_job) {
            continue;
        }

        job_context.OnJobStarted();
        if (!component.Supports(job_context.job_type)) {
            job_receiver.ReportUnsupportedDataType(job_context);
            continue;
        }
        try {
            logger.Info("Processing ", job_context.job_type_name, " job on ", service_name);
            std::visit([&component, &logger, &job_context, &job_receiver](const auto& job) {
                auto results = component.GetDetections(job);
                logger.Info("Component found ", results.size(), " results.");
                job_receiver.CompleteJob(job_context, results);
            }, job_context.job);
        }
        catch (const MPFDetectionException& e) {
            job_receiver.ReportJobError(job_context, e.error_code, e.what());
        }
        catch (const std::exception& e) {
            job_receiver.ReportJobError(job_context, MPF_OTHER_DETECTION_ERROR_TYPE, e.what());
        }
    }
}
