/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

#ifndef MPF_LAZYLOGGERWRAPPER_H
#define MPF_LAZYLOGGERWRAPPER_H

#include <sstream>
#include <string>
#include <utility>


template<typename Logger>
class LazyLoggerWrapper {
public:

    template<typename... CtorArgs>
    explicit LazyLoggerWrapper(const std::string &log_level, CtorArgs&&... args)
            : base_logger_(std::forward<CtorArgs>(args)...)
            , debug_enabled_(log_level == "DEBUG" || log_level == "TRACE")
            , info_enabled_(debug_enabled_ || log_level == "INFO")
            , warn_enabled_(info_enabled_ || log_level == "WARN")
            , error_enabled_(warn_enabled_ || log_level == "ERROR")
            , fatal_enabled_(error_enabled_ || log_level == "FATAL")
    {
    }

    template<typename... Args>
    void Debug(Args&&... args) {
        if (debug_enabled_) {
            base_logger_.Debug(ToString(std::forward<Args>(args)...));
        }
    }

    template<typename... Args>
    void Info(Args&&... args) {
        if (info_enabled_) {
            base_logger_.Info(ToString(std::forward<Args>(args)...));
        }
    }

    template<typename... Args>
    void Warn(Args&&... args) {
        if (warn_enabled_) {
            base_logger_.Warn(ToString(std::forward<Args>(args)...));
        }
    }

    template<typename... Args>
    void Error(Args&&... args) {
        if (error_enabled_) {
            base_logger_.Error(ToString(std::forward<Args>(args)...));
        }
    }

    template<typename... Args>
    void Fatal(Args&&... args) {
        if (fatal_enabled_) {
            base_logger_.Fatal(ToString(std::forward<Args>(args)...));
        }
    }


private:
    Logger base_logger_;
    bool debug_enabled_;
    bool info_enabled_;
    bool warn_enabled_;
    bool error_enabled_;
    bool fatal_enabled_;

    template<typename... Args>
    std::string ToString(Args&&... args) {
        std::ostringstream ss;
        InsertAll(ss, std::forward<Args>(args)...);
        return ss.str();
    }

    // Base case for template recursion.
    void InsertAll(std::ostringstream &ss) {
    }

    template<typename Head, typename... Tail>
    void InsertAll(std::ostringstream &ss, Head&& head, Tail&&... tail) {
        ss << std::forward<Head>(head);
        InsertAll(ss, std::forward<Tail>(tail)...);
    }
};


#endif //MPF_LAZYLOGGERWRAPPER_H
