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

#include <memory>
#include <sstream>
#include <string>
#include <string_view>
#include <type_traits>
#include <utility>


class ILogger {
public:
    virtual void Debug(std::string_view message) = 0;
    virtual void Info(std::string_view message) = 0;
    virtual void Warn(std::string_view message) = 0;
    virtual void Error(std::string_view message) = 0;
    virtual void Fatal(std::string_view message) = 0;

    virtual void SetJobName(std::string_view job_name) = 0;

    virtual ~ILogger() = default;
};


class JobLogContext {
public:
    JobLogContext(
        std::string_view new_job,
        std::shared_ptr<std::string> logger_job_ref,
        std::shared_ptr<ILogger> logger_impl);
    ~JobLogContext();

    // Only allow move construction.
    JobLogContext(JobLogContext&&) = default;
    JobLogContext& operator=(JobLogContext&&) = delete;

    JobLogContext(const JobLogContext&) = delete;
    JobLogContext& operator=(const JobLogContext&) = delete;

private:
    std::shared_ptr<std::string> logger_job_ref_;
    std::string previous_job_;
    std::shared_ptr<ILogger> logger_impl_;
};


class LoggerWrapper {
public:
    explicit LoggerWrapper(std::string_view log_level, std::unique_ptr<ILogger> base_logger)
            : base_logger_(std::move(base_logger))
            , debug_enabled_(log_level == "DEBUG" || log_level == "TRACE")
            , info_enabled_(debug_enabled_ || log_level == "INFO")
            , warn_enabled_(info_enabled_ || log_level == "WARN")
            , error_enabled_(warn_enabled_ || log_level == "ERROR")
            , fatal_enabled_(error_enabled_ || log_level == "FATAL")
    {
    }

    template<typename... Args>
    void Debug(Args&&... args) const {
        if (debug_enabled_) {
            base_logger_->Debug(ToString(std::forward<Args>(args)...));
        }
    }

    template<typename... Args>
    void Info(Args&&... args) const {
        if (info_enabled_) {
            base_logger_->Info(ToString(std::forward<Args>(args)...));
        }
    }

    template<typename... Args>
    void Warn(Args&&... args) const {
        if (warn_enabled_) {
            base_logger_->Warn(ToString(std::forward<Args>(args)...));
        }
    }

    template<typename... Args>
    void Error(Args&&... args) const {
        if (error_enabled_) {
            base_logger_->Error(ToString(std::forward<Args>(args)...));
        }
    }

    template<typename... Args>
    void Fatal(Args&&... args) const {
        if (fatal_enabled_) {
            base_logger_->Fatal(ToString(std::forward<Args>(args)...));
        }
    }

    [[nodiscard]] JobLogContext GetJobContext(std::string_view job_name) {
        return {'[' + std::string{job_name} + "] ", current_job_name_, base_logger_};
    }


private:
    std::shared_ptr<ILogger> base_logger_;
    bool debug_enabled_;
    bool info_enabled_;
    bool warn_enabled_;
    bool error_enabled_;
    bool fatal_enabled_;
    std::shared_ptr<std::string> current_job_name_ = std::make_shared<std::string>();

    template<typename Arg, typename... Args>
    static auto ToString(Arg&& arg, Args&&... args) {
        if constexpr (sizeof...(Args) == 0 && std::is_convertible_v<Arg, std::string_view>) {
            return std::string_view{std::forward<Arg>(arg)};
        }
        else {
            std::ostringstream ss;
            ss << std::forward<Arg>(arg);
            (ss << ... << std::forward<Args>(args));
            return ss.str();
        }
    }
};
