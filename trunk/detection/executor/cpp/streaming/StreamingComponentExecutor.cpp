/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
#include <log4cxx/basicconfigurator.h>
#include <log4cxx/xml/domconfigurator.h>

#include <detectionComponentUtils.h>

#include "StreamingComponentExecutor.h"
#include "StandardInWatcher.h"


namespace MPF { namespace COMPONENT {


    ExitCode StreamingComponentExecutor::RunJob(const std::string &ini_path) {
        std::string app_dir = GetAppDir();
        log4cxx::LoggerPtr logger = GetLogger(app_dir);
        std::string log_prefix;
        try {
            LOG4CXX_INFO(logger, "Loading job settings from: " << ini_path);

            JobSettings settings = JobSettings::FromIniFile(ini_path);
            std::string job_name = "Streaming Job #" + std::to_string(settings.job_id);
            LOG4CXX_INFO(logger, "Initializing " << job_name);

            log_prefix = "[" + job_name + "] ";
            MPFStreamingVideoJob job(job_name, app_dir + "/../plugins/", settings.job_properties,
                                     settings.media_properties);

            RetryStrategy retry_config = settings.retry_strategy;
            StreamingComponentExecutor executor = Create(logger, log_prefix, std::move(settings), std::move(job));

            switch (retry_config) {
                case RetryStrategy::NEVER_RETRY:
                    executor.Run<RetryStrategy::NEVER_RETRY>();
                    break;
                case RetryStrategy::NO_ALERT_NO_TIMEOUT:
                    executor.Run<RetryStrategy::NO_ALERT_NO_TIMEOUT>();
                    break;
                case RetryStrategy::NO_ALERT_WITH_TIMEOUT:
                    executor.Run<RetryStrategy::NO_ALERT_WITH_TIMEOUT>();
                    break;
                case RetryStrategy::ALERT_NO_TIMEOUT:
                    executor.Run<RetryStrategy::ALERT_NO_TIMEOUT>();
                    break;
                case RetryStrategy::ALERT_WITH_TIMEOUT:
                    executor.Run<RetryStrategy::ALERT_WITH_TIMEOUT>();
                    break;
            }

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

    StreamingComponentExecutor::StreamingComponentExecutor(
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
        , video_capture_(logger, settings_.stream_uri, job_)
        , detection_type_(detection_type)
        , confidence_threshold_(DetectionComponentUtils::GetProperty(
                    settings_.job_properties, "CONFIDENCE_THRESHOLD", ExecutorUtils::LOWEST_CONFIDENCE_THRESHOLD))
    {
    }


    StreamingComponentExecutor StreamingComponentExecutor::Create(
            const log4cxx::LoggerPtr &logger, const std::string &log_prefix,
            JobSettings &&settings, MPFStreamingVideoJob &&job) {

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
            sender.SendSummaryReport(0, detection_type, {}, {}, ex.what());
            throw;
        }

    }


    template <RetryStrategy RETRY_STRATEGY>
    void StreamingComponentExecutor::Run() {
        int frame_number = -1;
        std::unordered_map<int, long> frame_timestamps;
        VideoSegmentInfo segment_info(0, 0, settings_.segment_size - 1, 0, 0);
        bool begin_segment_called = false;

        try {
            LOG4CXX_INFO(logger_, log_prefix_ << "Connecting to stream at: " << settings_.stream_uri)
            sender_.SendInProgressNotification(GetTimestampMillis());

            StandardInWatcher *std_in_watcher = StandardInWatcher::GetInstance();

            int frame_interval = std::max(1, DetectionComponentUtils::GetProperty(job_.job_properties,
                                                                                  "FRAME_INTERVAL", 1));
            bool segment_activity_alert_sent = false;

            while (!std_in_watcher->QuitReceived()) {
                cv::Mat frame;
                try {
                    ReadFrame<RETRY_STRATEGY>(frame);
                }
                catch (const InterruptedException &ex) {
                    // Quit was received while trying to read frame.
                    break;
                }
                frame_number++;
                frame_timestamps[frame_number] = GetTimestampMillis();

                if (IsBeginningOfSegment(frame_number)) {
                    int segment_number = frame_number / settings_.segment_size;
                    int segment_end = frame_number + settings_.segment_size - 1;
                    segment_info = VideoSegmentInfo(segment_number, frame_number, segment_end, frame.cols, frame.rows);
                    component_.BeginSegment(segment_info);
                    begin_segment_called = true;
                    segment_activity_alert_sent = false;
                }

                if (frame_number % frame_interval == 0) {
                    bool activity_found = component_.ProcessFrame(frame, frame_number);
                    if (activity_found && !segment_activity_alert_sent) {
                        LOG4CXX_DEBUG(logger_, log_prefix_ << "Sending new activity alert for frame: " << frame_number)
                        sender_.SendActivityAlert(frame_number, frame_timestamps.at(frame_number));
                        segment_activity_alert_sent = true;
                    }
                }

                if (frame_number == segment_info.end_frame) {
                    std::vector<MPFVideoTrack> tracks = component_.EndSegment();
                    FixTracks(segment_info, tracks);
                    LOG4CXX_DEBUG(logger_, log_prefix_ << "Sending segment summary for " << tracks.size() << " tracks.")
                    sender_.SendSummaryReport(frame_number, detection_type_, tracks, frame_timestamps);
                    frame_timestamps.clear();
                }
            }
            if (frame_number != segment_info.end_frame && begin_segment_called) {
                // send the summary report if we've started, but have not completed, the next segment
                std::vector<MPFVideoTrack> tracks = component_.EndSegment();
                LOG4CXX_INFO(logger_, log_prefix_ << "Send segment summary for final segment.")
                FixTracks(segment_info, tracks);
                sender_.SendSummaryReport(frame_number, detection_type_, tracks, frame_timestamps);
            }
        }
        catch (const FatalError &ex) {
            // Send error report before actually handling exception.
            frame_timestamps.emplace(frame_number, GetTimestampMillis()); // Only inserts if key not already present.
            std::vector<MPFVideoTrack> tracks;
            if (begin_segment_called) {
                tracks = TryGetRemainingTracks();
                FixTracks(segment_info, tracks);
            }
            sender_.SendSummaryReport(frame_number, detection_type_, tracks, frame_timestamps, ex.what());
            throw;
        }
    }


    template<>
    void StreamingComponentExecutor::ReadFrame<RetryStrategy::NEVER_RETRY>(cv::Mat &frame) {
        if (!video_capture_.Read(frame)) {
            throw FatalError(ExitCode::STREAM_STALLED, "It is no longer possible to read frames.");
        }
    }

    template<>
    void StreamingComponentExecutor::ReadFrame<RetryStrategy::NO_ALERT_NO_TIMEOUT>(cv::Mat &frame) {
        video_capture_.ReadWithRetry(frame);
    }


    template<>
    void StreamingComponentExecutor::ReadFrame<RetryStrategy::NO_ALERT_WITH_TIMEOUT>(cv::Mat &frame) {
        if (!video_capture_.ReadWithRetry(frame, settings_.stall_timeout)) {
            throw FatalError(ExitCode::STREAM_STALLED, "It is no longer possible to read frames.");
        }
    }


    template<>
    void StreamingComponentExecutor::ReadFrame<RetryStrategy::ALERT_NO_TIMEOUT>(cv::Mat &frame) {
        if (video_capture_.ReadWithRetry(frame, settings_.stall_alert_threshold)) {
            return;
        }
        sender_.SendStallAlert(GetTimestampMillis());

        video_capture_.ReadWithRetry(frame);
        sender_.SendInProgressNotification(GetTimestampMillis());

    }

    template<>
    void StreamingComponentExecutor::ReadFrame<RetryStrategy::ALERT_WITH_TIMEOUT>(cv::Mat &frame) {
        if (video_capture_.ReadWithRetry(frame, settings_.stall_alert_threshold)) {
            return;
        }
        sender_.SendStallAlert(GetTimestampMillis());

        if (video_capture_.ReadWithRetry(frame, settings_.stall_timeout)) {
            sender_.SendInProgressNotification(GetTimestampMillis());
            return;
        }
        throw FatalError(ExitCode::STREAM_STALLED, "It is no longer possible to read frames.");
    }


    void StreamingComponentExecutor::FixTracks(const VideoSegmentInfo &segment_info,
            std::vector<MPFVideoTrack> &tracks) {
        ExecutorUtils::DropOutOfSegmentDetections(logger_, segment_info, tracks);
        ExecutorUtils::DropLowConfidenceDetections(confidence_threshold_, tracks);
        video_capture_.ReverseTransform(tracks);
    }


    bool StreamingComponentExecutor::IsBeginningOfSegment(int frame_number) const {
        return frame_number % settings_.segment_size == 0;
    }


    std::vector<MPFVideoTrack> StreamingComponentExecutor::TryGetRemainingTracks() {
        try {
            return component_.EndSegment();
        }
        catch (...) {
            return { };
        }
    }


    long StreamingComponentExecutor::GetTimestampMillis() {
        using namespace std::chrono;
        auto time_since_epoch = system_clock::now().time_since_epoch();
        return duration_cast<milliseconds>(time_since_epoch).count();
    }


    std::string StreamingComponentExecutor::GetAppDir() {
        char* this_exe = canonicalize_file_name("/proc/self/exe");
        if (this_exe == nullptr) {
            return ".";
        }

        std::string app_dir = dirname(this_exe); // The dirname documentation says the returned pointer must not be freed.
        free(this_exe);

        if (app_dir.empty()) {
            return ".";
        }
        return app_dir;
    }


    log4cxx::LoggerPtr StreamingComponentExecutor::GetLogger(const std::string &app_dir) {
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
}}