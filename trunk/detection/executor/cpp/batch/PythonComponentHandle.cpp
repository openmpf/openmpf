/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

#include <pybind11/embed.h>
#include <pybind11/stl.h>

#include "PythonComponentHandle.h"
#include "ComponentLoadError.h"

namespace py = pybind11;


namespace MPF { namespace COMPONENT {

    namespace {
        py::object get_builtin(const char *name) {
            py::module builtin = py::module::import("builtins");
            return builtin.attr(name);
        }

        namespace debug {
            void dump_py_obj(const py::handle &object, bool deep=false, bool print_values=false) {
                py::print("===Dumping Python Object===");
                py::print("Object: ", object);
                py::print("Object Type: ", object.get_type());

                py::function lookup_func = get_builtin(deep || !py::hasattr(object, "__dict__") ? "dir" : "vars");

                py::object attr_seq = lookup_func(object);
                py::print("len", py::len(attr_seq));

                for (const auto &attr_key : attr_seq) {
                    py::object attr_val = object.attr(attr_key);
                    if (print_values) {
                        py::print(attr_key, ":\t", attr_val);
                    }
                    else {
                        py::print(attr_key, ":\t", attr_val.get_type());
                    }
                }

                py::print("====\n");
            }
        }


        std::string get_module_directory(const std::string& module_path) {
            size_t final_slash_pos = module_path.rfind('/');
            if (final_slash_pos == std::string::npos) {
                return ".";
            }
            return module_path.substr(0, final_slash_pos);
        }


        std::string get_module_name(const std::string& module_path) {
            size_t final_slash_pos = module_path.rfind('/') + 1;
            size_t final_dot_pos = module_path.rfind('.');
            return module_path.substr(final_slash_pos, final_dot_pos - final_slash_pos);
        }


        void initialize_python() {
            static bool initialized = false;
            if (!initialized) {
                py::initialize_interpreter();
                initialized = true;
            }
        }


        void add_module_dir_to_python_path(const std::string &module_path) {
            std::string module_dir = get_module_directory(module_path);
            py::module::import("sys")
                    .attr("path")
                    .attr("insert")(0, module_dir);
        }


        bool is_callable(py::handle obj) {
            // This returns true for any callable, not just functions.
            return py::isinstance<py::function>(obj);
        }


        py::object load_component_from_module(const std::string &module_name, const std::string &module_path) {
            static const char * const export_component_var = "EXPORT_MPF_COMPONENT";
            static const std::string error_msg_end =
                    "Python components must declare a module level variable named \""
                    + std::string(export_component_var)
                    + "\", " "which gets assigned to either a class or some other callable.";

            py::module component_module;
            try {
                component_module = py::module::import(module_name.c_str());
            }
            catch (const std::exception &ex) {
                throw ComponentLoadError(
                        "An error occurred while trying to import the component module located at \""
                        + module_path + "\": " + ex.what());
            }

            if (!py::hasattr(component_module, export_component_var)) {
                throw ComponentLoadError(
                        "The module located at \"" + module_path
                        + "\" did not contain a module level variable named \""
                        + export_component_var + "\". " + error_msg_end);
            }

            py::object exported_component_class = component_module.attr(export_component_var);
            if (!is_callable(exported_component_class)) {
                throw ComponentLoadError(
                        "The module located at \"" + module_path + "\" contained a module level variable named \""
                        + export_component_var + "\", but its value was neither a class nor a callable. "
                        + error_msg_end);
            }
            return exported_component_class;

        }

        py::object load_component_from_file(const std::string &module_path) {
            add_module_dir_to_python_path(module_path);
            std::string module_name = get_module_name(module_path);
            return load_component_from_module(module_name, module_path);
        }


        /*
          Entry point documentation: https://setuptools.readthedocs.io/en/latest/pkg_resources.html#convenience-api
          Example entry point declaration in a component's setup.py:
          import setuptools
          setuptools.setup(
                name=...
                ...
                entry_points={
                    'mpf.exported_component': 'component = my_module:MyComponentClass'
                }
            )
        */
        py::object load_component_from_package(const std::string &distribution_name) {
            static const char * const entry_point_group = "mpf.exported_component";

            py::module pkg_resources = py::module::import("pkg_resources");
            py::dict entry_points;
            try {
                entry_points = pkg_resources.attr("get_entry_map")(distribution_name, entry_point_group);
            }
            catch (py::error_already_set &ex) {
                if (!ex.matches(pkg_resources.attr("DistributionNotFound"))) {
                    throw;
                }
            }

            if (entry_points.size() < 1) {
                try {
                    return load_component_from_module(distribution_name, distribution_name);
                }
                catch (const ComponentLoadError &ex) {
                    throw ComponentLoadError(
                            "The \"" + distribution_name + "\" component did not declare an \"" + entry_point_group
                            + "\" entry point so an attempt was made to load the component from a module named \""
                            + distribution_name + "\", but that also failed due to: " + ex.what());
                }
            }

            static const char * const expected_entry_point_name = "component";
            // The left hand side of the '=' in entry point declaration should be "component"
            py::handle component_entry_point = entry_points.attr("get")(expected_entry_point_name);
            if (component_entry_point.is_none()) {
                // An entry point in the "mpf.exported_component" group was found,
                // but the left hand side of the '=' was something else.
                // For example 'mpf.exported_component': 'MyComponentClass = my_module:MyComponentClass'
                // We really only care about the entry point group, since we don't do anything with entry point name.
                component_entry_point = entry_points.begin()->second;
            }
            return component_entry_point.attr("load")();
        }


        bool is_python_file(const std::string &lib_path) {
            static const std::string extension = ".py";
            if (lib_path.size() < extension.size()) {
                return false;
            }
            size_t start = lib_path.size() - extension.size();
            return lib_path.find(".py", start) != std::string::npos;
        }




        py::object load_component(const std::string &component_lib) {
            py::object component_class = is_python_file(component_lib)
                                         ? load_component_from_file(component_lib)
                                         : load_component_from_package(component_lib);
            try {
                return component_class();
            }
            catch (const std::exception &ex) {
                throw ComponentLoadError(
                        std::string("An error occurred while trying to create an instance of the Python component: ")
                        + ex.what());
            }
        }


        std::string to_std_string(py::handle obj) {
            return py::str(obj);
        }

        py::object get_method(py::handle instance, const std::string &method_name) {
            py::object method = py::getattr(instance, method_name.c_str(), py::none());
            if (method.is_none() || is_callable(method)) {
                return method;
            }
            throw std::runtime_error("Expected " + method_name + " to be callable, but it was: "
                                     + to_std_string(method.get_type()));
        }


        int get_int(py::handle obj, const char *field) {
            return obj.attr(field).cast<py::int_>();
        }

        float get_float(py::handle obj, const char * field) {
            return obj.attr(field).cast<py::float_>();
        }


        template <typename TFirst=py::object, typename TSecond=py::object>
        std::pair<TFirst, TSecond> to_std_pair(py::handle obj) {
            py::object bracket_operator = py::getattr(obj, "__getitem__", py::none());
            if (!bracket_operator.is_none()) {
                return { bracket_operator(0).cast<TFirst>(), bracket_operator(1).cast<TSecond>() };
            }

            auto pair_iter = obj.begin();
            auto first = pair_iter->cast<TFirst>();
            ++pair_iter;
            return { first, pair_iter->cast<TSecond>() };
        }


        Properties get_properties(py::handle obj, const char * field) {
            Properties result;
            py::object iter_items = obj.attr(field).attr("items")();
            for (auto &pair : iter_items) {
                result.insert(to_std_pair<py::str, py::str>(pair));
            }
            return result;
        }

        MPFImageLocation convert_image_location(py::handle py_img_loc) {
            return {
                    get_int(py_img_loc, "x_left_upper"),
                    get_int(py_img_loc, "y_left_upper"),
                    get_int(py_img_loc, "width"),
                    get_int(py_img_loc, "height"),
                    get_float(py_img_loc, "confidence"),
                    get_properties(py_img_loc, "detection_properties")
            };
        }


        class ComponentApi {
        private:
            py::object image_job_ctor_;
            py::object video_job_ctor_;
            py::object audio_job_ctor_;
            py::object generic_job_ctor_;

            py::object image_location_ctor_;
            py::object video_track_ctor_;
            py::object audio_track_ctor_;
            py::object generic_track_ctor_;

            py::object detection_exception_ctor_;

        public:
            explicit ComponentApi()
                    : ComponentApi(py::module::import("mpf_component_api"))
            {
            }

            explicit ComponentApi(py::module &&component_api_module)
                    : image_job_ctor_(component_api_module.attr("ImageJob"))
                    , video_job_ctor_(component_api_module.attr("VideoJob"))
                    , audio_job_ctor_(component_api_module.attr("AudioJob"))
                    , generic_job_ctor_(component_api_module.attr("GenericJob"))
                    , image_location_ctor_(component_api_module.attr("ImageLocation"))
                    , video_track_ctor_(component_api_module.attr("VideoTrack"))
                    , audio_track_ctor_(component_api_module.attr("AudioTrack"))
                    , generic_track_ctor_(component_api_module.attr("GenericTrack"))
                    , detection_exception_ctor_(component_api_module.attr("DetectionException"))
            {

            }

            MPFDetectionError get_error_code(const py::error_already_set &ex) {
                if (py::isinstance(ex.value(), detection_exception_ctor_)) {
                    int error_code = ex.value().attr("error_code").cast<py::int_>();
                    return static_cast<MPFDetectionError>(error_code);
                }
                return MPFDetectionError::MPF_OTHER_DETECTION_ERROR_TYPE;
            }


            py::object to_python(const MPFImageLocation &img_loc) {
                return image_location_ctor_(
                        img_loc.x_left_upper,
                        img_loc.y_left_upper,
                        img_loc.width,
                        img_loc.height,
                        img_loc.confidence,
                        img_loc.detection_properties);
            }

            py::object to_python(const MPFImageJob &job) {
                py::object feed_forward_location = py::none();
                if (job.has_feed_forward_location) {
                    feed_forward_location = to_python(job.feed_forward_location);
                }

                return image_job_ctor_(
                        job.job_name,
                        job.data_uri,
                        job.job_properties,
                        job.media_properties,
                        feed_forward_location);
            }


            py::object to_python(const MPFVideoTrack &track) {
                py::dict frame_loc_map;
                py::object flm_set = frame_loc_map.attr("__setitem__");
                for (const auto &pair : track.frame_locations) {
                    flm_set(pair.first, to_python(pair.second));
                }

                return video_track_ctor_(
                        track.start_frame,
                        track.stop_frame,
                        track.confidence,
                        frame_loc_map,
                        track.detection_properties);
            }

            py::object to_python(const MPFVideoJob &job) {
                py::object feed_forward_track = py::none();
                if (job.has_feed_forward_track) {
                    feed_forward_track = to_python(job.feed_forward_track);
                }

                return video_job_ctor_(
                        job.job_name,
                        job.data_uri,
                        job.start_frame,
                        job.stop_frame,
                        job.job_properties,
                        job.media_properties,
                        feed_forward_track);
            }


            py::object to_python(const MPFAudioTrack &track) {
                return audio_track_ctor_(
                        track.start_time,
                        track.stop_time,
                        track.confidence,
                        track.detection_properties);

            }

            py::object to_python(const MPFAudioJob &job) {
                py::object feed_forward_track = py::none();
                if (job.has_feed_forward_track) {
                    feed_forward_track = to_python(job.feed_forward_track);
                }

                return audio_job_ctor_(
                        job.job_name,
                        job.data_uri,
                        job.start_time,
                        job.stop_time,
                        job.job_properties,
                        job.media_properties,
                        feed_forward_track);
            }



            py::object to_python(const MPFGenericTrack &track) {
                return generic_track_ctor_(
                        track.confidence,
                        track.detection_properties);
            }


            py::object to_python(const MPFGenericJob &job) {
                py::object feed_forward_track = py::none();
                if (job.has_feed_forward_track) {
                    feed_forward_track = to_python(job.feed_forward_track);
                }

                return generic_job_ctor_(
                        job.job_name,
                        job.data_uri,
                        job.job_properties,
                        job.media_properties,
                        feed_forward_track);
            }
        };


        struct ComponentAttrs {
            std::string detection_type;
            py::function get_detections_from_image_method;
            py::function get_detections_from_video_method;
            py::function get_detections_from_audio_method;
            py::function get_detections_from_generic_method;

            explicit ComponentAttrs(const std::string &module_path)
                : ComponentAttrs(load_component(module_path))
            {

            }

            explicit ComponentAttrs(py::object &&component_instance)
                : detection_type(GetDetectionType(component_instance))
                , get_detections_from_image_method(get_method(component_instance, "get_detections_from_image"))
                , get_detections_from_video_method(get_method(component_instance, "get_detections_from_video"))
                , get_detections_from_audio_method(get_method(component_instance, "get_detections_from_audio"))
                , get_detections_from_generic_method(get_method(component_instance, "get_detections_from_generic"))
            {
                if (get_detections_from_image_method.is_none()
                        && get_detections_from_video_method.is_none()
                        && get_detections_from_audio_method.is_none()
                        && get_detections_from_generic_method.is_none()) {
                    throw ComponentLoadError(
                            "The component does contain any of the component API methods. "
                            "Components must implement one or more of the following methods: "
                            "get_detections_from_image, get_detections_from_video, get_detections_from_audio, "
                            "or get_detections_from_generic.");
                }
            }

        private:
            static std::string GetDetectionType(py::handle obj) {
                if (!py::hasattr(obj, "detection_type")) {
                    throw ComponentLoadError("The Python component does not contain a field named \"detection_type\".");
                }
                return to_std_string(obj.attr("detection_type"));
            }
        };
    }


    class PythonComponentHandle::impl {
    private:
        log4cxx::LoggerPtr logger_;

        // The pybind11 library declares everything with hidden visibility.
        // When this class has fields that are pybind11 types, the compiler warns:
        // "'MPF::COMPONENT::PythonComponentHandle::impl' declared with greater visibility than the type of its field".
        // Putting any class that has fields that are pybind11 types in an anonymous namespace gets rid of
        // the compiler warning.
        ComponentApi component_api_;

        ComponentAttrs component_;

    public:
        impl(const log4cxx::LoggerPtr &logger, const std::string &lib_path)
            : logger_((
                  // Use comma operator so that initialize_python gets called before any fields are initialized.
                  initialize_python(), // result is discarded
                  logger))
            , component_api_()
            , component_(lib_path)
        {
        }

        bool Supports(MPFDetectionDataType data_type) {
            switch (data_type) {
                case UNKNOWN:
                    return !component_.get_detections_from_generic_method.is_none();
                case VIDEO:
                    return !component_.get_detections_from_video_method.is_none();
                case IMAGE:
                    return !component_.get_detections_from_image_method.is_none();
                case AUDIO:
                    return !component_.get_detections_from_audio_method.is_none();
                default:
                    return false;
            }
        }

        std::string GetDetectionType() {
            return component_.detection_type;
        }


        MPFDetectionError GetDetections(const MPFVideoJob &job, std::vector<MPFVideoTrack> &tracks) {
            try {
                if (component_.get_detections_from_video_method.is_none()) {
                    return MPF_UNSUPPORTED_DATA_TYPE;
                }

                py::object py_video_job = component_api_.to_python(job);

                py::iterable py_results = component_.get_detections_from_video_method(py_video_job);

                for (auto &py_track : py_results) {
                    tracks.emplace_back(
                            get_int(py_track, "start_frame"),
                            get_int(py_track, "stop_frame"),
                            get_float(py_track, "confidence"),
                            get_properties(py_track, "detection_properties"));
                    auto &frame_locations = tracks.back().frame_locations;

                    py::object iter_items = py_track.attr("frame_locations").attr("items")();
                    for (auto &pair : iter_items) {
                        auto std_pair = to_std_pair<py::int_, py::object>(pair);
                        frame_locations.emplace(std_pair.first, convert_image_location(std_pair.second));
                    }
                }

                return MPF_DETECTION_SUCCESS;
            }
            catch (...) {
                return HandleComponentException("get_detections_from_video");
            }
        }


        MPFDetectionError GetDetections(const MPFImageJob &job, std::vector<MPFImageLocation> &locations) {
            try {
                if (component_.get_detections_from_image_method.is_none()) {
                    return MPF_UNSUPPORTED_DATA_TYPE;
                }

                py::object py_image_job = component_api_.to_python(job);

                py::iterable py_results = component_.get_detections_from_image_method(py_image_job);

                for (auto &py_img_loc : py_results) {
                    locations.push_back(convert_image_location(py_img_loc));
                }

                return MPF_DETECTION_SUCCESS;
            }
            catch (...) {
                return HandleComponentException("get_detections_from_image");
            }
        }


        MPFDetectionError GetDetections(const MPFAudioJob &job, std::vector<MPFAudioTrack> &tracks) {
            try {
                if (component_.get_detections_from_audio_method.is_none()) {
                    return MPF_UNSUPPORTED_DATA_TYPE;
                }

                py::object py_audio_job = component_api_.to_python(job);

                py::iterable py_results = component_.get_detections_from_audio_method(py_audio_job);
                for (auto &py_track : py_results) {
                    tracks.emplace_back(
                        get_int(py_track, "start_time"),
                        get_int(py_track, "stop_time"),
                        get_float(py_track, "confidence"),
                        get_properties(py_track, "detection_properties"));
                }

                return MPF_DETECTION_SUCCESS;
            }
            catch (...) {
                return HandleComponentException("get_detections_from_audio");
            }
        }


        MPFDetectionError GetDetections(const MPFGenericJob &job, std::vector<MPFGenericTrack> &tracks) {
            try {
                if (component_.get_detections_from_generic_method.is_none()) {
                    return MPF_UNSUPPORTED_DATA_TYPE;
                }

                py::object py_generic_job = component_api_.to_python(job);

                py::iterable py_results = component_.get_detections_from_generic_method(py_generic_job);
                for (auto &py_track : py_results) {
                    tracks.emplace_back(
                            get_float(py_track, "confidence"),
                            get_properties(py_track, "detection_properties"));
                }

                return MPF_DETECTION_SUCCESS;
            }
            catch (...) {
                return HandleComponentException("get_detections_from_generic");
            }
        }


    private:

        MPFDetectionError HandleComponentException(const std::string &component_method) {
            try {
                throw;
            }
            catch (const py::error_already_set &ex) {
                LOG4CXX_ERROR(logger_, "An error occurred while invoking the \""
                        << component_method << "\" method on the Python component: " << ex.what());
                return component_api_.get_error_code(ex);
            }
            catch (const std::exception &ex) {
                LOG4CXX_ERROR(logger_, "An error occurred while invoking the \""
                        << component_method << "\" method on the Python component: " << ex.what());
                return MPF_OTHER_DETECTION_ERROR_TYPE;
            }
            catch (...) {
                LOG4CXX_ERROR(logger_, "An error occurred while invoking the \""
                        << component_method << "\" method on the Python component.");
                return MPF_OTHER_DETECTION_ERROR_TYPE;
            }
        }
    };





    PythonComponentHandle::PythonComponentHandle(const log4cxx::LoggerPtr &logger, const std::string &lib_path)
            : impl_(new impl(logger, lib_path))
    {
    }

    // Can't be defaulted in header because in the header PythonComponentHandle::impl is still an incomplete type.
    PythonComponentHandle::~PythonComponentHandle() = default;

    PythonComponentHandle::PythonComponentHandle(PythonComponentHandle &&other) noexcept = default;

    PythonComponentHandle& PythonComponentHandle::operator=(PythonComponentHandle &&other) noexcept = default;


    void PythonComponentHandle::SetRunDirectory(const std::string &run_dir) {
        // This is unnecessary for Python components because they can just use __file__
    }


    bool PythonComponentHandle::Init() {
        return true;
    }


    std::string PythonComponentHandle::GetDetectionType() {
        return impl_->GetDetectionType();
    }


    bool PythonComponentHandle::Supports(MPFDetectionDataType data_type) {
        return impl_->Supports(data_type);
    }


    MPFDetectionError PythonComponentHandle::GetDetections(const MPFVideoJob &job,
                                                           std::vector<MPFVideoTrack> &tracks) {
        return impl_->GetDetections(job, tracks);
    }


    MPFDetectionError PythonComponentHandle::GetDetections(const MPFImageJob &job,
                                                           std::vector<MPFImageLocation> &locations) {
        return impl_->GetDetections(job, locations);
    }


    MPFDetectionError PythonComponentHandle::GetDetections(const MPFAudioJob &job,
                                                           std::vector<MPFAudioTrack> &tracks) {
        return impl_->GetDetections(job, tracks);
    }


    MPFDetectionError PythonComponentHandle::GetDetections(const MPFGenericJob &job,
                                                           std::vector<MPFGenericTrack> &tracks) {
        return impl_->GetDetections(job, tracks);
    }

    bool PythonComponentHandle::Close() {
        return true;
    }
}}