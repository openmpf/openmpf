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

#include <pybind11/pybind11.h>

#include "PythonBase.h"

namespace MPF {

std::shared_ptr<py::scoped_interpreter> PythonBase::GetInterpreter() {
    static std::weak_ptr<py::scoped_interpreter> interpreter_weak;
    if (auto existing_interpreter = interpreter_weak.lock()) {
        return existing_interpreter;
    }
    auto new_interpreter = std::make_shared<py::scoped_interpreter>(false);
    interpreter_weak = new_interpreter;

    // During import, the Python signal module installs signal handlers. The signal handler that
    // Python installs for SIGINT prevents C++ code from handling the signal. Below, we remove
    // Python's custom SIGINT handler and restore the default behavior.
    auto signal_module = py::module_::import("signal");
    signal_module.attr("signal")(
        signal_module.attr("SIGINT"), signal_module.attr("SIG_DFL"));
    return new_interpreter;
}

} // namespace MPF
