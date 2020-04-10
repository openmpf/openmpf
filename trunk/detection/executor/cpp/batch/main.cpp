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

#include <stdio.h>
#include <sys/types.h>
#include <sys/time.h>
#include <cstdlib>
#include <unistd.h>

#include <log4cxx/logger.h>
#include <log4cxx/xml/domconfigurator.h>
#include <log4cxx/simplelayout.h>
#include <log4cxx/consoleappender.h>
#include <log4cxx/fileappender.h>
#include <log4cxx/logmanager.h>
#include <log4cxx/level.h>

#include <QCoreApplication>
#include <QTime>

#include "MPFMessenger.h"
#include "MPFDetectionBuffer.h"
#include "CppComponentHandle.h"
#include "ComponentLoadError.h"
#include "PythonComponentHandle.h"

#include <MPFDetectionComponent.h>

using std::exception;
using std::string;
using std::map;
using std::vector;

using namespace MPF;
using namespace COMPONENT;

string GetFileName(const string& s) {
    size_t i = s.rfind('/', s.length());
    if (i != std::string::npos) {
        return s.substr(i+1, s.length()-i);
    }
    return s;
}

bool is_python(log4cxx::LoggerPtr &logger, int argc, char* argv[]);

std::string get_virtual_env_activate_path();


template <typename ComponentHandle>
int run_jobs(log4cxx::LoggerPtr &logger, const std::string &broker_uri, const std::string &request_queue,
             const std::string &app_dir, ComponentHandle &detection_engine);

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
    // Create a Core Application so that we can access the path to the executable,
    // since the config and log files are found relative to that path.
    QCoreApplication *this_app = new QCoreApplication(argc, argv);
    string app_dir = (this_app->applicationDirPath()).toStdString();
    delete this_app;

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
            PythonComponentHandle component_handle(logger, lib_path, get_virtual_env_activate_path());
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

std::string get_virtual_env_activate_path()  {
    std::string venv_path;
    char* env_var_venv_path_ptr = getenv("COMPONENT_VIRTUALENV");
    if (env_var_venv_path_ptr != nullptr && env_var_venv_path_ptr[0] != '\0') {
        venv_path = env_var_venv_path_ptr;
    }
    else {
        venv_path = "venv";
    }
    return venv_path + "/bin/activate_this.py";
}


template <typename ComponentHandle>
int run_jobs(log4cxx::LoggerPtr &logger, const std::string &broker_uri, const std::string &request_queue,
            const std::string &app_dir, ComponentHandle &detection_engine) {
    int pollingInterval = 1;

    // Instantiate AMQ interface
    MPFMessenger messenger(logger);

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

        messenger.Startup(broker_uri, request_queue);

        detection_engine.SetRunDirectory(app_dir + "/../plugins");

        if (!detection_engine.Init()) {
            LOG4CXX_ERROR(logger, "Detection component initialization failed, exiting.");
            return -1;
        }

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
            MPFMessageMetadata *msg_metadata = new MPFMessageMetadata;
            int request_body_length;
            unsigned char *request_contents = messenger.ReceiveMessage(msg_metadata, &request_body_length);

            if (request_contents != NULL) {
                QTime time;
                time.start();

                //  Set not to sleep flag.
                gotMessageOnLastPull = true;

                std::stringstream job_name; // Empty; need to unpack detection request to determine name

                MPFDetectionBuffer detection_buf(logger);
                bool success = detection_buf.UnpackRequest(request_contents, request_body_length);

                unsigned char* detection_response_body = NULL;
                int response_body_length;

                if (!success) {
                    LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Failed while unpacking the detection request");

                    msg_metadata->time_elapsed = time.elapsed();

                    // Pack error response
                    detection_response_body = detection_buf.PackErrorResponse(
                            msg_metadata, MPFDetectionDataType::UNKNOWN, &response_body_length,
                            MPF_DETECTION_NOT_INITIALIZED); // TODO: consider using a more descriptive error

                } else {
                    detection_buf.GetMessageMetadata(msg_metadata);
                    MPFDetectionDataType data_type = detection_buf.GetDataType();
                    string data_uri = detection_buf.GetDataUri();

                    map<string, string> algorithm_properties;
                    detection_buf.GetAlgorithmProperties(algorithm_properties);

                    map<string, string> media_properties;
                    detection_buf.GetMediaProperties(media_properties);


                    MPFDetectionVideoRequest video_request;
                    MPFDetectionAudioRequest audio_request;
                    MPFDetectionImageRequest image_request;
                    MPFDetectionGenericRequest generic_request;

                    job_name << "Job " << msg_metadata->job_id << ":" << GetFileName(data_uri);

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
                        MPFDetectionError rc;
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

                                rc = detection_engine.GetDetections(video_job, tracks);
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

                                rc = detection_engine.GetDetections(video_job, tracks);
                            }

                            msg_metadata->time_elapsed = time.elapsed();

                            if (rc != MPF_DETECTION_SUCCESS) {
                                LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Video detection method returned an error for " << data_uri);
                            }

                            // Pack video response
                            detection_response_body = detection_buf.PackVideoResponse(
                                    tracks, msg_metadata, data_type, detection_type, &response_body_length, rc);

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

                                rc = detection_engine.GetDetections(audio_job, tracks);
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

                                rc = detection_engine.GetDetections(audio_job, tracks);
                            }
                            msg_metadata->time_elapsed = time.elapsed();

                            if (rc != MPF_DETECTION_SUCCESS) {
                                LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Audio detection method returned an error for " << data_uri);
                            }

                            // Pack audio response
                            detection_response_body = detection_buf.PackAudioResponse(
                                    tracks, msg_metadata, data_type, detection_type, &response_body_length, rc);

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

                                rc = detection_engine.GetDetections(image_job, locations);
                            }
                            else {
                                // Invoke the detection component
                                // without a feed-forward location
                                MPFImageJob image_job(job_name.str(),
                                                      data_uri,
                                                      algorithm_properties,
                                                      media_properties);

                                rc = detection_engine.GetDetections(image_job, locations);
                            }
                            msg_metadata->time_elapsed = time.elapsed();

                            if (rc != MPF_DETECTION_SUCCESS) {
                                LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Image detection method returned an error for " << data_uri);
                            }

                            // Pack image response
                            detection_response_body = detection_buf.PackImageResponse(
                                    locations, msg_metadata, data_type, detection_type, &response_body_length, rc);

                        } else {
                            /*
                            LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Invalid detection data_type of " << data_type);

                            msg_metadata->time_elapsed = time.elapsed();

                            // Pack error response
                            detection_response_body = detection_buf.PackErrorResponse(
                                    msg_metadata, data_type, &response_body_length, MPF_UNRECOGNIZED_DATA_TYPE);
                            */

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

                                rc = detection_engine.GetDetections(generic_job, tracks);
                            }
                            else {
                                // Invoke the detection component
                                // without a feed-forward track
                                MPFGenericJob generic_job(job_name.str(),
                                                          data_uri,
                                                          algorithm_properties,
                                                          media_properties);

                                rc = detection_engine.GetDetections(generic_job, tracks);
                            }
                            msg_metadata->time_elapsed = time.elapsed();

                            if (rc != MPF_DETECTION_SUCCESS) {
                                LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Generic detection method returned an error for " << data_uri);
                            }

                            // Pack generic response
                            detection_response_body = detection_buf.PackGenericResponse(
                                    tracks, msg_metadata, data_type, detection_type, &response_body_length, rc);
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
                        LOG4CXX_WARN(logger, "[" << job_name.str()
                                << "] The detection component does not support detection data type of "
                                << data_type_str);

                        msg_metadata->time_elapsed = time.elapsed();

                        // Pack error response
                        detection_response_body = detection_buf.PackErrorResponse(
                                msg_metadata, data_type, &response_body_length, MPF_UNSUPPORTED_DATA_TYPE);
                    }
                }

                // Sanity check
                if (NULL != detection_response_body) {
                    // Send response
                    LOG4CXX_DEBUG(logger, "[" << job_name.str() << "] Sending response message on " <<
                                          service_name << ".");

                    messenger.SendMessage(detection_response_body,
                                                  msg_metadata,
                                                  response_body_length,
                                                  job_name.str());

                    delete[] detection_response_body;

                } else {
                    LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Failed to generate a detection response");
                }
            }

            delete msg_metadata;

            // Check for 'q\n' input
            // Read from file descriptor 0 (stdin)
            FD_ZERO(&readfds);
            FD_SET(0, &readfds);
            nfds = select(1, &readfds, NULL, NULL, &tv);
            if (nfds != 0) {
                if (FD_ISSET(0, &readfds)) {
                    bytes_read = read(0, input_buf, input_buf_size);
                }
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

    // Shut down the message interface
    try {
        messenger.Shutdown();
    } catch (cms::CMSException &e) {
        error_occurred = true;
        LOG4CXX_ERROR(logger, "CMS Exception caught during message interface shutdown: "
                              << request_queue << ": "
                              << e.what());
    } catch (exception &e) {
        error_occurred = true;
        LOG4CXX_ERROR(logger, "Standard Exception caught during message interface shutdown: "
                              << request_queue << ": "
                              << e.what());
    } catch (...) {
        error_occurred = true;
        // Swallow any other unknown exceptions
        LOG4CXX_ERROR(logger, "Unknown Exception caught during message interface shutdown: "
                              << request_queue);
    }

    // Close the logger
    log4cxx::LogManager::shutdown();
    return error_occurred ? 1 : 0;
}
