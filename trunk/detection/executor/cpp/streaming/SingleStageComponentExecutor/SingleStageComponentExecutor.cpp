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

#include "SingleStageComponentExecutor.h"
#include "ExecutorUtils.h"
#include "StandardInWatcher.h"


namespace MPF { namespace COMPONENT {

SingleStageComponentExecutor::SingleStageComponentExecutor(
    const log4cxx::LoggerPtr &logger,
    const std::string &log_prefix,
    MPFMessagingConnection &connection,
    JobSettings &&settings,
    MPFStreamingVideoJob &&job,
    StreamingComponentHandle &&component,
    const std::string &detection_type)
        : logger_(logger)
        , log_prefix_(log_prefix)
        , settings_(std::move(settings))
        , frame_store_(settings_)
        , msg_reader_(settings_, connection.Get())
        , msg_sender_(settings_, connection.Get())
        , job_(std::move(job))
        , component_(std::move(component))
        , detection_type_(detection_type)
        , confidence_threshold_(DetectionComponentUtils::GetProperty(
            settings_.job_properties,
            "CONFIDENCE_THRESHOLD",
            ExecutorUtils::LOWEST_CONFIDENCE_THRESHOLD)) {}


    ExitCode SingleStageComponentExecutor::RunJob(const std::string &ini_path) {
        std::string app_dir = ExecutorUtils::GetAppDir();
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

            std::string detection_type;

            LOG4CXX_INFO(logger, log_prefix << "Loading component from: " << settings.component_lib_path);
            StreamingComponentHandle component(settings.component_lib_path, job);
            detection_type = component.GetDetectionType();

            MPFMessagingConnection connection(settings);

            SingleStageComponentExecutor executor(logger, log_prefix, connection,
                                                  std::move(settings), std::move(job),
                                                  std::move(component), detection_type);
            // SingleStageComponentExecutor executor = Create(logger, log_prefix, connection,
            //                                                std::move(settings), std::move(job));

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
            LOG4CXX_INFO(logger, log_prefix << "Job has successfully completed because the quit command was received.");

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


    // SingleStageComponentExecutor SingleStageComponentExecutor::Create(
    //         const log4cxx::LoggerPtr &logger, const std::string &log_prefix,
    //         const MPFMessagingConnection &connection,
    //         JobSettings &&settings, MPFStreamingVideoJob &&job) {

    //     std::string detection_type;
    //     try {
    //         LOG4CXX_INFO(logger, log_prefix << "Loading component from: " << settings.component_lib_path);
    //         StreamingComponentHandle component(settings.component_lib_path, job);
    //         detection_type = component.GetDetectionType();

    //         return SingleStageComponentExecutor(
    //                 logger,
    //                 log_prefix,
    //                 connection,
    //                 std::move(settings),
    //                 std::move(job),
    //                 std::move(component),
    //                 detection_type);
    //     }
    //     catch (const FatalError &ex) {
    //         sender.SendSummaryReport(0, detection_type, {}, {}, ex.what());
    //         throw;
    //     }

    // }


    template <RetryStrategy RETRY_STRATEGY>
    void SingleStageComponentExecutor::Run() {
        std::unordered_map<int, long> frame_timestamps;
        bool begin_segment_called = false;
        int frame_count = 0;
        VideoSegmentInfo segment_info(0, 0, 0, 0, 0);
        MPFFrameReadyMessage frame_msg;
        try {
            // LOG4CXX_INFO(logger_, log_prefix_ << "Connecting to stream at: " << settings_.stream_uri)
            // sender_.SendInProgressNotification(GetTimestampMillis());

            StandardInWatcher *std_in_watcher = StandardInWatcher::GetInstance();

            // int frame_interval = std::max(1, DetectionComponentUtils::GetProperty(job_.job_properties,
            //                                                                       "FRAME_INTERVAL",
            //                                                                       1));
            bool begin_segment_called = false;
            bool segment_activity_alert_sent = false;

            while (!std_in_watcher->QuitReceived()) {
                MPFSegmentReadyMessage seg_msg = msg_reader_.GetSegmentReadyMsg();
                
                frame_msg = msg_reader_.CreateFrameReadyConsumer(seg_msg.segment_number).GetFrameReadyMsg();
                // Check that the segment number in the frame
                // ready message is the same as the segment number we
                // just received.
                assert (frame_msg.segment_number == seg_msg.segment_number);

                int segment_end_frame = frame_msg.frame_index + settings_.segment_size - 1;
                segment_info = {seg_msg.segment_number,
                                frame_msg.frame_index,
                                segment_end_frame,
                                seg_msg.frame_width,
                                seg_msg.frame_height
                };
                

                component_.BeginSegment(segment_info);
                begin_segment_called = true;
                segment_activity_alert_sent = false;

                frame_timestamps[frame_msg.frame_index] = frame_msg.frame_timestamp;

                frame_count = 0;
                while (!std_in_watcher->QuitReceived() && (frame_count < settings_.segment_size)) {
                    frame_count++;
                    // Read the frame out of the frame store
                    cv::Mat frame;
                    frame_store_.GetFrame(frame, frame_msg.frame_index);
                    bool activity_found = component_.ProcessFrame(frame, frame_msg.frame_index);
                    if (activity_found && !segment_activity_alert_sent) {
                        LOG4CXX_DEBUG(logger_, log_prefix_ << "Sending new activity alert for frame: " << frame_msg.frame_index);
                        msg_sender_.SendActivityAlert(frame_msg.frame_index,
                                                      frame_timestamps.at(frame_msg.frame_index));
                        segment_activity_alert_sent = true;
                    }
                    // Send release frame message
                    msg_sender_.SendReleaseFrame(frame_msg.frame_index);
                    // Read the next frame
                    frame_msg = msg_reader_.GetFrameReadyMsg();
                }

                if (frame_count == settings_.segment_size) {
                    std::vector<MPFVideoTrack> tracks = component_.EndSegment();
                    FixTracks(segment_info, tracks);
                    LOG4CXX_DEBUG(logger_, log_prefix_ << "Sending segment summary for " << tracks.size() << " tracks.")
                    msg_sender_.SendSummaryReport(frame_msg.frame_index, detection_type_,
                                                  tracks, frame_timestamps);
                    frame_timestamps.clear();
                }
            }
            if (frame_count != settings_.segment_size && begin_segment_called) {
                // send the summary report if we've started, but have not completed, the next segment
                std::vector<MPFVideoTrack> tracks = component_.EndSegment();
                LOG4CXX_INFO(logger_, log_prefix_ << "Send segment summary for final segment.")
                FixTracks(segment_info, tracks);
                msg_sender_.SendSummaryReport(frame_msg.frame_index, detection_type_, tracks, frame_timestamps);
            }
        }
        catch (const FatalError &ex) {
            // Send error report before actually handling exception.
            frame_timestamps.emplace(frame_msg.frame_index, frame_msg.frame_timestamp); // Only inserts if key not already present.
            std::vector<MPFVideoTrack> tracks;
            if (begin_segment_called) {
                tracks = TryGetRemainingTracks();
                FixTracks(segment_info, tracks);
            }
            msg_sender_.SendSummaryReport(frame_msg.frame_index, detection_type_,
                                          tracks, frame_timestamps, ex.what());
            throw;
        }
    }


    void SingleStageComponentExecutor::FixTracks(const VideoSegmentInfo &segment_info,
            std::vector<MPFVideoTrack> &tracks) {
        ExecutorUtils::DropOutOfSegmentDetections(logger_, segment_info, tracks);
        ExecutorUtils::DropLowConfidenceDetections(confidence_threshold_, tracks);
        // video_capture_.ReverseTransform(tracks);
    }


    // bool SingleStageComponentExecutor::IsBeginningOfSegment(int frame_number) const {
    //     return frame_number % settings_.segment_size == 0;
    // }


    std::vector<MPFVideoTrack> SingleStageComponentExecutor::TryGetRemainingTracks() {
        try {
            return component_.EndSegment();
        }
        catch (...) {
            return { };
        }
    }


    log4cxx::LoggerPtr SingleStageComponentExecutor::GetLogger(const std::string &app_dir) {
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
