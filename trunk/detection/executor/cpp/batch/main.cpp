/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

#include <libgen.h>
#include <unistd.h>

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <exception>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include <log4cxx/logger.h>
#include <log4cxx/logmanager.h>
#include <log4cxx/xml/domconfigurator.h>

#include <MPFDetectionComponent.h>
#include <MPFDetectionException.h>

#include "ComponentLoadError.h"
#include "CppComponentHandle.h"
#include "MPFDetectionBuffer.h"
#include "MPFMessenger.h"
#include "PythonComponentHandle.h"
#include "BatchExecutorUtil.h"


using std::exception;
using std::string;
using std::map;
using std::vector;

using namespace MPF;
using namespace COMPONENT;


bool is_python(log4cxx::LoggerPtr &logger, int argc, char* argv[]);

template <typename ComponentHandle>
int run_jobs(log4cxx::LoggerPtr &logger, const std::string &broker_uri, const std::string &request_queue,
             const std::string &app_dir, ComponentHandle &detection_engine);

std::string get_app_dir(const char * argv0);

/**
 * This is the main program for the Detection Component.  It accepts two
 * command line arguments: 1) the track request queue name, and 2) a log
 * file name; default values are used if no arguments are supplied. Once
 * started, it remains in an endless loop in which it queries for and handles
 * DetectionRequest messages until the user enters 'q<return>'.
 * @param argc An integer containing the number of command line arguments
 * @param argv A character array containing the command line arguments in
 * string form
 * @return An integer representing the exit status of the program
 */
int main(int argc, char* argv[]) {
    // Determine the directory containing this executable
    // since the config and log files are found relative to that path.
    string app_dir = get_app_dir(argv[0]);

    log4cxx::xml::DOMConfigurator::configure(app_dir + "/../config/Log4cxxConfig.xml");
    log4cxx::LoggerPtr logger = log4cxx::Logger::getLogger("org.mitre.mpf.detection");

    if (argc < 4) {
        LOG4CXX_ERROR(logger,
                      "ERROR: the activemq broker uri, component library name, and request queue name must be supplied as arguments");
        return -1;
    }

    string broker_uri = argv[1];
    string lib_path = argv[2];
    string request_queue = argv[3];

    LOG4CXX_DEBUG(logger, "broker uri = " << argv[1]);
    LOG4CXX_DEBUG(logger, "library name = " << argv[2]);
    LOG4CXX_DEBUG(logger, "request queue = " << argv[3]);


    try {
        if (is_python(logger, argc, argv)) {
            PythonComponentHandle component_handle(logger, lib_path);
            return run_jobs(logger, broker_uri, request_queue, app_dir, component_handle);
        }
        else {
            CppComponentHandle component_handle(lib_path);
            return run_jobs(logger, broker_uri, request_queue, app_dir, component_handle);
        }
    }
    catch (const ComponentLoadError &ex) {
        LOG4CXX_ERROR(logger, "An error occurred while trying to load component: " << ex.what());
        return 38;
    }
    catch (const std::exception &ex) {
        LOG4CXX_ERROR(logger, "An error occurred while running the job: " << ex.what());
        return -1;
    }
    catch (...) {
        LOG4CXX_ERROR(logger, "An error occurred while running the job.");
        return -1;
    }
}

std::string get_app_dir(const char * const argv0) {
    std::unique_ptr<char, decltype(&std::free)> this_exe(canonicalize_file_name("/proc/self/exe"), std::free);
    if (this_exe != nullptr) {
        // The dirname documentation says the returned pointer must not be freed.
        std::string app_dir = dirname(this_exe.get());
        if (!app_dir.empty()) {
            return app_dir;
        }
    }

    std::unique_ptr<char[]> argv0_copy(new char[strlen(argv0) + 1]);
    std::strcpy(argv0_copy.get(), argv0);
    std::string app_dir = dirname(argv0_copy.get());
    if (!app_dir.empty()) {
        return app_dir;
    }

    std::unique_ptr<char, decltype(&std::free)> cwd(get_current_dir_name(), std::free);
    if (cwd != nullptr) {
        return std::string(cwd.get());
    }

    return ".";
}

std::string get_extension(const std::string &path) {
    size_t last_slash_pos = path.rfind('/');
    if (last_slash_pos == std::string::npos) {
        last_slash_pos = 0;
    }
    size_t dot_pos = path.find('.', last_slash_pos);
    if (dot_pos == std::string::npos) {
        return "";
    }
    return path.substr(dot_pos + 1);
}


bool is_python(log4cxx::LoggerPtr &logger, int argc, char* argv[]) {
    if (argc > 4) {
        std::string provided_language = argv[4];
        std::transform(provided_language.begin(), provided_language.end(), provided_language.begin(),
                       static_cast<int(*)(int)>(std::tolower));

        if (std::string("python") == provided_language) {
            return true;
        }
        if (std::string("c++") == provided_language) {
            return false;
        }
        LOG4CXX_WARN(logger, "Expected the fifth command line argument to either be \"c++\" or \"python\", but \""
                << argv[4] << "\" was provided.")
    }
    else {
        LOG4CXX_WARN(logger, "Expected the fifth command line argument to either be \"c++\" or \"python\", "
                "but no value was provided.");
    }

    std::string lib_extension = get_extension(argv[2]);
    std::transform(lib_extension.begin(), lib_extension.end(), lib_extension.begin(),
                   static_cast<int(*)(int)>(std::tolower));

    if (lib_extension.find("so") == std::string::npos) {
        LOG4CXX_WARN(logger, "Assuming \"" << argv[2]
                           << "\" is a Python component because it does not have the .so extension.");
        return true;
    }
    else {
        LOG4CXX_WARN(logger, "Assuming \"" << argv[2]
                           << "\" is a C++ component because it has the .so extension.");
        return false;
    }
}


void handle_component_exception(std::string& error_message, MPFDetectionError &error_code) {
    try {
        throw;
    }
    catch (const MPFDetectionException &ex) {
        error_message = ex.what();
        error_code = ex.error_code;
    }
    catch (std::exception &ex) {
        error_message = ex.what();
        error_code = MPF_OTHER_DETECTION_ERROR_TYPE;
    }
}

string get_file_name(const string& s) {
    size_t i = s.rfind('/', s.length());
    if (i != std::string::npos) {
        return s.substr(i+1, s.length()-i);
    }
    return s;
}


template <typename ComponentHandle>
int run_jobs(log4cxx::LoggerPtr &logger, const std::string &broker_uri, const std::string &request_queue,
            const std::string &app_dir, ComponentHandle &detection_engine) {
    int pollingInterval = 1;

    // Remain in loop handling job request messages
    // until 'q\n' is received on stdin
    bool error_occurred = false;
    try {

        int nfds = 0;
        int bytes_read = 0;
        int input_buf_size = 2;
        char input_buf[input_buf_size];
        string quit_string("q\n");
        fd_set readfds;
        struct timeval tv;
        bool keep_running = true;

        // Set timeout on check for 'q\n' to 5 seconds
        tv.tv_sec = 5;
        tv.tv_usec = 0;

        MPFMessenger messenger(logger, broker_uri, request_queue);

        detection_engine.SetRunDirectory(app_dir + "/../plugins");

        if (!detection_engine.Init()) {
            LOG4CXX_ERROR(logger, "Detection component initialization failed, exiting.");
            return -1;
        }

        const std::map<std::string, std::string> env_job_props
                = BatchExecutorUtil::get_environment_job_properties();

        string service_name(getenv("SERVICE_NAME"));
        LOG4CXX_INFO(logger, "Completed initialization of " << service_name << ".");

        bool gotMessageOnLastPull = false;
        while (keep_running) {
            //	Sleep for pollingInterval seconds between polls.
            if (gotMessageOnLastPull == false) {
                sleep(pollingInterval);
            }
            gotMessageOnLastPull = false;

            // Receive job request
            MPFMessageMetadata msg_metadata;
            std::vector<unsigned char> request_contents = messenger.ReceiveMessage(msg_metadata);

            if (!request_contents.empty()) {
                //  Set not to sleep flag.
                gotMessageOnLastPull = true;

                std::stringstream job_name; // Empty; need to unpack detection request to determine name

                MPFDetectionBuffer detection_buf(request_contents);

                std::vector<unsigned char> detection_response_body;

                detection_buf.GetMessageMetadata(&msg_metadata);
                MPFDetectionDataType data_type = detection_buf.GetDataType();
                string data_uri = detection_buf.GetDataUri();

                map<string, string> algorithm_properties;
                detection_buf.GetAlgorithmProperties(algorithm_properties);
                for (auto env_prop_pair : env_job_props) {
                    algorithm_properties[env_prop_pair.first] = env_prop_pair.second;
                }

                map<string, string> media_properties;
                detection_buf.GetMediaProperties(media_properties);


                MPFDetectionVideoRequest video_request;
                MPFDetectionAudioRequest audio_request;
                MPFDetectionImageRequest image_request;
                MPFDetectionGenericRequest generic_request;

                job_name << "Job " << msg_metadata.job_id << ":" << get_file_name(data_uri);

                if (data_type == MPFDetectionDataType::VIDEO) {
                    detection_buf.GetVideoRequest(video_request);

                    // Identify segment
                    job_name << "(";
                    job_name << video_request.start_frame;
                    job_name << "-";
                    job_name << video_request.stop_frame;
                    job_name << ")";

                } else if (data_type == MPFDetectionDataType::AUDIO) {
                    detection_buf.GetAudioRequest(audio_request);

                    // Identify segment
                    job_name << "(";
                    job_name << audio_request.start_time;
                    job_name << "-";
                    job_name << audio_request.stop_time;
                    job_name << ")";

                } else if (data_type == MPFDetectionDataType::IMAGE) {
                    detection_buf.GetImageRequest(image_request);
                } else {
                    detection_buf.GetGenericRequest(generic_request);
                }

                LOG4CXX_INFO(logger, "[" << job_name.str() << "] Processing message on " << service_name << ".");

                string detection_type = detection_engine.GetDetectionType();

                if (detection_engine.Supports(data_type)) {
                    MPFDetectionError rc = MPF_DETECTION_SUCCESS;
                    std::string error_message;

                    if (data_type == MPFDetectionDataType::VIDEO) {
                        vector <MPFVideoTrack> tracks;

                        if (video_request.has_feed_forward_track) {
                            // Invoke the detection component with
                            // a feed-forward track
                            LOG4CXX_INFO(logger, "[" << job_name.str() << "] Processing feed-forward track on " << service_name << ".");
                            MPFVideoJob video_job(job_name.str(),
                                                  data_uri,
                                                  video_request.start_frame,
                                                  video_request.stop_frame,
                                                  video_request.feed_forward_track,
                                                  algorithm_properties,
                                                  media_properties);
                            try {
                                tracks = detection_engine.GetDetections(video_job);
                            }
                            catch (...) {
                                handle_component_exception(error_message, rc);
                            }
                        }
                        else {
                            // Invoke the detection component
                            // without a feed-forward track
                            MPFVideoJob video_job(job_name.str(),
                                                  data_uri,
                                                  video_request.start_frame,
                                                  video_request.stop_frame,
                                                  algorithm_properties,
                                                  media_properties);

                            try {
                                tracks = detection_engine.GetDetections(video_job);
                            }
                            catch (...) {
                                handle_component_exception(error_message, rc);
                            }
                        }

                        if (rc != MPF_DETECTION_SUCCESS) {
                            LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Video detection method returned an error for " << data_uri);
                        }

                        // Pack video response
                        detection_response_body = detection_buf.PackVideoResponse(
                                tracks, msg_metadata, data_type,
                                video_request.start_frame, video_request.stop_frame,
                                detection_type, rc, error_message);

                    } else if (data_type == MPFDetectionDataType::AUDIO) {
                        vector <MPFAudioTrack> tracks;
                        if (audio_request.has_feed_forward_track) {
                            // Invoke the detection component with
                            // a feed-forward track
                            LOG4CXX_INFO(logger, "[" << job_name.str() << "] Processing feed-forward track on " << service_name << ".");
                            MPFAudioJob audio_job(job_name.str(),
                                                  data_uri,
                                                  audio_request.start_time,
                                                  audio_request.stop_time,
                                                  audio_request.feed_forward_track,
                                                  algorithm_properties,
                                                  media_properties);

                            try {
                                tracks = detection_engine.GetDetections(audio_job);
                            }
                            catch (...) {
                                handle_component_exception(error_message, rc);
                            }
                        }
                        else {
                            // Invoke the detection component
                            // without a feed-forward track
                            MPFAudioJob audio_job(job_name.str(),
                                                  data_uri,
                                                  audio_request.start_time,
                                                  audio_request.stop_time,
                                                  algorithm_properties,
                                                  media_properties);

                            try {
                                tracks = detection_engine.GetDetections(audio_job);
                            }
                            catch (...) {
                                handle_component_exception(error_message, rc);
                            }
                        }

                        if (rc != MPF_DETECTION_SUCCESS) {
                            LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Audio detection method returned an error for " << data_uri);
                        }

                        // Pack audio response
                        detection_response_body = detection_buf.PackAudioResponse(
                                tracks, msg_metadata, data_type,
                                audio_request.start_time, audio_request.stop_time,
                                detection_type, rc, error_message);

                    } else if (data_type == MPFDetectionDataType::IMAGE) {
                        vector <MPFImageLocation> locations;
                        if (image_request.has_feed_forward_location) {
                            // Invoke the detection component with
                            // a feed-forward location
                            LOG4CXX_INFO(logger, "[" << job_name.str() << "] Processing feed-forward location on " << service_name << ".");
                            MPFImageJob image_job(job_name.str(),
                                                  data_uri,
                                                  image_request.feed_forward_location,
                                                  algorithm_properties,
                                                  media_properties);

                            try {
                                locations = detection_engine.GetDetections(image_job);
                            }
                            catch (...) {
                                handle_component_exception(error_message, rc);
                            }
                        }
                        else {
                            // Invoke the detection component
                            // without a feed-forward location
                            MPFImageJob image_job(job_name.str(),
                                                  data_uri,
                                                  algorithm_properties,
                                                  media_properties);

                            try {
                                locations = detection_engine.GetDetections(image_job);
                            }
                            catch (...) {
                                handle_component_exception(error_message, rc);
                            }
                        }

                        if (rc != MPF_DETECTION_SUCCESS) {
                            LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Image detection method returned an error for " << data_uri);
                        }

                        // Pack image response
                        detection_response_body = detection_buf.PackImageResponse(
                                locations, msg_metadata, data_type, detection_type, rc, error_message);

                    } else {
                        vector <MPFGenericTrack> tracks;
                        if (generic_request.has_feed_forward_track) {
                            // Invoke the detection component
                            // with a feed-forward track
                            LOG4CXX_INFO(logger, "[" << job_name.str() << "] Processing feed-forward track on " << service_name << ".");
                            MPFGenericJob generic_job(job_name.str(),
                                                      data_uri,
                                                      generic_request.feed_forward_track,
                                                      algorithm_properties,
                                                      media_properties);

                            try {
                                tracks = detection_engine.GetDetections(generic_job);
                            }
                            catch (...) {
                                handle_component_exception(error_message, rc);
                            }
                        }
                        else {
                            // Invoke the detection component
                            // without a feed-forward track
                            MPFGenericJob generic_job(job_name.str(),
                                                      data_uri,
                                                      algorithm_properties,
                                                      media_properties);

                            try {
                                tracks = detection_engine.GetDetections(generic_job);
                            }
                            catch (...) {
                                handle_component_exception(error_message, rc);
                            }
                        }

                        if (rc != MPF_DETECTION_SUCCESS) {
                            LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Generic detection method returned an error for " << data_uri);
                        }

                        // Pack generic response
                        detection_response_body = detection_buf.PackGenericResponse(
                                tracks, msg_metadata, data_type, detection_type, rc, error_message);
                    }

                } else {
                    std::string data_type_str;
                    switch (data_type) {
                        case UNKNOWN:
                            data_type_str = "UNKNOWN";
                            break;
                        case VIDEO:
                            data_type_str = "VIDEO";
                            break;
                        case IMAGE:
                            data_type_str = "IMAGE";
                            break;
                        case AUDIO:
                            data_type_str = "AUDIO";
                            break;
                        default:
                            data_type_str = "INVALID_TYPE";
                    }
                    std::string error_message = "The detection component does not support detection data type of "
                                                + data_type_str;
                    LOG4CXX_WARN(logger, "[" << job_name.str() << "] " << error_message);

                    // Pack error response
                    detection_response_body = detection_buf.PackErrorResponse(
                            msg_metadata, data_type, MPF_UNSUPPORTED_DATA_TYPE,
                            error_message);
                }

                // Sanity check
                if (!detection_response_body.empty()) {
                    // Send response
                    LOG4CXX_DEBUG(logger, "[" << job_name.str() << "] Sending response message on " <<
                                          service_name << ".");

                    messenger.SendMessage(detection_response_body, msg_metadata, job_name.str());

                } else {
                    LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Failed to generate a detection response");
                }
            }

            // Check for 'q\n' input
            // Read from file descriptor 0 (stdin)
            FD_ZERO(&readfds);
            FD_SET(0, &readfds);
            nfds = select(1, &readfds, NULL, NULL, &tv);
            if (nfds != 0 && FD_ISSET(0, &readfds)) {
                bytes_read = read(0, input_buf, input_buf_size);
                string std_input(input_buf);
                std_input.resize(input_buf_size);
                if ((bytes_read > 0) && (std_input == quit_string)) {
                    LOG4CXX_INFO(logger, "Received quit command.");
                    keep_running = false;
                }
            }
        } // end while
    } catch (std::exception &e) {
        error_occurred = true;
        LOG4CXX_ERROR(logger, "Standard Exception caught in main.cpp:  "
                              << e.what()
                              << "\n");
    } catch (...) {
        error_occurred = true;
        // Swallow any other unknown exceptions
        LOG4CXX_ERROR(logger, "Unknown Exception caught in main.cpp\n");
    }

    if (!detection_engine.Close()) {
        error_occurred = true;
        LOG4CXX_ERROR(logger, "Detection engine failed to close... ");
    }

    // Close the logger
    log4cxx::LogManager::shutdown();
    return error_occurred ? 1 : 0;
}
