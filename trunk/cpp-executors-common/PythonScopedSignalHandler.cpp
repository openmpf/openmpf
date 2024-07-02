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

#include "PythonScopedSignalHandler.h"

namespace MPF {

PythonScopedSignalHandler::PythonScopedSignalHandler()
    : PythonScopedSignalHandler{py::module_::import("signal")} {
}

PythonScopedSignalHandler::PythonScopedSignalHandler(const py::module_& signal_module)
    : signal_func_{signal_module.attr("signal")}
    , sigint_{signal_module.attr("SIGINT")}
    , prev_handler_{signal_func_(sigint_, signal_module.attr("default_int_handler"))} {
}


PythonScopedSignalHandler::~PythonScopedSignalHandler() {
    try {
        signal_func_(sigint_, prev_handler_);
    }
    catch (py::error_already_set& e) {
        e.discard_as_unraisable(__func__);
    }
}
} // namespace MPF
