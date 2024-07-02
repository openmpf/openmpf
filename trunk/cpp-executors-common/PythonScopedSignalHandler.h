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

#include <pybind11/pybind11.h>

namespace MPF {

namespace py = pybind11;

class __attribute__ ((visibility("hidden"))) PythonScopedSignalHandler {
public:
    // Make Python handle SIGINT.
    PythonScopedSignalHandler();

    // Allow the calling program to handle SIGINT.
    ~PythonScopedSignalHandler();

    PythonScopedSignalHandler(const PythonScopedSignalHandler&) = delete;
    PythonScopedSignalHandler& operator=(const PythonScopedSignalHandler&) = delete;
    PythonScopedSignalHandler(PythonScopedSignalHandler&&) = delete;
    PythonScopedSignalHandler& operator=(PythonScopedSignalHandler&&) = delete;

private:
    py::object signal_func_;
    py::object sigint_;
    py::object prev_handler_;

    explicit PythonScopedSignalHandler(const py::module_& signal_module);
};

} // namespace MPF
