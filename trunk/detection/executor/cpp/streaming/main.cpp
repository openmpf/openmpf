/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

#include <iostream>
#include <string>
#include <memory>
#include <map>
#include <chrono>

#include <unistd.h>

#include <opencv2/videoio.hpp>
#include <opencv2/core.hpp>

#include <log4cxx/logger.h>
#include <log4cxx/xml/domconfigurator.h>
#include <log4cxx/basicconfigurator.h>

#include <MPFDetectionComponent.h>

#include "QuitWatcher.h"
#include "StreamingComponentHandle.h"
#include "MPFAMQMessenger.h"
#include "JobSettings.h"



using namespace MPF;
using namespace COMPONENT;


std::string get_app_dir();
log4cxx::LoggerPtr get_logger(const std::string &app_dir);
long get_timestamp_millis();


int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::cerr << "Usage: " << argv[0] << " <ini_path>" << std::endl;
        return 1;
    }

    log4cxx::LoggerPtr logger;
    std::string log_prefix;
    try {
        std::string app_dir = get_app_dir();

        logger = get_logger(app_dir);

        LOG4CXX_INFO(logger, "Loading job settings from: " << argv[1]);
        JobSettings settings = JobSettings::FromIniFile(argv[1]);

        std::string job_name = "Streaming Job #" + std::to_string(settings.job_id);
        LOG4CXX_INFO(logger, "Initializing " << job_name);
        MPFStreamingVideoJob job(job_name, settings.stream_uri, settings.job_properties, settings.media_properties);

        log_prefix = "[" + job_name + "] ";


        std::shared_ptr<AMQMessagingManager> messaging_manager = std::make_shared<AMQMessagingManager>(logger);
        messaging_manager->Connect(settings.message_broker_uri, {});

        ActivityAlertMessenger activity_alert_messenger(messaging_manager, logger);
        activity_alert_messenger.InitQueue(settings.activity_alert_queue, {});
        activity_alert_messenger.CreateProducer();


        SegmentSummaryMessenger segment_summary_messenger(messaging_manager, logger);
        segment_summary_messenger.InitQueue(settings.summary_report_queue, {});
        segment_summary_messenger.CreateProducer();

        messaging_manager->Start();


        LOG4CXX_INFO(logger, log_prefix << "Loading component from: " << settings.component_lib_path);
        StreamingComponentHandle component(settings.component_lib_path, app_dir, job);
        std::string detection_Type = component.GetDetectionType();

        LOG4CXX_INFO(logger, log_prefix << "Connecting to stream at: " << settings.stream_uri)
        cv::VideoCapture video_capture(settings.stream_uri);
        if (!video_capture.isOpened()) {
            LOG4CXX_ERROR(logger, log_prefix << "Unable to connect to stream: " << settings.segment_size);
            return 2;
        }

        QuitWatcher quit_watcher;
        bool read_failed = false;

        int frame_id = -1;
        while (!quit_watcher.IsTimeToQuit()) {
            cv::Mat frame;
            if (!video_capture.read(frame)) {
                read_failed = true;
                break;
            }
            frame_id++;

            bool activity_found = false;
            component.ProcessFrame(frame, activity_found);
            if (activity_found) {
                LOG4CXX_DEBUG(logger, log_prefix << "Sending new activity alert for frame: " << frame_id)
                MPFActivityAlertMessage activity_alert(job_name, settings.job_id, frame_id / settings.segment_size,
                                                       frame_id, get_timestamp_millis());
                activity_alert_messenger.SendMessage(activity_alert);
            }

            if (frame_id != 0 && (frame_id % settings.segment_size == 0)) {
                std::vector<MPFVideoTrack> tracks;
                MPFDetectionError rc = component.GetVideoTracks(tracks);
                LOG4CXX_DEBUG(logger, log_prefix << "Sending segment summary for " << tracks.size() << " tracks.")
                int segment_number = frame_id / settings.segment_size;
                int segment_begin = settings.segment_size * segment_number;
                int segment_end = settings.segment_size * (segment_number + 1) - 1;
                MPFSegmentSummaryMessage segment_summary(job_name, settings.job_id, segment_number,
                                                         segment_begin, segment_end, detection_Type, rc, tracks);
                segment_summary_messenger.SendMessage(segment_summary);
            }
        }

        if (frame_id % settings.segment_size != 0) {
            std::vector<MPFVideoTrack> tracks;
            MPFDetectionError rc = component.GetVideoTracks(tracks);
            LOG4CXX_INFO(logger, log_prefix << "Send segment summary for final segment.")
            int segment_number = frame_id / settings.segment_size;
            int segment_begin = settings.segment_size * segment_number;
            int segment_end = settings.segment_size * (segment_number + 1) - 1;
            MPFSegmentSummaryMessage segment_summary(job_name, settings.job_id, segment_number,
                                                     segment_begin, segment_end, detection_Type, rc, tracks);
            segment_summary_messenger.SendMessage(segment_summary);
        }

        if (quit_watcher.IsTimeToQuit() && !quit_watcher.HasError()) {
            LOG4CXX_INFO(logger, log_prefix << "Exiting normally because quit was requested.")
            return 0;
        }

        if (quit_watcher.HasError()) {
            LOG4CXX_ERROR(logger, log_prefix << "Exiting due to an error while trying to read from standard in.")
            return 2;
        }

        if (read_failed) {
            LOG4CXX_INFO(logger, log_prefix << "Exiting because it is no longer possible to read frames.")
        }
        return 0;
    }
    catch (const std::exception &ex) {
        std::cerr << log_prefix << "Exiting due to error: " << ex.what() << std::endl;
        if (logger != nullptr) {
            LOG4CXX_ERROR(logger, log_prefix << "Exiting due to error: " << ex.what());
        }
        return 2;
    }
    catch (...) {
        std::cerr << log_prefix << "Exiting due to error." << std::endl;
        if (logger != nullptr) {
            LOG4CXX_ERROR(logger, log_prefix << "Exiting due to error.");
        }
        return 2;
    }
}


std::string get_app_dir() {
    char buffer[PATH_MAX];
    ssize_t rc = readlink("/proc/self/exe", buffer, PATH_MAX - 1);
    if (rc == -1) {
        return "";
    }
    buffer[rc] = '\0';

    std::string exe_path(buffer);
    size_t last_slash_pos = exe_path.rfind('/');
    if (last_slash_pos != std::string::npos) {
        return exe_path.substr(0, last_slash_pos);
    }
    return exe_path;
}

log4cxx::LoggerPtr get_logger(const std::string &app_dir) {
    log4cxx::xml::DOMConfigurator::configure(app_dir + "/../config/StreamingExecutorLog4cxxConfig.xml");
    log4cxx::LoggerPtr logger = log4cxx::Logger::getLogger("org.mitre.mpf.detection.streaming");
    if (logger->getAllAppenders().empty()) {
        log4cxx::BasicConfigurator::configure();
        LOG4CXX_WARN(logger, "Unable to locate StreamingExecutorLog4cxxConfig.xml. Logging to standard out instead.")
    }
    return logger;
}



long get_timestamp_millis() {
    using namespace std::chrono;
    auto time_point_millis = time_point_cast<milliseconds>(system_clock::now());
    return time_point_millis.time_since_epoch().count();
}




