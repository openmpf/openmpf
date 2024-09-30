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

#pragma once

#include <memory>
#include <string_view>

#include <pybind11/pybind11.h>

#include "ILogger.h"
#include "PythonBase.h"

namespace MPF {

namespace py = pybind11;

class AddLogContextFilter;

class PythonLogger : private PythonBase, public ILogger {
public:
    PythonLogger(std::string_view log_level, std::string_view logger_name);

    void Debug(std::string_view message) override;

    void Info(std::string_view message) override;

    void Warn(std::string_view message) override;

    void Error(std::string_view message) override;

    void Fatal(std::string_view message) override;

    void SetContextMessage(std::string_view context_msg) override;

private:
    PythonLogger(std::shared_ptr<AddLogContextFilter> filter, py::handle logger);

    std::shared_ptr<AddLogContextFilter> ctx_filter_;
    py::function debug_;
    py::function info_;
    py::function warn_;
    py::function error_;
    py::function fatal_;
};

} // namespace MPF
