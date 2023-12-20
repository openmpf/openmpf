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
#include <iostream>
#include <memory>
#include <filesystem>
#include <string_view>
#include <cstring>
#include <cstdlib>

#include "ComponentLoadError.h"
#include "CppComponentHandle.h"
#include "LazyLoggerWrapper.h"
#include "PythonComponentHandle.h"
#include "JobReceiver.h"
#include "Messenger.h"

namespace fs = std::filesystem;
using namespace MPF::COMPONENT;

std::string get_app_dir(const char * const argv0);
std::string get_component_name_and_set_env_var();
std::string get_log_level_and_set_env_var();
bool is_python(int argc, const char * argv[]);


using logger_variant_t = std::variant<std::monostate,
        LazyLoggerWrapper<PythonLogger>, LazyLoggerWrapper<CppLogger>>;

void log_top_level_exception(
        logger_variant_t& logger_variant, std::string_view description, std::string_view what);

template <typename Logger, typename ComponentHandle>
int run_jobs(Logger& logger, std::string_view broker_uri, std::string_view request_queue,
             std::string_view app_dir, ComponentHandle& detection_engine);

template <typename Logger, typename ComponentHandle, typename JobType>
auto run_job(
        Logger& logger, ComponentHandle& detection_engine, const JobType& job,
        std::string_view service_name, std::string_view job_type_name,
        std::string_view results_name);


int main(int argc, const char* argv[]) {
    if (argc < 4) {
        std::cerr << "ERROR: Too few arguments.\nUsage: " << argv[0]
                  << " <broker-uri> <library-path> <request-queue> <language>\n";
        return 1;
    }

    logger_variant_t logger_variant;
    try {
        auto app_dir = get_app_dir(argv[0]);
        auto broker_uri = argv[1];
        auto lib_path = argv[2];
        auto request_queue = argv[3];

        auto component_name = get_component_name_and_set_env_var();
        auto log_level = get_log_level_and_set_env_var();

        if (is_python(argc, argv)) {
            auto& logger = logger_variant.emplace<1>(log_level, log_level, component_name);
            PythonComponentHandle component_handle{logger, lib_path};
            return run_jobs(logger, broker_uri, request_queue, app_dir, component_handle);
        }
        else {
            auto& logger = logger_variant.emplace<2>(log_level, app_dir);
            CppComponentHandle component_handle{lib_path};
            return run_jobs(logger, broker_uri, request_queue, app_dir, component_handle);
        }
    }
    catch (const AmqConnectionInitializationException& e) {
        log_top_level_exception(
            logger_variant, "Failed to connect to ActiveMQ broker due to: ", e.what());
        return 37;
    }
    catch (const ComponentLoadError& e) {
        log_top_level_exception(
            logger_variant, "An error occurred while trying to load component: ", e.what());
        return 38;
    }
    catch (const std::exception& e) {
        log_top_level_exception(
            logger_variant, "A fatal error occurred: ", e.what());
        return 1;
    }
}

void log_top_level_exception(
        logger_variant_t& logger_variant, std::string_view description, std::string_view what) {
    return std::visit([description, what](auto& logger) {
        if constexpr (std::is_same_v<std::decay_t<decltype(logger)>, std::monostate>) {
            // The error occurred before logging was initialized.
            std::cerr << description << what << '\n';
        }
        else {
            logger.Error(description, what);
        }
    }, logger_variant);
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
    if (auto component_name = std::getenv("COMPONENT_NAME");
            component_name != nullptr && std::strlen(component_name) > 0) {
        return component_name;
    }
    std::string component_name = "detection";
    // Need to make sure COMPONENT_NAME is set because it is used to determine the name of the log file.
    setenv("COMPONENT_NAME", component_name.c_str(), 1);

    std::cerr << "Expected the COMPONENT_NAME environment variable to be set to the "
                 "component's name, but it was empty. Using " << component_name << " instead.\n";
    return component_name;
}

std::string get_log_level_and_set_env_var() {
    auto env_level = std::getenv("LOG_LEVEL");
    if (env_level == nullptr || std::strlen(env_level) == 0) {
        std::string level_name = "INFO";
        setenv("LOG_LEVEL", level_name.c_str(), 1);
        return level_name;
    }

    std::string level_name = env_level;
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
        std::cerr << "The LOG_LEVEL environment variable is set to " << env_level
                  << " but that isn't a valid log level. Using " << level_name << " instead.\n";

        setenv("LOG_LEVEL", level_name.c_str(), 1);
        return level_name;
    }
}

bool is_python(int argc, const char * argv[]) {
    if (argc > 4) {
        std::string provided_language = argv[4];
        std::transform(provided_language.begin(), provided_language.end(),
                       provided_language.begin(),
                       [](auto c) { return std::tolower(c); });
        if ("python" == provided_language) {
            return true;
        }
        if ("c++" == provided_language) {
            return false;
        }
        std::cerr << R"(Expected the fifth command line argument to either be "c++" or "python", but ")"
                  << argv[4] << "\" was provided.\n";
    }
    else {
        std::cerr << R"(Expected the fifth command line argument to either be "c++" or "python", )"
                     "but no value was provided.\n";
    }

    std::string lib_extension = fs::path(argv[2]).extension();
    std::transform(lib_extension.begin(), lib_extension.end(), lib_extension.begin(),
                   [](auto c) { return std::tolower(c); });
    if (".so" == lib_extension) {
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
    if (auto service_name = std::getenv("SERVICE_NAME");
            service_name != nullptr && std::strlen(service_name) > 0) {
        return service_name;
    }
    else {
        return "UNKNOWN_SERVICE";
    }
}


template <typename Logger, typename ComponentHandle>
int run_jobs(Logger& logger, std::string_view broker_uri, std::string_view request_queue,
              std::string_view app_dir, ComponentHandle& detection_engine) {
    detection_engine.SetRunDirectory(std::string{app_dir} + "/../plugins");
    if (!detection_engine.Init()) {
        logger.Error("Detection component initialization failed, exiting.");
        return 1;
    }

    JobReceiver<Logger> job_receiver{
            logger, broker_uri, request_queue, detection_engine.GetDetectionType()};

    auto service_name = get_service_name();
    logger.Info("Completed initialization of ", service_name, '.');

    while (true) {
        logger.Info("Waiting for next job.");
        auto job_context = job_receiver.GetJob();
        auto job_type = job_context.job_type;
        if (!detection_engine.Supports(job_type)) {
            job_receiver.ReportUnsupportedDataType(job_context);
            continue;
        }
        try {
            switch (job_type) {
                case MPFDetectionDataType::VIDEO: {
                    auto results = run_job(
                            logger, detection_engine, job_context.GetVideoJob(),
                            service_name, "video", "tracks");
                    job_receiver.CompleteJob(job_context, results);
                    break;
                }
                case MPFDetectionDataType::IMAGE: {
                    auto results = run_job(
                            logger, detection_engine, job_context.GetImageJob(),
                            service_name, "image", "detections");
                    job_receiver.CompleteJob(job_context, results);
                    break;
                }
                case MPFDetectionDataType::AUDIO: {
                    auto results = run_job(
                            logger, detection_engine, job_context.GetAudioJob(),
                            service_name, "audio", "tracks");
                    job_receiver.CompleteJob(job_context, results);
                    break;
                }
                default: {
                    auto results = run_job(
                            logger, detection_engine, job_context.GetGenericJob(),
                            service_name, "generic", "tracks");
                    job_receiver.CompleteJob(job_context, results);
                    break;
                }
            }
        }
        catch (const MPFDetectionException& e) {
            job_receiver.ReportJobError(job_context, e.error_code, e.what());
        }
        catch (const std::exception& e) {
            job_receiver.ReportJobError(job_context, MPF_OTHER_DETECTION_ERROR_TYPE, e.what());
        }
    }
}


template <typename Logger, typename ComponentHandle, typename JobType>
auto run_job(
        Logger& logger, ComponentHandle& detection_engine, const JobType& job,
        std::string_view service_name, std::string_view job_type_name,
        std::string_view results_name) {
    logger.Info("Processing ", job_type_name, " on ", service_name);
    auto results = detection_engine.GetDetections(job);
    logger.Info("Component found ", results.size(), ' ', results_name);
    return results;
}
