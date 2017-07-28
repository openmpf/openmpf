/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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
#include <dlfcn.h>  // for dlopen(), dlsym(), etc.

#include <log4cxx/logger.h>
#include <log4cxx/xml/domconfigurator.h>
#include <log4cxx/simplelayout.h>
#include <log4cxx/consoleappender.h>
#include <log4cxx/fileappender.h>
#include <log4cxx/logmanager.h>
#include <log4cxx/level.h>

#include <boost/property_tree/ptree.hpp>
#include <boost/property_tree/ini_parser.hpp>

#include <QDir>
#include <QMap>
#include <QHash>
#include <QFile>
#include <QObject>
#include <QString>
#include <QStringList>
#include <QCoreApplication>
#include <QTime>

#include "MPFFrameReader.h"
#include "MPFMessageQueue.h"
#include "MPFFrameBuffer.h"

using std::exception;
using std::string;
using std::map;
using std::vector;

using namespace MPF;
using namespace COMPONENT;
namespace pt = boost::property_tree;

string GetFileName(const string& s) {
    size_t i = s.rfind('/', s.length());
    if (i != string::npos) {
        return s.substr(i+1, s.length()-i);
    }
    return s;
}


template<typename T>
void getArg(pt::ptree Ptree,
            const string prop_tree_path,
            const T defaultValue,
            T &arg);

template<typename T>
int getArg(pt::ptree Ptree,
           const string prop_tree_path,
           T &arg);

/**
 * This is the main program for the Streaming Video Frame Reader.
 * The program runs indefinitely, until it receives a command on the
 * standard input from the Node Manager Master. Expected commands are:
 *                "cancel": the user has canceled the job, so wait for
 *                          the quit command
 *                "quit": tear down all resources and exit.
 * @return An integer representing the exit status of the program
 *         Exit status codes are defined as follows:
 *                EXIT_SUCCESS = exited after receiving a "quit" command
                  EXIT_FAILURE = exited due to unrecoverable processing error.
 */
int main(int argc, char* argv[]) {

    // Create a Core Application so that we can access the path to the executable,
    // since the config and log files are found relative to that path.
    QCoreApplication *this_app = new QCoreApplication(argc, argv);
    string app_dir = (this_app->applicationDirPath()).toStdString();
    delete this_app;

    log4cxx::xml::DOMConfigurator::configure(app_dir + "/../config/Log4cxxConfig.xml");
    log4cxx::LoggerPtr logger = log4cxx::Logger::getLogger("org.mitre.mpf.framereader");

    int job_id;
    string job_name;
    string broker_uri;
    string frame_reader_library_path;
    string message_queue_library_path;
    string stream_uri;
    unsigned int segment_size;
    unsigned int frame_buffer_num_segments;
    string frame_buffer_name;
    int num_pipeline_stages;
    string SegmentReadyStage1_name;
    string SegmentReadyStage2_name;  // Optional
    string FrameReadyQueue_name;
    string ReleaseFrameQueue_name;
    string JobStatusQueue_name;
    float stall_detect_threshold_secs;
    float stall_alert_period_secs;
    float stall_timeout_secs;
    pt::ptree jobArgs;

    string config_file(argv[1]);
    try {
        pt::ini_parser::read_ini(config_file, jobArgs);
    } catch (const pt::ini_parser::ini_parser_error &e) {
        LOG4CXX_ERROR(logger, "Unable to parse config file named " << config_file << ": " <<e.what());
        return 5; // EIO = I/O error
    }

    int rc = getArg(jobArgs, "job_id", job_id);
    if (rc) return rc;

    int rc = getArg(jobArgs, "stream_uri", stream_uri);
    if (rc) return rc;

    job_name = "Job " + job_id + ":" + stream_uri;
    Properties job_properties;

    job_properties["JOB_NAME"] = job_name;
    job_properties["LOGGER"] = logger;

    const string default_broker_uri = "failover://(tcp://localhost:61616?jms.prefetchPolicy.all=1)?startupMaxReconnectAttempts=1";
    getArg(jobArgs, "broker_uri", broker_uri, default_broker_uri);

    rc = getArg(jobArgs, "frame_reader.library_path", frame_reader_library_path);
    if (rc) return rc;

    MPFFrameReader *Freader;
    string frame_reader_create_sym("frame_reader_creator");
    typedef MPFFrameReader* framereader_create_t();
    typedef void framereader_destroy_t(MPFFrameReader*);

    // The RTLD_NOW flag causes dlopen() to resolve all symbols now so if something is
    // missing, we will fail now rather than later.
    void *framereader_lib = dlopen(frame_reader_library_path.c_str(), RTLD_LOCAL | RTLD_NOW);
    if (NULL == framereader_lib) {
        LOG4CXX_ERROR(logger, "Failed to open frame reader library " << frame_reader_library_path << ": " << dlerror());
	return(79);  // 79=ELIBACC Can not access a needed shared library
    }

    framereader_create_t* framereader_create =
            (framereader_create_t*)dlsym(framereader_lib, "frame_reader_creator");
    if (NULL == framereader_create) {
        LOG4CXX_ERROR(logger, "dlsym failed for frame reader creator: " << dlerror());
        dlclose(framereader_lib);
        return(38);  // 38=ENOSYS Function not implemented
    }

    framereader_destroy_t* framereader_destroy =
            (framereader_destroy_t*)dlsym(framereader_lib,"frame_reader_deleter");
    if (NULL == framereader_destroy) {
        LOG4CXX_ERROR(logger,"dlsym failed for frame_reader_deleter: " << dlerror());
        dlclose(framereader_lib);
        return(38);  // 38=ENOSYS Function not implemented
    }

    FReader = framereader_create();
    if (NULL == FReader) {
	LOG4CXX_ERROR(logger,"Failed to create a frame reader instance");
	dlclose(framereader_lib);
	return(79);  // 79=ELIBACC Can not access a needed shared library
    }

    // Create the messenger object

    rc = getArg(jobArgs, "messenger.library_path", messenger_library_path);
    if (rc) return rc;

    MPFMessenger *Messenger;
    typedef MPFMessenger* messenger_create_t();
    typedef void messenger_destroy_t(MPFMessenger*);

    // The RTLD_NOW flag causes dlopen() to resolve all symbols now so if something is
    // missing, we will fail now rather than later.
    void *messenger_lib = dlopen(messenger_library_path.c_str(), RTLD_LOCAL | RTLD_NOW);
    if (NULL == messenger_lib) {
        LOG4CXX_ERROR(logger, "Failed to open messenger library " << messenger_library_path << ": " << dlerror());
	return(79);  // 79=ELIBACC Can not access a needed shared library
    }

    messenger_create_t* messenger_create =
            (messenger_create_t*)dlsym(messenger_lib, "messenger_creator");
    if (NULL == messenger_create) {
        LOG4CXX_ERROR(logger, "dlsym failed for messenger creator: " << dlerror());
        dlclose(messenger_lib);
        return(38);  // 38=ENOSYS Function not implemented
    }

    messenger_destroy_t* messenger_destroy =
            (messenger_destroy_t*)dlsym(messenger_lib,"messenger_deleter");
    if (NULL == messenger_destroy) {
        LOG4CXX_ERROR(logger,"dlsym failed for messenger_deleter: " << dlerror());
        dlclose(messenger_lib);
        return(38);  // 38=ENOSYS Function not implemented
    }

    Messenger = messenger_create();
    if (NULL == messenger) {
	LOG4CXX_ERROR(logger,"Failed to create a messenger instance");
	dlclose(messenger_lib);
	return(79);  // 79=ELIBACC Can not access a needed shared library
    }

    Properties broker_properties;
    MPFMessengerError MsgrErr;
    MsgrErr = Messenger->Startup(broker_uri, broker_properties);
    if (MsgrErr != MPF_MESSENGER_SUCCESS) {
        LOG4CXX_ERROR(logger, "Failed to start messenger");
        return MsgrErr;
    }

    // Create sender for the job status queue
    int rc = getArg(jobArgs, "job_status_queue_name", JobStatusQueue_name);
    if (rc) return rc;
    MPFMessageSender jobStatusSender;
    Properties queue_properties;
    MsgrErr = Messenger->CreateSender(JobStatusQueue_name,
                                      queue_properties,
                                      jobStatusSender);

    // Create sender for the frame ready queue

    int rc = getArg(jobArgs, "frame_ready_queue_name", FrameReadyQueue_name);
    if (rc) return rc;
    MPFMessageSender FrameReadySender;
    MsgrErr = Messenger->CreateSender(FrameReadyQueue_name,
                                      queue_properties,
                                      FrameReadySender);
    

    // Create sender for the first stage segment ready queue

    int rc = getArg(jobArgs, "segment_ready_stage1_queue_name", SegmentReadyStage1_name);
    if (rc) return rc;
    MPFMessageSender *Stage1Sender;
    MsgrErr = Messenger->CreateSender(SegmentReadyStage1_name,
                                      queue_properties,
                                      Stage1Sender);

    // Check whether we have a 2 stage pipeline
    int rc = getArg(jobArgs, "num_pipeline_stages", num_pipeline_stages);
    if (rc) return rc;
    MPFMessageSender *Stage2Sender;
    if (num_pipeline_stages == 2 1) {
        // prepare for 2-stage pipeline
        int rc = getArg(jobArgs, "segment_ready_stage2_queue_name", SegmentReadyStage2_name);
        if (rc) return rc;
        MPFMessageSender *Stage2Sender;
        MsgrErr = Messenger->CreateSender(SegmentReadyStage2_name,
                                          queue_properties,
                                          Stage2Sender);

    }

    // Create a receiver for the release frame queue

    int rc = getArg(jobArgs, "release_frame_queue_name", ReleaseFrameQueue_name);
    if (rc) return rc;
    MPFMessageReceiver *releaseFrameReceiver;
    MsgrErr = Messenger->CreateReceiver(ReleaseFrameQueue_name,
                                        queue_properties,
                                        releaseFrameReceiver);


    for (pt::ptree::value_type &job_prop : jobArgs.get_child("job_properties")) {
        job_properties[prop.first] = prop.second.data();
    }
 
    Properties media_properties;
    for (pt::ptree::value_type &job_prop : jobArgs.get_child("media_properties")) {
        media_properties[prop.first] = prop.second.data();
    }

    MPFFrameReaderJob job(job_name, stream_uri,
                          job_properties, media_properties);
    
    // FrameReader attaches to the video stream to get video stream
    // properties.

    Properties stream_properties;
    MPFFrameReaderError FRerror;
    FRerror = Freader->AttachToStream(job, stream_properties);
    if (FRerror) {
        LOG4CXX_ERROR(logger, "Failed to attach to video stream: " << stream_uri);
        return FRerror;
    }
    // FrameReader creates the frame data shared storage.

    int rc = getArg(jobArgs, "frame_store_path_name", frame_buffer_name);
    if (rc) return rc;
    Freader->OpenFrameStore(job, stream_properties);

    // Remain in loop handling track request messages
    // until 'q\n' is received on stdin
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

        detection_engine->SetRunDirectory(app_dir + "/../plugins");

        if (!detection_engine->Init()) {
            LOG4CXX_ERROR(logger, "Detection component initialization failed, exiting.");
            return -1;
        }

        LOG4CXX_INFO(logger, "Completed initialization of " << getenv("SERVICE_NAME") << ".");

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
                    }

                    LOG4CXX_INFO(logger, "[" << job_name.str() << "] Processing message on " << getenv("SERVICE_NAME") << ".");

                    string detection_type = detection_engine->GetDetectionType();

                    if (detection_engine->Supports(data_type)) {

                        if (data_type == MPFDetectionDataType::VIDEO) {
                            vector <MPFVideoTrack> tracks;


                            MPFVideoJob video_job(job_name.str(), data_uri, video_request.start_frame,
                                                  video_request.stop_frame, algorithm_properties, media_properties);

                            // Invoke component
                            MPFDetectionError rc = detection_engine->GetDetections(video_job, tracks);

                            msg_metadata->time_elapsed = time.elapsed();
                            
                            if (rc != MPF_DETECTION_SUCCESS) {
                                LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Video detection method returned an error for " << data_uri);
                            }

                            // Pack video response
                            detection_response_body = detection_buf.PackVideoResponse(
                                    tracks, msg_metadata, data_type, detection_type, &response_body_length, rc);

                        } else if (data_type == MPFDetectionDataType::AUDIO) {
                            vector <MPFAudioTrack> tracks;

                            MPFAudioJob audio_job(job_name.str(), data_uri, audio_request.start_time,
                                                  audio_request.stop_time, algorithm_properties, media_properties);

                            // Invoke the detection component
                            MPFDetectionError rc = detection_engine->GetDetections(audio_job, tracks);

                            msg_metadata->time_elapsed = time.elapsed();

                            if (rc != MPF_DETECTION_SUCCESS) {
                                LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Audio detection method returned an error for " << data_uri);
                            }

                            // Pack audio response
                            detection_response_body = detection_buf.PackAudioResponse(
                                    tracks, msg_metadata, data_type, detection_type, &response_body_length, rc);

                        } else if (data_type == MPFDetectionDataType::IMAGE) {
                            vector <MPFImageLocation> locations;
                            MPFImageJob image_job(job_name.str(), data_uri, algorithm_properties, media_properties);

                            // Invoke component
                            MPFDetectionError rc = detection_engine->GetDetections(image_job, locations);

                            msg_metadata->time_elapsed = time.elapsed();

                            if (rc != MPF_DETECTION_SUCCESS) {
                                LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Image detection method returned an error for " << data_uri);
                            }

                            // Pack image response
                            detection_response_body = detection_buf.PackImageResponse(
                                    locations, msg_metadata, data_type, detection_type, &response_body_length, rc);

                        } else {
                            LOG4CXX_ERROR(logger, "[" << job_name.str() << "] Invalid detection data_type of " << data_type);

                            msg_metadata->time_elapsed = time.elapsed();

                            // Pack error response
                            detection_response_body = detection_buf.PackErrorResponse(
                                    msg_metadata, data_type, &response_body_length, MPF_UNRECOGNIZED_DATA_TYPE);
                        }

                    } else {
                        LOG4CXX_WARN(logger, "[" << job_name.str() << "] The detection component does not support detection data_type of " << data_type);

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
                                          getenv("SERVICE_NAME") << ".");

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
                    keep_running = false;
                }
            }

        } // end while
    } catch (std::exception &e) {
        LOG4CXX_ERROR(logger, "Standard Exception caught in main.cpp:  "
                              << e.what()
                              << "\n");
    } catch (...) {
        // Swallow any other unknown exceptions
        LOG4CXX_ERROR(logger, "Unknown Exception caught in main.cpp\n");
    }

    if (!detection_engine->Close()) {
        LOG4CXX_ERROR(logger, "Detection engine failed to close... ");
    }

    // Shut down the message interface
    try {
        messenger.Shutdown();
    } catch (cms::CMSException &e) {
        LOG4CXX_ERROR(logger, "CMS Exception caught during message interface shutdown: "
                              << request_queue << ": "
                              << e.what());
    } catch (exception &e) {
        LOG4CXX_ERROR(logger, "Standard Exception caught during message interface shutdown: "
                              << request_queue << ": "
                              << e.what());
    } catch (...) {
        // Swallow any other unknown exceptions
        LOG4CXX_ERROR(logger, "Unknown Exception caught during message interface shutdown: "
                              << request_queue);
    }

    // Don't call this until the 'quit' message is received from the
    // node manager.
    // Destroy the FrameReader
    Freader->CloseFrameStore();
    framereader_destroy(FReader);

    // Close the senders and receivers

    // Delete the Messenger
    messenger_destroy(FReader);


    // Close the logger
    log4cxx::LogManager::shutdown();
    return 0;
}


// This version allows a default value for properties that can be
// omitted if we want to use a default value
template<typename T>
void getArg(pt::ptree Ptree,
            const string prop_tree_path,
            const T defaultValue,
            T &arg) {
    arg = Ptree.get(prop_tree_path, defaultValue);
}

// This version does not allow a default value and returns an error if
// the property is not found
template<typename T>
int getArg(pt::ptree Ptree,
           const string prop_tree_path,
            T &arg) {

    try {
        arg = Ptree.get<T>(prop_tree_path);
    } catch (const pt::ptree_error &e) {
        LOG4CXX_ERROR(logger, "Could not read configuration property\""
                      << prop_tree_path
                      << "\" : " << e.what());
        return 22; // EINVAL = invalid argument
    }

}

