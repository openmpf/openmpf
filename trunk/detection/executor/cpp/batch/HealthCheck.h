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

#pragma once

#include <chrono>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>
#include <utility>
#include <variant>

#include <MPFDetectionComponent.h>

#include "BatchExecutorUtil.h"
#include "LoggerWrapper.h"


namespace MPF::COMPONENT {


class HealthCheckLoadError : public std::runtime_error {
public:
    explicit HealthCheckLoadError(std::string_view message);
};


class FailedHealthCheck : public std::runtime_error {
    using std::runtime_error::runtime_error;
};


struct HealthCheckJob {
    std::variant<MPFVideoJob, MPFImageJob, MPFAudioJob, MPFGenericJob> job;
    int expected_num_tracks;
};


class HealthCheck;


class HealthCheckImpl {
private:
    friend HealthCheck;

    LoggerWrapper logger_;

    std::optional<int> max_attempts_;

    std::optional<std::chrono::seconds> health_check_timeout_;

    HealthCheckJob health_check_job_;

    using clock_t = std::chrono::steady_clock;
    clock_t::time_point next_check_time_ = clock_t::now();

    int failure_count_{0};

    HealthCheckImpl(LoggerWrapper logger, std::optional<std::string_view> health_check_file);

    /**
     * The on failure function is passed as a parameter so that the rollback can happen immediately
     * and this method can sleep until the component should try the health check again.
    */
    template <typename Component, typename OnFailureFn>
    bool Check(Component& component, OnFailureFn&& on_failure) {
        auto log_ctx = logger_.GetJobContext("Health check");
        try {
            RunHealthCheck(component);
            failure_count_ = 0;
            return true;
        }
        catch (const FailedHealthCheck& e) {
            failure_count_++;
            logger_.Error(
                "Health check failed ", failure_count_, " times. This failure was due to: ",
                e.what());
            std::forward<OnFailureFn>(on_failure)();
            if (max_attempts_ && failure_count_ >= *max_attempts_) {
                throw;
            }
            if (health_check_timeout_) {
                std::this_thread::sleep_until(next_check_time_);
            }
            return false;
        }
    }

    template <typename Component>
    void RunHealthCheck(Component& component) {
        if (!IsTimeForAnotherCheck()) {
            return;
        }
        std::size_t num_results = 0;
        try {
            logger_.Info("Running health check.");
            num_results = std::visit([&component](const auto& job) {
                return component.GetDetections(job).size();
            }, health_check_job_.job);
            SetNextHealthCheckTime();
        }
        catch (const std::exception& e) {
            SetNextHealthCheckTime();
            throw FailedHealthCheck{
                std::string{"Component threw an exception during health check: "} + e.what()};
        }
        if (num_results < health_check_job_.expected_num_tracks) {
            throw FailedHealthCheck{
                "Only found " + std::to_string(num_results) + " results, but "
                + std::to_string(health_check_job_.expected_num_tracks) + " were expected."};
        }
        logger_.Info("Health check passed.");
    }


    bool IsTimeForAnotherCheck() const;

    void SetNextHealthCheckTime();

    static std::optional<int> GetMaxAttempts(const LoggerWrapper& logger);

    static std::optional<std::chrono::seconds> GetHealthCheckTimeout(const LoggerWrapper& logger);
};



class HealthCheck {
public:
    explicit HealthCheck(
        LoggerWrapper logger, std::optional<std::string_view> health_check_file={});


    template <typename Component, typename OnFailureFn>
    bool Check(Component& component, OnFailureFn&& on_failure) {
        // Using optional instead of sub-classing because method templates can't be virtual.
        if (impl_) {
            return impl_->Check(component, std::forward<OnFailureFn>(on_failure));
        }
        else {
            return true;
        }
    }

private:
    std::optional<HealthCheckImpl> impl_;

    static std::optional<HealthCheckImpl> Init(
            LoggerWrapper logger, std::optional<std::string_view> health_check_file);

    static bool IsEnabled(const LoggerWrapper& logger);
};

}
