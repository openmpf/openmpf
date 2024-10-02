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

#include <stdexcept>
#include <string_view>
#include <utility>

#include <pybind11/pybind11.h>


namespace MPF::PythonUtil {
    namespace py = pybind11;

    template <typename TFirst = py::object, typename TSecond = py::object>
    std::pair<TFirst, TSecond> ToStdPair(py::handle obj) {
        try {
            return {obj[py::int_{0}].cast<TFirst>(), obj[py::int_{1}].cast<TSecond>()};
        }
        catch (const py::error_already_set& e) {
            if (!e.matches(PyExc_AttributeError) && !e.matches(PyExc_TypeError)) {
                throw;
            }
        }

        auto first_iter = obj.begin();
        if (first_iter == obj.end()) {
            throw std::length_error{"Expected at least two items, but iterable is empty."};
        }
        auto second_iter = first_iter;
        ++second_iter;
        if (second_iter == obj.end()) {
            throw std::length_error{"Expected at least two items, but iterable only contains one."};
        }
        return {first_iter->cast<TFirst>(), second_iter->cast<TSecond>()};
    }

    py::object LoadComponent(std::string_view distribution_name);
} // namespace MPF::PythonUtil
