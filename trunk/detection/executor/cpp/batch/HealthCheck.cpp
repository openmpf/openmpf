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

#include <filesystem>

#include <boost/property_tree/ptree.hpp>
#include <boost/property_tree/ini_parser.hpp>

#include <MPFDetectionObjects.h>

#include "BatchExecutorUtil.h"

#include "HealthCheck.h"

namespace {
    using namespace MPF::COMPONENT;

    Properties LoadProperties(const boost::property_tree::ptree& ini_props) {
        Properties props;
        for (const auto& [key, value] : ini_props) {
            props.emplace(key, value.data());
        }
        return props;
    }

    Properties LoadJobProperties(const boost::property_tree::ptree& ini_settings) {
        return LoadProperties(ini_settings.get_child("job_properties", {}));
    }

    Properties LoadMediaProperties(const boost::property_tree::ptree& ini_settings) {
        return LoadProperties(ini_settings.get_child("media_properties", {}));
    }


    std::string GetMediaPath(const boost::property_tree::ptree& ini_settings) {
        try {
            auto media_path = BatchExecutorUtil::ExpandFileName(
                    ini_settings.get<std::string>("media"));
            if (media_path == "") {
                throw HealthCheckLoadError{"\"media\" key was empty"};
            }
            if (!std::filesystem::exists(media_path)) {
                throw HealthCheckLoadError{std::string{
                    "No media file is present at \""} + media_path + "\"."};
            }
            return media_path;
        }
        catch (const boost::property_tree::ptree_bad_path&) {
            throw HealthCheckLoadError{"\"media\" key was missing"};
        }
        catch (const std::invalid_argument& e) {
            throw HealthCheckLoadError{std::string{
                "The path in the \"media\" key could not be expanded due to: "} + e.what()};
        }
    }


    HealthCheckJob LoadHealthCheckJob(std::optional<std::string_view> optional_path) {
        boost::property_tree::ptree ini_settings;
        try {
            auto path = BatchExecutorUtil::ExpandFileName(
                optional_path.value_or(
                    "${MPF_HOME:-/opt/mpf}/plugins/${COMPONENT_NAME}/health/health-check.ini"));
            boost::property_tree::ini_parser::read_ini(path, ini_settings);
        }
        catch (const boost::property_tree::ini_parser::ini_parser_error& e) {
            throw HealthCheckLoadError{e.what()};
        }

        int min_num_tracks{};
        try {
            min_num_tracks = ini_settings.get<int>("min_num_tracks");
        }
        catch (const boost::property_tree::ptree_bad_data&) {
            throw HealthCheckLoadError{"\"min_num_tracks\" key was not an integer"};
        }
        catch (const boost::property_tree::ptree_bad_path&) {
            throw HealthCheckLoadError{"\"min_num_tracks\" key was missing"};
        }

        std::string media_type;
        try {
            media_type = ini_settings.get<std::string>("media_type");
        }
        catch (const boost::property_tree::ptree_bad_path&) {
            throw HealthCheckLoadError{"\"media_type\" key was missing"};
        }

        if (BatchExecutorUtil::EqualsIgnoreCase(media_type, "VIDEO")) {
            return {
                MPFVideoJob{
                    "HealthCheck", GetMediaPath(ini_settings), 0, -1,
                    LoadJobProperties(ini_settings), LoadMediaProperties(ini_settings)},
                min_num_tracks
            };
        }
        else if (BatchExecutorUtil::EqualsIgnoreCase(media_type, "IMAGE")) {
            return {
                MPFImageJob{
                    "HealthCheck", GetMediaPath(ini_settings),
                    LoadJobProperties(ini_settings), LoadMediaProperties(ini_settings)},
                min_num_tracks
            };
        }
        else if (BatchExecutorUtil::EqualsIgnoreCase(media_type, "AUDIO")) {
            return {
                MPFAudioJob{
                    "HealCheck", GetMediaPath(ini_settings), 0, -1,
                    LoadJobProperties(ini_settings), LoadMediaProperties(ini_settings)
                },
                min_num_tracks
            };
        }
        else if (BatchExecutorUtil::EqualsIgnoreCase(media_type, "GENERIC")) {
            return {
                MPFGenericJob{"HealthCheck", GetMediaPath(ini_settings),
                    LoadJobProperties(ini_settings), LoadMediaProperties(ini_settings)},
                min_num_tracks
            };
        }
        else {
            throw HealthCheckLoadError{
                "\"media_type\" key was set to an invalid value: " + media_type};
        }
    }
} // end anonymous namespace


namespace MPF::COMPONENT {


HealthCheckLoadError::HealthCheckLoadError(std::string_view message)
    : std::runtime_error{"Failed to load health ini file due to: " + std::string{message}}
{
}


HealthCheckImpl::HealthCheckImpl(
        LoggerWrapper logger, std::optional<std::string_view> health_check_file)
    : logger_{std::move(logger)}
    , max_attempts_{GetMaxAttempts(logger_)}
    , health_check_timeout_{GetHealthCheckTimeout(logger_)}
    , health_check_job_{LoadHealthCheckJob(health_check_file)}
{
}


bool HealthCheckImpl::IsTimeForAnotherCheck() const {
    if (!health_check_timeout_) {
        return true;
    }
    return clock_t::now() >= next_check_time_;
}


void HealthCheckImpl::SetNextHealthCheckTime() {
    if (health_check_timeout_) {
        next_check_time_ = clock_t::now() + *health_check_timeout_;
    }
}


std::optional<int> HealthCheckImpl::GetMaxAttempts(const LoggerWrapper& logger) {
    auto attempt_env_val = BatchExecutorUtil::GetEnv("HEALTH_CHECK_RETRY_MAX_ATTEMPTS");
    if (!attempt_env_val) {
        logger.Warn("No value was provided for \"HEALTH_CHECK_RETRY_MAX_ATTEMPTS\". "
                    "Component will never exit because of failed health checks.");
        return {};
    }
    try {
        int num_checks = std::stoi(*attempt_env_val);
        if (num_checks > 0) {
            return num_checks;
        }
        else {
            return {};
        }
    }
    catch (const std::invalid_argument& e) {
        throw std::runtime_error{std::string{
            "The \"HEALTH_CHECK_RETRY_MAX_ATTEMPTS\" environment variable did not"
            " contain a number: "} + e.what()};
    }
}

std::optional<std::chrono::seconds> HealthCheckImpl::GetHealthCheckTimeout(
        const LoggerWrapper& logger) {
    auto timeout_env_val = BatchExecutorUtil::GetEnv("HEALTH_CHECK_TIMEOUT");
    if (!timeout_env_val) {
        logger.Warn("No value was provided for \"HEALTH_CHECK_TIMEOUT\". "
                    "Component will run a health check before each job.");
        return {};
    }
    try {
        int num_seconds = std::stoi(*timeout_env_val);
        if (num_seconds > 0) {
            return std::chrono::seconds{num_seconds};
        }
        else {
            return {};
        }
    }
    catch (const std::invalid_argument& e) {
        throw std::runtime_error{std::string{
            "The \"HEALTH_CHECK_TIMEOUT\" environment variable did not contain a number: "}
            + e.what()};
    }
}



HealthCheck::HealthCheck(
        LoggerWrapper logger, std::optional<std::string_view> health_check_file)
    : impl_{Init(std::move(logger), health_check_file)}
{
}

std::optional<HealthCheckImpl> HealthCheck::Init(
        LoggerWrapper logger, std::optional<std::string_view> health_check_file) {
    if (IsEnabled(logger)) {
        return HealthCheckImpl{std::move(logger), health_check_file};
    }
    else {
        return {};
    }
}

bool HealthCheck::IsEnabled(const LoggerWrapper& logger) {
    auto mode = BatchExecutorUtil::GetEnv("HEALTH_CHECK");
    if (!mode) {
        return false;
    }
    else if (BatchExecutorUtil::EqualsIgnoreCase(*mode, "ENABLED")
            || BatchExecutorUtil::EqualsIgnoreCase(*mode, "TRUE")
            || mode == "1") {
        logger.Info("Enabling health checks.");
        return true;
    }
    else {
        logger.Warn("Unknown health check mode \"", *mode, "\". Disabling health checks.");
        return false;
    }
}
}
