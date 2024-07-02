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

#include <string_view>

#include <pybind11/pybind11.h>

#include "LoggerWrapper.h"
#include "PythonBase.h"
#include "subject.pb.h"

namespace MPF::SUBJECT {

namespace mpf_buffers = org::mitre::mpf::wfm::buffers;
namespace py = pybind11;


class PythonComponent : private PythonBase {
public:
    PythonComponent(LoggerWrapper logger, std::string_view distribution_name);

    mpf_buffers::SubjectTrackingResult GetSubjects(const mpf_buffers::SubjectTrackingJob& job) const;

private:
    LoggerWrapper logger_;
    py::module_ mpf_subject_api_{py::module_::import("mpf_subject_api")};
    py::module_ mpf_component_api_{py::module_::import("mpf_component_api")};
    py::object component_instance_;


    py::object ConvertSubjectTrackingJob(
            const mpf_buffers::SubjectTrackingJob& subject_tracking_job) const;

    py::object ConvertImageDetectionJob(
            const mpf_buffers::ImageDetectionJobResults& image_job) const;

    py::object ConvertVideoDetectionJob(
            const mpf_buffers::VideoDetectionJobResults& video_job) const;
};

} // namespace MPF::SUBJECT
