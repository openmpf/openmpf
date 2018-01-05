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
#include "JobSettings.h"
#include "BasicAmqMessageSender.h"
#include "ExitCodes.h"
#include "InternalComponentError.h"


using namespace MPF;
using namespace COMPONENT;


int run_job(const std::string &ini_path);
int run_job(const JobSettings &settings, const std::string &app_dir, log4cxx::LoggerPtr &logger);

std::string get_app_dir();
log4cxx::LoggerPtr get_logger(const std::string &app_dir);


int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::cerr << "Usage: " << argv[0] << " <ini_path>" << std::endl;
        return ExitCodes::InvalidCommandLineArgs;
    }
    return run_job(argv[1]);
}


int run_job(const std::string &ini_path) {
    std::string app_dir = get_app_dir();
    log4cxx::LoggerPtr logger = get_logger(app_dir);
    std::string log_prefix;
    try {
        LOG4CXX_INFO(logger, "Loading job settings from: " << ini_path);
        JobSettings settings = JobSettings::FromIniFile(ini_path);
        log_prefix = "[Streaming Job #" + std::to_string(settings.job_id) + "] ";
        return run_job(settings, app_dir, logger);
    }
    catch (const InternalComponentError &ex) {
        LOG4CXX_ERROR(logger, log_prefix << "Exiting due to internal component error: " << ex.what());
        return ExitCodes::InternalComponentError;
    }
    catch (const std::exception &ex) {
        LOG4CXX_ERROR(logger, log_prefix << "Exiting due to error: " << ex.what());
        return ExitCodes::UnexpectedError;
    }
    catch (...) {
        LOG4CXX_ERROR(logger, log_prefix << "Exiting due to error.");
        return ExitCodes::UnexpectedError;
    }
}


int run_job(const JobSettings &settings, const std::string &app_dir, log4cxx::LoggerPtr &logger) {
    std::string job_name = "Streaming Job #" + std::to_string(settings.job_id);
    LOG4CXX_INFO(logger, "Initializing " << job_name);
    MPFStreamingVideoJob job(job_name, app_dir + "/../plugins/", settings.job_properties, settings.media_properties);

    std::string log_prefix = "[" + job_name + "] ";

    BasicAmqMessageSender sender(settings);


    LOG4CXX_INFO(logger, log_prefix << "Loading component from: " << settings.component_lib_path);
    StreamingComponentHandle component(settings.component_lib_path, job);
    std::string detection_type = component.GetDetectionType();

    LOG4CXX_INFO(logger, log_prefix << "Connecting to stream at: " << settings.stream_uri)
    cv::VideoCapture video_capture(settings.stream_uri);
    if (!video_capture.isOpened()) {
        LOG4CXX_ERROR(logger, log_prefix << "Unable to connect to stream: " << settings.stream_uri);
        return ExitCodes::UnableToOpenStream;
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

        if (frame_id % settings.segment_size == 0) {
            component.BeginSegment({frame_id / settings.segment_size, frame_id, frame_id + settings.segment_size});
        }

        bool activity_found = component.ProcessFrame(frame, frame_id);
        if (activity_found) {
            LOG4CXX_DEBUG(logger, log_prefix << "Sending new activity alert for frame: " << frame_id)
            sender.SendActivityAlert(frame_id);
        }

        if (frame_id != 0 && (frame_id % settings.segment_size == 0)) {
            const std::vector<MPFVideoTrack> &tracks = component.EndSegment();
            LOG4CXX_DEBUG(logger, log_prefix << "Sending segment summary for " << tracks.size() << " tracks.")
            sender.SendSummaryReport(frame_id, detection_type, MPF_DETECTION_SUCCESS, tracks);
        }
    }

    if (frame_id % settings.segment_size != 0) {
        const std::vector<MPFVideoTrack> &tracks = component.EndSegment();
        LOG4CXX_INFO(logger, log_prefix << "Send segment summary for final segment.")
        sender.SendSummaryReport(frame_id, detection_type, MPF_DETECTION_SUCCESS, tracks);
    }

    if (quit_watcher.IsTimeToQuit() && !quit_watcher.HasError()) {
        LOG4CXX_INFO(logger, log_prefix << "Exiting normally because quit was requested.")
        return 0;
    }

    if (quit_watcher.HasError()) {
        LOG4CXX_ERROR(logger, log_prefix << "Exiting due to an error while trying to read from standard in.")
        return ExitCodes::UnexpectedError;
    }

    if (read_failed) {
        LOG4CXX_INFO(logger, log_prefix << "Exiting because it is no longer possible to read frames.")
        return ExitCodes::StreamNoLongerReadable;
    }
    return 0;

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




