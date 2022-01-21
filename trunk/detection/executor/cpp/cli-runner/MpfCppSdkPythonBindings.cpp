/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

#include <pybind11/pybind11.h>
#include <pybind11/iostream.h>
#include <pybind11/stl.h>
#include <pybind11/stl_bind.h>

#include <pyerrors.h>

#include <DlClassLoader.h>
#include <MPFDetectionComponent.h>
#include <MPFDetectionObjects.h>

namespace py = pybind11;
using namespace pybind11::literals;
using namespace MPF::COMPONENT;


using CppComponent = DlClassLoader<MPFDetectionComponent>;

using call_guard_t = py::call_guard<
        // Binds std::cout to Python's sys.stdout.
        py::scoped_ostream_redirect,
        // Binds std::cerr to Python's sys.stderr.
        py::scoped_estream_redirect,
        // Release the global interpreter lock when running component code. This is safe because
        // the C++ components don't interact with any Python objects. This needs to be after the
        // stream redirects because they need the GIL when initially created.
        py::gil_scoped_release
>;

// Without PYBIND11_MAKE_OPAQUE, when a function returns a std::vector, pybind will automatically
// copy it in to a Python list. We don't want this to occur with the results of the GetDetections()
// methods because the CLI runner is just going to loop over the results and convert them in to
// a JSON serializable Python dict. We call py::bind_vector below so that we can iterate over
// std::vector in Python.
PYBIND11_MAKE_OPAQUE(std::vector<MPFVideoTrack>);
PYBIND11_MAKE_OPAQUE(std::vector<MPFImageLocation>);
PYBIND11_MAKE_OPAQUE(std::vector<MPFAudioTrack>);
PYBIND11_MAKE_OPAQUE(std::vector<MPFGenericTrack>);


PYBIND11_MODULE(mpf_cpp_sdk, m) {
    m.doc() = "OpenMPF C++ SDK Python Bindings";

    py::class_<MPFJob>(m, "Job")
            .def_readonly("job_name", &MPFJob::job_name)
            .def_readonly("data_uri", &MPFJob::data_uri)
            .def_readonly("job_properties", &MPFJob::job_properties)
            .def_readonly("media_properties", &MPFJob::media_properties);


    py::class_<MPFImageJob, MPFJob>(m, "ImageJob")
            .def(py::init<const std::string&, const std::string&, const Properties&,
                          const Properties&>(),
                "job_name"_a, "data_uri"_a, "job_properties"_a, "media_properties"_a);


    py::class_<MPFVideoJob, MPFJob>(m, "VideoJob")
            .def(py::init<const std::string&, const std::string&, int, int, const Properties&,
                          const Properties&>(),
                 "job_name"_a, "data_uri"_a, "start_frame"_a, "stop_frame"_a, "job_properties"_a,
                 "media_properties"_a)
            .def_readonly("start_frame", &MPFVideoJob::start_frame)
            .def_readonly("stop_frame", &MPFVideoJob::stop_frame);


    py::class_<MPFAudioJob, MPFJob>(m, "AudioJob")
        .def(py::init<const std::string&, const std::string&, int, int, const Properties&,
                      const Properties&>(),
             "job_name"_a, "data_uri"_a, "start_time"_a, "stop_time"_a, "job_properties"_a,
             "media_properties"_a)
        .def_readonly("start_time", &MPFAudioJob::start_time)
        .def_readonly("stop_time", &MPFAudioJob::stop_time);


    py::class_<MPFGenericJob, MPFJob>(m, "GenericJob")
            .def(py::init<const std::string&, const std::string&, const Properties&,
                          const Properties&>(),
                 "job_name"_a, "data_uri"_a, "job_properties"_a, "media_properties"_a);




    py::class_<MPFImageLocation>(m, "ImageLocation")
            .def(py::init<int, int, int, int, float, const Properties&>(),
                 "x_left_upper"_a, "y_left_upper"_a, "width"_a, "height"_a,
                 "confidence"_a=-1.0, "detection_properties"_a=Properties{})
            .def_readwrite("x_left_upper", &MPFImageLocation::x_left_upper)
            .def_readwrite("y_left_upper", &MPFImageLocation::y_left_upper)
            .def_readwrite("width", &MPFImageLocation::width)
            .def_readwrite("height", &MPFImageLocation::height)
            .def_readwrite("confidence", &MPFImageLocation::confidence)
            .def_readwrite("detection_properties", &MPFImageLocation::detection_properties);


    py::class_<MPFVideoTrack>(m, "VideoTrack")
            .def(py::init<int, int, float, const Properties&>(),
                 "start"_a, "stop"_a, "confidence"_a=-1.0, "detection_properties"_a=Properties{})
            .def_readwrite("start_frame", &MPFVideoTrack::start_frame)
            .def_readwrite("stop_frame", &MPFVideoTrack::stop_frame)
            .def_readwrite("confidence", &MPFVideoTrack::confidence)
            .def_readwrite("frame_locations", &MPFVideoTrack::frame_locations)
            .def_readwrite("detection_properties", &MPFVideoTrack::detection_properties);


    py::class_<MPFAudioTrack>(m, "AudioTrack")
            .def(py::init<int, int, float, const Properties&>(),
                 "start"_a, "stop"_a, "confidence"_a=-1.0, "detection_properties"_a=Properties{})
            .def_readwrite("start_time", &MPFAudioTrack::start_time)
            .def_readwrite("stop_time", &MPFAudioTrack::stop_time)
            .def_readwrite("confidence", &MPFAudioTrack::confidence)
            .def_readwrite("detection_properties", &MPFAudioTrack::detection_properties);


    py::class_<MPFGenericTrack>(m, "GenericTrack")
            .def(py::init<float, const Properties&>(),
                    "confidence"_a=-1.0, "detection_properties"_a=Properties{})
            .def_readwrite("confidence", &MPFGenericTrack::confidence)
            .def_readwrite("detection_properties", &MPFGenericTrack::detection_properties);

    py::bind_vector<std::vector<MPFVideoTrack>>(m, "VectorVideoTrack");
    py::bind_vector<std::vector<MPFImageLocation>>(m, "VectorImageLocation");
    py::bind_vector<std::vector<MPFAudioTrack>>(m, "VectorAudioTrack");
    py::bind_vector<std::vector<MPFGenericTrack>>(m, "VectorGenericTrack");


    py::enum_<MPFDetectionDataType>(m, "DetectionDataType")
            .value("UNKNOWN", MPFDetectionDataType::UNKNOWN)
            .value("VIDEO", MPFDetectionDataType::VIDEO)
            .value("IMAGE", MPFDetectionDataType::IMAGE)
            .value("AUDIO", MPFDetectionDataType::AUDIO)
            .value("INVALID_TYPE", MPFDetectionDataType::INVALID_TYPE);


    py::class_<CppComponent>(m, "CppComponent")
            .def(py::init([](const std::string& lib_path) {
                return CppComponent(lib_path,  "component_creator", "component_deleter");
            }), "lib_path"_a, call_guard_t())
            .def("SetRunDirectory",
                    [](CppComponent& c, const std::string &dir) { return c->SetRunDirectory(dir); },
                    "run_dir"_a,
                    call_guard_t())
            .def("Init",
                    [](CppComponent& c) { return c->Init(); },
                    call_guard_t())
            .def("Close",
                    [](CppComponent& c) { return c->Close(); },
                    call_guard_t())
            .def("GetDetectionType",
                    [](CppComponent& c) { return c->GetDetectionType();  },
                    call_guard_t())
            .def("Supports",
                    [](CppComponent& c, MPFDetectionDataType type) { return c->Supports(type); },
                    "data_type"_a,
                    call_guard_t())
            .def("GetDetections",
                    [](CppComponent& c, const MPFImageJob &job) { return c->GetDetections(job); },
                    "job"_a,
                    call_guard_t())
            .def("GetDetections",
                 [](CppComponent& c, const MPFVideoJob &job) { return c->GetDetections(job); },
                 "job"_a,
                 call_guard_t())
            .def("GetDetections",
                 [](CppComponent& c, const MPFAudioJob &job) { return c->GetDetections(job); },
                 "job"_a,
                 call_guard_t())
            .def("GetDetections",
                 [](CppComponent& c, const MPFGenericJob &job) { return c->GetDetections(job); },
                 "job"_a,
                 call_guard_t());



    py::enum_<MPFDetectionError>(m, "DetectionError")
            .value("DETECTION_SUCCESS", MPFDetectionError::MPF_DETECTION_SUCCESS)
            .value("OTHER_DETECTION_ERROR_TYPE", MPFDetectionError::MPF_OTHER_DETECTION_ERROR_TYPE)
            .value("DETECTION_NOT_INITIALIZED", MPFDetectionError::MPF_DETECTION_NOT_INITIALIZED)
            .value("UNSUPPORTED_DATA_TYPE", MPFDetectionError::MPF_UNSUPPORTED_DATA_TYPE)
            .value("COULD_NOT_OPEN_DATAFILE", MPFDetectionError::MPF_COULD_NOT_OPEN_DATAFILE)
            .value("COULD_NOT_READ_DATAFILE", MPFDetectionError::MPF_COULD_NOT_READ_DATAFILE)
            .value("FILE_WRITE_ERROR", MPFDetectionError::MPF_FILE_WRITE_ERROR)
            .value("BAD_FRAME_SIZE", MPFDetectionError::MPF_BAD_FRAME_SIZE)
            .value("DETECTION_FAILED", MPFDetectionError::MPF_DETECTION_FAILED)
            .value("INVALID_PROPERTY", MPFDetectionError::MPF_INVALID_PROPERTY)
            .value("MISSING_PROPERTY", MPFDetectionError::MPF_MISSING_PROPERTY)
            .value("GPU_ERROR", MPFDetectionError::MPF_GPU_ERROR)
            .value("NETWORK_ERROR", MPFDetectionError::MPF_NETWORK_ERROR)
            .value("COULD_NOT_OPEN_MEDIA", MPFDetectionError::MPF_COULD_NOT_OPEN_MEDIA)
            .value("COULD_NOT_READ_MEDIA", MPFDetectionError::MPF_COULD_NOT_READ_MEDIA);


    py::object property = py::module::import("builtins").attr("property");

    static py::exception<MPFDetectionException> detection_exception(m, "DetectionException");

    detection_exception.attr("what") = property(py::cpp_function([] (py::handle self) {
        return self.attr("args")[py::int_(0)];
    }));

    detection_exception.attr("error_code") = property(py::cpp_function([] (py::handle self) {
        return self.attr("args")[py::int_(1)];
    }));

    detection_exception.attr("__str__") = py::cpp_function([] (py::handle self) {
        auto args = self.attr("args");
        return "{} ({})"_s.format(args[py::int_(0)], args[py::int_(1)]);
    }, py::is_method(detection_exception));


    static py::exception<DlError> dl_error(m, "DlError", detection_exception.ptr());


    py::register_exception_translator([](std::exception_ptr ex_ptr) {
        try {
            if (ex_ptr) {
                std::rethrow_exception(ex_ptr);
            }
        }
        catch (const DlError &e) {
            auto args = py::make_tuple(e.what(), e.error_code);
            PyErr_SetObject(dl_error.ptr(), args.ptr());
        }
        catch (const MPFDetectionException &e) {
            auto args = py::make_tuple(e.what(), e.error_code);
            PyErr_SetObject(detection_exception.ptr(), args.ptr());
        }
    });
}
