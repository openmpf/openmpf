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

#include <string>

#include "ComponentLoadError.h"

#include "PythonUtil.h"

using namespace std::string_literals;

namespace MPF::PythonUtil {

py::object LoadComponent(std::string_view distribution_name) {
    auto import_meta = py::module_::import("importlib.metadata");
    try {
        auto distribution = import_meta.attr("distribution")(distribution_name);
        py::list entry_points = distribution.attr("entry_points");
        if (entry_points.size() == 1) {
            return entry_points[0].attr("load")();
        }

        py::str expected_group = "mpf.exported_component";
        py::str expected_name = "component";
        py::object group_matches = py::none();
        for (const auto& entry_point : entry_points) {
            if (!expected_group.equal(entry_point.attr("group"))) {
                continue;
            }
            if (expected_name.equal(entry_point.attr("name"))) {
                return entry_point.attr("load")();
            }
            group_matches = entry_point.cast<py::object>();
        }
        if (!group_matches.is_none()) {
            // An entry point in the "mpf.exported_component" group was found, but the
            // left-hand side of the '=' was something else. For example
            // 'mpf.exported_component': 'MyComponentClass = my_module:MyComponentClass'
            // We really only care about the entry point group, since we don't do anything
            // with entry point name.
            return group_matches.attr("load")();
        }
    }
    catch (const py::error_already_set& e) {
        throw ComponentLoadError{"Failed to load component due to: "s + e.what()};
    }
    throw ComponentLoadError{"Component entrypoint not present."};
}

} // namespace MPF::PythonUtil
