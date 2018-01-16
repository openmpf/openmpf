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

#include <cstdlib>
#include <libgen.h>

#include <opencv2/videoio.hpp>
#include <opencv2/core.hpp>

#include <log4cxx/logger.h>
#include <log4cxx/xml/domconfigurator.h>
#include <log4cxx/basicconfigurator.h>

#include <MPFDetectionComponent.h>

#include "StandardInWatcher.h"
#include "StreamingComponentHandle.h"
#include "JobSettings.h"
#include "BasicAmqMessageSender.h"
#include "ExecutorErrors.h"


using namespace MPF;
using namespace COMPONENT;



class StreamingComponentExecutor {
public:

    static ExitCode RunJob(const std::string &ini_path) {
        std::string app_dir = get_app_dir();
        log4cxx::LoggerPtr logger = get_logger(app_dir);
        std::string log_prefix;
        try {
            LOG4CXX_INFO(logger, "Loading job settings from: " << ini_path);

            JobSettings settings = JobSettings::FromIniFile(ini_path);
            std::string job_name = "Streaming Job #" + std::to_string(settings.job_id);
            LOG4CXX_INFO(logger, "Initializing " << job_name);

            log_prefix = "[" + job_name + "] ";
            MPFStreamingVideoJob job(job_name, app_dir + "/../plugins/", settings.job_properties,
                                     settings.media_properties);

            StreamingComponentExecutor executor = Create(logger, log_prefix, std::move(settings), std::move(job));
            executor.Run();
            return ExitCode::SUCCESS;
        }
        catch (const cms::CMSException &ex) {
            LOG4CXX_ERROR(logger, log_prefix << "Exiting due to message broker error: " << ex.what());
            return ExitCode::MESSAGE_BROKER_ERROR;
        }
        catch (const FatalError &ex) {
            LOG4CXX_ERROR(logger, log_prefix << "Exiting due to error: " << ex.what());
            return ex.GetExitCode();
        }
        catch (const std::exception &ex) {
            LOG4CXX_ERROR(logger, log_prefix << "Exiting due to error: " << ex.what());
            return ExitCode::UNEXPECTED_ERROR;
        }
        catch (...) {
            LOG4CXX_ERROR(logger, log_prefix << "Exiting due to error.");
            return ExitCode::UNEXPECTED_ERROR;
        }
    }


private:
    log4cxx::LoggerPtr logger_;

    const std::string log_prefix_;

    JobSettings settings_;

    BasicAmqMessageSender sender_;

    MPFStreamingVideoJob job_;

    StreamingComponentHandle component_;

    const std::string detection_type_;


    StreamingComponentExecutor(
            const log4cxx::LoggerPtr &logger,
            const std::string &log_prefix,
            JobSettings &&settings,
            BasicAmqMessageSender &&sender,
            MPFStreamingVideoJob &&job,
            StreamingComponentHandle &&component,
            const std::string &detection_type)
        : logger_(logger)
        , log_prefix_(log_prefix)
        , settings_(std::move(settings))
        , job_(std::move(job))
        , sender_(std::move(sender))
        , component_(std::move(component))
        , detection_type_(detection_type)
    {

    }


    static StreamingComponentExecutor Create(
            const log4cxx::LoggerPtr &logger, const std::string &log_prefix,
            JobSettings &&settings,  MPFStreamingVideoJob &&job) {

        BasicAmqMessageSender sender(settings);
        std::string detection_type;
        try {

            LOG4CXX_INFO(logger, log_prefix << "Loading component from: " << settings.component_lib_path);
            StreamingComponentHandle component(settings.component_lib_path, job);
            detection_type = component.GetDetectionType();

            return StreamingComponentExecutor(
                    logger,
                    log_prefix,
                    std::move(settings),
                    std::move(sender),
                    std::move(job),
                    std::move(component),
                    detection_type);
        }
        catch (const FatalError &ex) {
            sender.SendSummaryReport(0, detection_type, {}, ex.what());
            throw;
        }
    }



    void Run() {
        int frame_number = -1;
        try {
            LOG4CXX_INFO(logger_, log_prefix_ << "Connecting to stream at: " << settings_.stream_uri)
            cv::VideoCapture video_capture(settings_.stream_uri);
            if (!video_capture.isOpened()) {
                throw FatalError(ExitCode::UNABLE_TO_CONNECT_TO_STREAM,
                                 "Unable to connect to stream: " + settings_.stream_uri);
            }

            StandardInWatcher *std_in_watcher = StandardInWatcher::GetInstance();

            bool segment_activity_alert_sent = false;

            while (!std_in_watcher->QuitReceived()) {
                cv::Mat frame;
                if (!video_capture.read(frame)) {
                    // TODO: Detect and report stalls.
                    throw FatalError(ExitCode::STREAM_STALLED, "It is no longer possible to read frames.");
                }
                frame_number++;

                if (frame_number % settings_.segment_size == 0) {
                    int segment_number = frame_number / settings_.segment_size;
                    int segment_end = frame_number + settings_.segment_size;
                    cv::Size frame_size = frame.size();
                    component_.BeginSegment({ segment_number, frame_number, segment_end,
                                                   frame_size.width, frame_size.height });
                    segment_activity_alert_sent = false;
                }

                bool activity_found = component_.ProcessFrame(frame, frame_number);
                if (activity_found && !segment_activity_alert_sent) {
                    LOG4CXX_DEBUG(logger_, log_prefix_ << "Sending new activity alert for frame: " << frame_number)
                    sender_.SendActivityAlert(frame_number);
                    segment_activity_alert_sent = true;
                }

                if (frame_number != 0 && (frame_number % settings_.segment_size == 0)) {
                    const std::vector<MPFVideoTrack> &tracks = component_.EndSegment();
                    LOG4CXX_DEBUG(logger_, log_prefix_ << "Sending segment summary for " << tracks.size() << " tracks.")
                    sender_.SendSummaryReport(frame_number, detection_type_, tracks);
                }
            }
            if (frame_number % settings_.segment_size != 0) {
                const std::vector<MPFVideoTrack> &tracks = component_.EndSegment();
                LOG4CXX_INFO(logger_, log_prefix_ << "Send segment summary for final segment.")
                sender_.SendSummaryReport(frame_number, detection_type_, tracks);
            }
        }
        catch (const FatalError &ex) {
            // Send error report before actually handling exception.
            sender_.SendSummaryReport(frame_number, detection_type_, TryGetRemainingTracks(), ex.what());
            throw;
        }
    }


    std::vector<MPFVideoTrack> TryGetRemainingTracks() {
        try {
            return component_.EndSegment();
        }
        catch (...) {
            return { };
        }
    }


    static std::string get_app_dir() {
        char* this_exe = canonicalize_file_name("/proc/self/exe");
        if (this_exe == nullptr) {
            return "";
        }

        std::string app_dir = dirname(this_exe); // The dirname documentation says the returned pointer must not be freed.
        free(this_exe);
        return app_dir;
    }


    static log4cxx::LoggerPtr get_logger(const std::string &app_dir) {
        std::string log_config_file = app_dir + "/../config/StreamingExecutorLog4cxxConfig.xml";
        log4cxx::xml::DOMConfigurator::configure(log_config_file);
        log4cxx::LoggerPtr logger = log4cxx::Logger::getLogger("org.mitre.mpf.detection.streaming");
        if (logger->getAllAppenders().empty()) {
            log4cxx::BasicConfigurator::configure();
            LOG4CXX_WARN(logger, "Unable to load log configuration file at " << log_config_file
                                 << ". Logging to standard out instead.")
        }
        return logger;
    }
};



int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::cerr << "Usage: " << argv[0] << " <ini_path>" << std::endl;
        return static_cast<int>(ExitCode::INVALID_COMMAND_LINE_ARGUMENTS);
    }
    return static_cast<int>(StreamingComponentExecutor::RunJob(argv[1]));
}


