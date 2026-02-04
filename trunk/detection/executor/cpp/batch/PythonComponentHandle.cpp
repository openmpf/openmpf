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

#include <pybind11/embed.h>
#include <pybind11/stl.h>

#include <MPFDetectionException.h>

#include "BatchExecutorUtil.h"
#include "ComponentLoadError.h"

#include "PythonComponentHandle.h"

namespace py = pybind11;


namespace MPF::COMPONENT {

    namespace {
        py::object get_builtin(const char *name) {
            return py::module_::import("builtins").attr(name);
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
            py::module_::import("sys")
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

            py::module_ component_module;
            try {
                component_module = py::module_::import(module_name.c_str());
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
          Example entry point declaration in a component's setup.cfg:
          [options.entry_points]
          mpf.exported_component =
              component = my_module:MyComponentClass
         */
        py::object load_component_from_package(const std::string &distribution_name) {
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
            catch (const py::error_already_set& ex) {
                if (!ex.matches(import_meta.attr("PackageNotFoundError"))) {
                    throw;
                }
            }

            try {
                return load_component_from_module(distribution_name, distribution_name);
            }
            catch (const ComponentLoadError &ex) {
                throw ComponentLoadError(
                    "The \"" + distribution_name + "\" component did not declare an "
                    "\"mpf.exported_component\" entry point so an attempt was made to load "
                    "the component from a module named \"" + distribution_name + "\", "
                    "but that also failed due to: " + ex.what());
            }
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

        std::optional<py::function> get_method(
                py::handle instance,
                const std::string& method_name) {
            py::object method = py::getattr(instance, method_name.c_str(), py::none());
            if (method.is_none()) {
                return {};
            }
            if (is_callable(method)) {
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
            for (const auto &pair : iter_items) {
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
            py::object all_video_tracks_job_ctor_;
            py::object audio_job_ctor_;
            py::object all_audio_tracks_job_ctor_;
            py::object generic_job_ctor_;

            py::object image_location_ctor_;
            py::object video_track_ctor_;
            py::object audio_track_ctor_;
            py::object generic_track_ctor_;

            py::object detection_exception_ctor_;

        public:
            explicit ComponentApi()
                    : ComponentApi(py::module_::import("mpf_component_api"))
            {
            }

            explicit ComponentApi(py::module_ &&component_api_module)
                    : image_job_ctor_(component_api_module.attr("ImageJob"))
                    , video_job_ctor_(component_api_module.attr("VideoJob"))
                    , all_video_tracks_job_ctor_(component_api_module.attr("AllVideoTracksJob"))
                    , audio_job_ctor_(component_api_module.attr("AudioJob"))
                    , all_audio_tracks_job_ctor_(component_api_module.attr("AllAudioTracksJob"))
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

            py::object to_python(const MPFAllVideoTracksJob &job) {
                py::list feed_forward_tracks;
                if (job.has_feed_forward_tracks) {
                    for (const auto& feed_forward_track : job.feed_forward_tracks) {
                        feed_forward_tracks.append(to_python(feed_forward_track));
                    }
                }

                return all_video_tracks_job_ctor_(
                        job.job_name,
                        job.data_uri,
                        job.start_frame,
                        job.stop_frame,
                        job.job_properties,
                        job.media_properties,
                        feed_forward_tracks);
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


            py::object to_python(const MPFAllAudioTracksJob &job) {
                py::list feed_forward_tracks;
                if (job.has_feed_forward_tracks) {
                    for (const auto& feed_forward_track : job.feed_forward_tracks) {
                        feed_forward_tracks.append(to_python(feed_forward_track));
                    }
                }

                return all_audio_tracks_job_ctor_(
                        job.job_name,
                        job.data_uri,
                        job.start_time,
                        job.stop_time,
                        job.job_properties,
                        job.media_properties,
                        feed_forward_tracks);
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
            std::optional<py::function> get_detections_from_image_method;
            std::optional<py::function> get_detections_from_video_method;
            std::optional<py::function> get_detections_from_all_video_tracks_method;
            std::optional<py::function> get_detections_from_audio_method;
            std::optional<py::function> get_detections_from_all_audio_tracks_method;
            std::optional<py::function> get_detections_from_generic_method;

            explicit ComponentAttrs(const std::string &module_path)
                : ComponentAttrs(load_component(module_path))
            {

            }

            explicit ComponentAttrs(py::object &&component_instance)
                : get_detections_from_image_method(get_method(component_instance, "get_detections_from_image"))
                , get_detections_from_video_method(get_method(component_instance, "get_detections_from_video"))
                , get_detections_from_all_video_tracks_method(get_method(component_instance, "get_detections_from_all_video_tracks"))
                , get_detections_from_audio_method(get_method(component_instance, "get_detections_from_audio"))
                , get_detections_from_all_audio_tracks_method(get_method(component_instance, "get_detections_from_all_audio_tracks"))
                , get_detections_from_generic_method(get_method(component_instance, "get_detections_from_generic"))
            {
                if (!get_detections_from_image_method
                        && !get_detections_from_video_method
                        && !get_detections_from_all_video_tracks_method
                        && !get_detections_from_audio_method
                        && !get_detections_from_all_audio_tracks_method
                        && !get_detections_from_generic_method) {
                    throw ComponentLoadError(
                            "The component doesn't contain any of the component API methods. "
                            "Components must implement one or more of the following methods: "
                            "get_detections_from_image, get_detections_from_video, "
                            "get_detections_from_all_video_tracks, get_detections_from_audio, "
                            "get_detections_from_all_audio_tracks, or get_detections_from_generic.");
                }
            }
        };

        struct LoggerAttrs {
            py::function debug;
            py::function info;
            py::function warn;
            py::function error;
            py::function fatal;

            explicit LoggerAttrs(py::handle logger)
                : debug(logger.attr("debug"))
                , info(logger.attr("info"))
                , warn(logger.attr("warning"))
                , error(logger.attr("error"))
                , fatal(logger.attr("fatal"))
            {
            }
        };

        // When logging from C++ filename gets set to "(unknown file)".
        // This replaces "(unknown file)" with the logger name.
        class LogRecordFactory {
        public:
            explicit LogRecordFactory(std::shared_ptr<std::string> job_name_log_prefix_ptr)
                : job_name_log_prefix_ptr_(std::move(job_name_log_prefix_ptr)) {
            }

            py::object operator()(const py::args &args, const py::kwargs &kwargs) {
                py::object record = log_record_cls_(*args, **kwargs);
                py::str file_name = record.attr("filename");
                if (file_name.equal(unknown_file_str_)) {
                    record.attr("filename") = record.attr("name");
                }
                if (!job_name_log_prefix_ptr_->empty()) {
                    record.attr("msg") = py::str(*job_name_log_prefix_ptr_) + record.attr("msg");
                }
                return record;
            }

        private:
            py::object log_record_cls_ = py::module_::import("logging").attr("LogRecord");
            py::str unknown_file_str_ = "(unknown file)";
            std::shared_ptr<std::string> job_name_log_prefix_ptr_;
        };
    } // end anonymous namespace


    class PythonComponentHandle::impl {
    private:
        LoggerWrapper logger_;

        // The pybind11 library declares everything with hidden visibility.
        // When this class has fields that are pybind11 types, the compiler warns:
        // "'MPF::COMPONENT::PythonComponentHandle::impl' declared with greater visibility than the type of its field".
        // Putting any class that has fields that are pybind11 types in an anonymous namespace gets rid of
        // the compiler warning.
        ComponentApi component_api_;

        ComponentAttrs component_;

    public:
        impl(LoggerWrapper logger, const std::string &lib_path)
            : logger_(std::move(logger))
            , component_(lib_path)
        {
        }

        bool Supports(MPFDetectionDataType data_type) const {
            switch (data_type) {
                case UNKNOWN:
                    return component_.get_detections_from_generic_method.has_value();
                case VIDEO:
                    return component_.get_detections_from_video_method.has_value()
                           || component_.get_detections_from_all_video_tracks_method.has_value();
                case IMAGE:
                    return component_.get_detections_from_image_method.has_value();
                case AUDIO:
                    return component_.get_detections_from_audio_method.has_value()
                           || component_.get_detections_from_all_audio_tracks_method.has_value();
                default:
                    return false;
            }
        }

        std::vector<MPFVideoTrack> GetDetections(const MPFVideoJob &job) {
            try {
                if (!component_.get_detections_from_video_method.has_value()) {
                    throw MPFDetectionException(MPF_UNSUPPORTED_DATA_TYPE,
                                                "Unsupported data type.");
                }

                py::object py_video_job = component_api_.to_python(job);

                py::iterable py_results
                        = (*component_.get_detections_from_video_method)(py_video_job);

                return ToVideoTracks(py_results);
            }
            catch (...) {
                HandleComponentException("get_detections_from_video");
            }
        }


        std::vector<MPFVideoTrack> GetDetections(const MPFAllVideoTracksJob &job) {
            try {
                if (!component_.get_detections_from_all_video_tracks_method.has_value()) {
                    throw MPFDetectionException(MPF_UNSUPPORTED_DATA_TYPE,
                                                "Unsupported data type.");
                }

                py::object py_video_job = component_api_.to_python(job);

                py::iterable py_results
                        = (*component_.get_detections_from_all_video_tracks_method)(py_video_job);

                return ToVideoTracks(py_results);
            }
            catch (...) {
                HandleComponentException("get_detections_from_all_video_tracks");
            }
        }


        std::vector<MPFImageLocation> GetDetections(const MPFImageJob &job) {
            try {
                if (!component_.get_detections_from_image_method.has_value()) {
                    throw MPFDetectionException(MPF_UNSUPPORTED_DATA_TYPE,
                                                "Unsupported data type.");
                }

                py::object py_image_job = component_api_.to_python(job);

                py::iterable py_results
                        = (*component_.get_detections_from_image_method)(py_image_job);

                std::vector<MPFImageLocation> locations;
                for (const auto &py_img_loc : py_results) {
                    locations.push_back(convert_image_location(py_img_loc));
                }

                return locations;
            }
            catch (...) {
                HandleComponentException("get_detections_from_image");
            }
        }


        std::vector<MPFAudioTrack> GetDetections(const MPFAudioJob &job) {
            try {
                if (!component_.get_detections_from_audio_method.has_value()) {
                    throw MPFDetectionException(MPF_UNSUPPORTED_DATA_TYPE,
                                                "Unsupported data type.");
                }

                py::object py_audio_job = component_api_.to_python(job);

                py::iterable py_results
                        = (*component_.get_detections_from_audio_method)(py_audio_job);

                return ToAudioTracks(py_results);
            }
            catch (...) {
                HandleComponentException("get_detections_from_audio");
            }
        }

        std::vector<MPFAudioTrack> GetDetections(const MPFAllAudioTracksJob &job) {
            try {
                if (!component_.get_detections_from_all_audio_tracks_method.has_value()) {
                    throw MPFDetectionException(MPF_UNSUPPORTED_DATA_TYPE,
                                                "Unsupported data type.");
                }

                py::object py_audio_job = component_api_.to_python(job);

                py::iterable py_results
                        = (*component_.get_detections_from_audio_method)(py_audio_job);

                return ToAudioTracks(py_results);
            }
            catch (...) {
                HandleComponentException("get_detections_from_all_audio_tracks");
            }
        }


        std::vector<MPFGenericTrack> GetDetections(const MPFGenericJob &job) {
            try {
                if (!component_.get_detections_from_generic_method.has_value()) {
                    throw MPFDetectionException(MPF_UNSUPPORTED_DATA_TYPE,
                                                "Unsupported data type.");
                }

                py::object py_generic_job = component_api_.to_python(job);

                py::iterable py_results
                        = (*component_.get_detections_from_generic_method)(py_generic_job);
                std::vector<MPFGenericTrack> tracks;
                for (const auto &py_track : py_results) {
                    tracks.emplace_back(
                            get_float(py_track, "confidence"),
                            get_properties(py_track, "detection_properties"));
                }

                return tracks;
            }
            catch (...) {
                HandleComponentException("get_detections_from_generic");
            }
        }


    private:

        std::vector<MPFVideoTrack> ToVideoTracks(const py::iterable &py_results) {
            std::vector<MPFVideoTrack> tracks;
            for (const auto &py_track : py_results) {
                tracks.emplace_back(
                        get_int(py_track, "start_frame"),
                        get_int(py_track, "stop_frame"),
                        get_float(py_track, "confidence"),
                        get_properties(py_track, "detection_properties"));
                auto &frame_locations = tracks.back().frame_locations;

                py::object iter_items = py_track.attr("frame_locations").attr("items")();
                for (const auto &pair : iter_items) {
                    auto std_pair = to_std_pair<py::int_, py::object>(pair);
                    frame_locations.emplace(std_pair.first, convert_image_location(std_pair.second));
                }
            }
            return tracks;
        }

        std::vector<MPFAudioTrack> ToAudioTracks(const py::iterable &py_results) {
            std::vector<MPFAudioTrack> tracks;
            for (const auto &py_track : py_results) {
                tracks.emplace_back(
                    get_int(py_track, "start_time"),
                    get_int(py_track, "stop_time"),
                    get_float(py_track, "confidence"),
                    get_properties(py_track, "detection_properties"));
            }
            return tracks;
        }

        [[noreturn]] void HandleComponentException(const std::string &component_method) {
            std::string base_message = "An error occurred while invoking the \"" + component_method
                    + "\" method on the Python component";
            try {
                throw;
            }
            catch (const MPFDetectionException &ex) {
                std::string message = base_message + ": " + ex.what();
                logger_.Error(message);
                throw MPFDetectionException(ex.error_code, message);
            }
            catch (const py::error_already_set &ex) {
                // ex.what() includes the stack trace so we log ex.what(),
                // but throw with to_std_string(ex.value()) so the stack trace
                // doesn't appear in the error message sent to Workflow Manager.
                logger_.Error(base_message, ": ", ex.what());
                throw MPFDetectionException(
                        component_api_.get_error_code(ex),
                        base_message + ": " + to_std_string(ex.value()));
            }
            catch (const std::exception &ex) {
                std::string message = base_message + ": " + ex.what();
                logger_.Error(message);
                throw MPFDetectionException(MPF_OTHER_DETECTION_ERROR_TYPE, message);
            }
            catch (...) {
                logger_.Error(base_message);
                throw MPFDetectionException(MPF_OTHER_DETECTION_ERROR_TYPE, base_message);
            }
        }
    }; //  class PythonComponentHandle::impl

    PythonComponentHandle::PythonComponentHandle(const LoggerWrapper &logger,
                                                 const std::string &lib_path) {
        initialize_python();
        impl_ = std::make_unique<impl>(logger, lib_path);
    }

    // Can't be defaulted in header because in the header PythonComponentHandle::impl is still an incomplete type.
    PythonComponentHandle::~PythonComponentHandle() = default;


    void PythonComponentHandle::SetRunDirectory(const std::string &run_dir) {
        // This is unnecessary for Python components because they can just use __file__
    }


    bool PythonComponentHandle::Init() {
        return true;
    }


    bool PythonComponentHandle::Supports(MPFDetectionDataType data_type) {
        return impl_->Supports(data_type);
    }


    std::vector<MPFVideoTrack> PythonComponentHandle::GetDetections(const MPFVideoJob &job) {
        return impl_->GetDetections(job);
    }

    std::vector<MPFVideoTrack> PythonComponentHandle::GetDetections(const MPFAllVideoTracksJob &job) {
        return impl_->GetDetections(job);
    }


    std::vector<MPFImageLocation> PythonComponentHandle::GetDetections(const MPFImageJob &job) {
        return impl_->GetDetections(job);
    }


    std::vector<MPFAudioTrack> PythonComponentHandle::GetDetections(const MPFAudioJob &job) {
        return impl_->GetDetections(job);
    }

    std::vector<MPFAudioTrack> PythonComponentHandle::GetDetections(const MPFAllAudioTracksJob &job) {
        return impl_->GetDetections(job);
    }

    std::vector<MPFGenericTrack> PythonComponentHandle::GetDetections(const MPFGenericJob &job) {
        return impl_->GetDetections(job);
    }

    bool PythonComponentHandle::Close() {
        return true;
    }


    class PythonLogger::logger_impl {
    public:
        LoggerAttrs loggerAttrs {
            py::module_::import("logging").attr("getLogger")("org.mitre.mpf.detection") };
    };


    PythonLogger::PythonLogger(const std::string &log_level, const std::string &component_name) {
        initialize_python();
        ConfigureLogging(log_level, component_name, job_name_log_prefix_ptr_);
        impl_ = std::make_unique<logger_impl>();
    }

    PythonLogger::~PythonLogger() = default;


    void PythonLogger::Debug(std::string_view message) {
        impl_->loggerAttrs.debug(message);
    }

    void PythonLogger::Info(std::string_view message) {
        impl_->loggerAttrs.info(message);
    }

    void PythonLogger::Warn(std::string_view message) {
        impl_->loggerAttrs.warn(message);
    }

    void PythonLogger::Error(std::string_view message) {
        impl_->loggerAttrs.error(message);
    }

    void PythonLogger::Fatal(std::string_view message) {
        impl_->loggerAttrs.fatal(message);
    }

    void PythonLogger::SetJobName(std::string_view job_name) {
        *job_name_log_prefix_ptr_ = job_name;
    }

    void PythonLogger::ConfigureLogging(const std::string &log_level_name,
                                        const std::string &component_name,
                                        std::shared_ptr<std::string> job_name_log_prefix_ptr) {
        static bool initialized = false;
        if (initialized) {
            return;
        }
        initialized = true;

        auto logging_module = py::module_::import("logging");
        // Change default level names to match what WFM expects
        // Change default level name for logger.warn and logger.warning from 'WARNING' to 'WARN'
        logging_module.attr("addLevelName")(logging_module.attr("WARN"), "WARN");
        //Change default level name for logger.fatal and logger.critical from 'CRITICAL' to 'FATAL'
        logging_module.attr("addLevelName")(logging_module.attr("FATAL"), "FATAL");

        py::list handlers;

        py::object sys_stderr = py::module_::import("sys").attr("stderr");
        py::object stream_handler = logging_module.attr("StreamHandler")(sys_stderr);
        handlers.append(stream_handler);

        std::string log_file_path = GetLogFilePath(component_name);
        if (!log_file_path.empty()) {
            py::object timed_rotating_file_handler_cls
                    = py::module_::import("logging.handlers").attr("TimedRotatingFileHandler");

            py::object file_handler = timed_rotating_file_handler_cls(
                    log_file_path, py::arg("when")="midnight", py::arg("delay")=true);
            handlers.append(file_handler);
        }

        py::str py_log_level = log_level_name == "TRACE"
                ? "NOTSET"  // Python doesn't use TRACE so we just log everything when TRACE is provided
                : log_level_name;

        logging_module.attr("basicConfig")(
            py::arg("format")="%(asctime)s %(levelname)-5s [%(filename)s:%(lineno)d] - %(message)s",
            py::arg("level")=py_log_level,
            py::arg("handlers")=handlers);

        if (log_file_path.empty()) {
            py::object logger = logging_module.attr("getLogger")("org.mitre.mpf.detection");
            logger.attr("error")(
                    "Unable to determine full path to log file because the $MPF_LOG_PATH and/or "
                    "$THIS_MPF_NODE environment variables were not set. Log messages will only be "
                    "sent to standard error.");
        }

        logging_module.attr("setLogRecordFactory")(py::cpp_function(
                LogRecordFactory(std::move(job_name_log_prefix_ptr))));
    }


    std::string PythonLogger::GetLogFilePath(const std::string &component_name) {
        auto log_path_env_val = BatchExecutorUtil::GetEnv("MPF_LOG_PATH");
        if (!log_path_env_val) {
            return "";
        }

        auto this_node_env_val = BatchExecutorUtil::GetEnv("THIS_MPF_NODE");
        if (!this_node_env_val) {
            return "";
        }

        std::string log_path = *log_path_env_val;
        if (log_path.back() != '/') {
            log_path += '/';
        }

        std::string log_dir = log_path + *this_node_env_val + "/log";
        py::module_::import("os").attr("makedirs")(log_dir, py::arg("exist_ok")=true);

        return log_dir + '/' + component_name + ".log";
    }
}
