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
        , connection_(connection)
        , segment_ready_reader_(settings_.segment_ready_queue, connection)
        , release_frame_sender_(settings_.release_frame_queue, connection)
        , activity_alert_sender_(settings_.activity_alert_queue, connection)
        , summary_report_sender_(settings_.summary_report_queue, connection)
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

        std::string detection_type;

        LOG4CXX_INFO(logger, log_prefix << "Loading component from: " << settings.component_lib_path);
        StreamingComponentHandle component(settings.component_lib_path, job);
        detection_type = component.GetDetectionType();

        MPFMessagingConnection connection(settings);

        SingleStageComponentExecutor executor(logger, log_prefix, connection,
                                              std::move(settings), std::move(job),
                                              std::move(component), detection_type);

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


void SingleStageComponentExecutor::Run() {
    std::unordered_map<int, long> frame_timestamps;
    bool begin_segment_called = false;
    int segment_frame_count = 0;
    int frame_to_process = 0;
    int segment_number = 0;
    VideoSegmentInfo segment_info(0, 0, 0, 0, 0);
    MPFFrameReadyMessage frame_msg;

    try {

        StandardInWatcher *std_in_watcher = StandardInWatcher::GetInstance();

        int frame_interval = std::max(1, DetectionComponentUtils::GetProperty(job_.job_properties,
                                                                              "frameInterval",
                                                                              1));
        LOG4CXX_DEBUG(logger_, "pid " << getpid() << ": frame_interval = " << frame_interval);
        bool begin_segment_called;
        bool segment_activity_alert_sent;
        bool segment_summary_report_sent;
        bool sleep_interrupted = false;
        std::chrono::milliseconds timeout_msec = settings_.message_receive_retry_interval;

        while (!std_in_watcher->QuitReceived()) {
            begin_segment_called = false;
            segment_activity_alert_sent = false;
            segment_summary_report_sent = false;
            MPFSegmentReadyMessage seg_msg;
            try {
                seg_msg = GetNextSegmentReadyMsg(timeout_msec);
            }
            catch (const InterruptedException &ex) {
                sleep_interrupted = true;
                break;
            }

            segment_number = seg_msg.segment_number;
            LOG4CXX_DEBUG(logger_, "pid " << getpid() <<  ": segment_number = " << segment_number);

            // Create a frame ready consumer for this segment and get the
            // first message
            std::string selector = "SEGMENT_NUMBER = " + std::to_string(segment_number);
            LOG4CXX_DEBUG(logger_, "pid " << getpid() <<  ": create consumer for selector " << selector);
            BasicAmqMessageReader<MPFFrameReadyMessage> frame_ready_reader(settings_.frame_ready_queue_stage1, selector, connection_);

            bool got_msg = frame_ready_reader.GetMsgNoWait(frame_msg);
            while (!got_msg) {
                got_msg = frame_ready_reader.GetMsgNoWait(frame_msg);
                std_in_watcher->InterruptibleSleep(timeout_msec);
            }

            // Check that the segment number in the frame
            // ready message is the same as the segment number we
            // just received.
            if (frame_msg.segment_number != seg_msg.segment_number) {
                throw std::runtime_error("Received frame for the wrong segment (" + std::to_string(frame_msg.segment_number) + ")");
            }

            // Initialize the frame transformer.
            cv::Size frame_size(seg_msg.frame_width, seg_msg.frame_height);
            if (nullptr == frame_transformer_) {
                frame_transformer_ = FrameTransformerFactory::GetTransformer(job_, frame_size);
            }

            int segment_end_frame = frame_msg.frame_index + settings_.segment_size - 1;
            LOG4CXX_DEBUG(logger_, "pid " << getpid() <<  ": segment_number = " << segment_number);
            LOG4CXX_DEBUG(logger_, "pid " << getpid() <<  ": frame_number = " << frame_msg.frame_index);
            LOG4CXX_DEBUG(logger_, "pid " << getpid() <<  ": end_frame = " << segment_end_frame);
            LOG4CXX_DEBUG(logger_, "pid " << getpid() <<  ": frame width = " << seg_msg.frame_width);
            LOG4CXX_DEBUG(logger_, "pid " << getpid() <<  ": frame height = " << seg_msg.frame_height);
            segment_info = {segment_number,
                            frame_msg.frame_index,
                            segment_end_frame,
                            seg_msg.frame_width,
                            seg_msg.frame_height
            };

            component_.BeginSegment(segment_info);
            begin_segment_called = true;

            // Process frames in this segment, starting with the
            // first one just read.
            int num_frames_processed = 0;
            frame_to_process = frame_msg.frame_index;
            LOG4CXX_DEBUG(logger_, "pid " << getpid() <<  ": frame_to_process = " << frame_to_process);
            while (!std_in_watcher->QuitReceived() && (frame_to_process <= segment_end_frame)) {

                frame_timestamps[frame_msg.frame_index] = frame_msg.frame_timestamp;

                // Read the frame out of the frame store
                cv::Mat frame(seg_msg.frame_height,
                              seg_msg.frame_width,
                              seg_msg.cvType);
                frame_store_.GetFrame(frame, frame_msg.frame_index);

                 // Apply transformations
                frame_transformer_->TransformFrame(frame, frame_msg.frame_index);

                // Process the frame in the component
                bool activity_found = component_.ProcessFrame(frame, frame_msg.frame_index);
                if (activity_found && !segment_activity_alert_sent) {
                    RespondToActivity(frame_msg, frame_timestamps[frame_msg.frame_index]);
                    segment_activity_alert_sent = true;
                }
                num_frames_processed++;
                frame_to_process += frame_interval;

                // Send this frame message to the release frame queue
                release_frame_sender_.SendMsg(frame_msg);

                // If we haven't reached the end of the segment
                // yet, then read the next frame
                if (frame_to_process <= segment_end_frame) {
                    try {
                        frame_msg = GetNextFrameToProcess(frame_ready_reader,
                                                          frame_to_process,
                                                          timeout_msec);
                    }
                    catch  (const InterruptedException &ex) {
                        sleep_interrupted = true;
                        break;
                    }
                }
                else {
                    // We don't need to process any more frames, but
                    // we need to take any remaining messages out of
                    // the queue so that they will be acknowledged and
                    // not end up in the DLQ instead.
                    int last_frame_processed = segment_info.start_frame +
                                               (num_frames_processed-1)*frame_interval;
                    LOG4CXX_DEBUG(logger_, "pid " << getpid() <<  ": Last frame processed = " << last_frame_processed);
                    for (int i = last_frame_processed+1; i <= segment_end_frame; i++) {
                        try {
                            frame_msg = GetNextFrameToProcess(frame_ready_reader, i, timeout_msec);
                            release_frame_sender_.SendMsg(frame_msg);
                        }
                        catch  (const InterruptedException &ex) {
                            sleep_interrupted = true;
                            break;
                        }
                    }
                }
            } // End of segment

            std::vector<MPFVideoTrack> tracks = component_.EndSegment();
            ConcludeSegment(tracks, segment_info, frame_timestamps, "");
            segment_summary_report_sent = true;
            frame_timestamps.clear();
            if (sleep_interrupted) break;
        }
        // Here if the quit signal was received
        if (begin_segment_called && !segment_summary_report_sent) {
            // send the summary report if we've started, but have not completed, the next segment
            std::vector<MPFVideoTrack> tracks = component_.EndSegment();
            ConcludeSegment(tracks, segment_info, frame_timestamps, "");
        }
    }
    catch (const FatalError &ex) {
        // Send error report before actually handling exception.
        frame_timestamps.emplace(frame_msg.frame_index, frame_msg.frame_timestamp); // Only inserts if key not already present.

        std::vector<MPFVideoTrack> tracks;
        if (begin_segment_called) {
            tracks = TryGetRemainingTracks();
            // FixTracks(segment_info, tracks);
        }
        try {
            ConcludeSegment(tracks, segment_info, frame_timestamps, ex.what());
        } catch (...) {
            LOG4CXX_ERROR(logger_, "Exception caught in ConcludeSegment while handling FatalError");
        }
        throw;
    }
}


void SingleStageComponentExecutor::RespondToActivity(MPFFrameReadyMessage &frame_msg,
                                                     long frame_timestamp) {
    LOG4CXX_DEBUG(logger_, log_prefix_ <<  ": pid " << getpid() << ": Sending new activity alert for frame: " << frame_msg.frame_index);
    MPFActivityAlertMessage alert_msg(settings_.job_id,
                                      frame_msg.frame_index,
                                      frame_timestamp);
    activity_alert_sender_.SendMsg(alert_msg);
}


void SingleStageComponentExecutor::ConcludeSegment(std::vector<MPFVideoTrack> &tracks,
                                                   VideoSegmentInfo &segment_info,
                                                   std::unordered_map<int,long> &frame_timestamps,
                                                   std::string message) {
    FixTracks(segment_info, tracks);
    LOG4CXX_DEBUG(logger_, log_prefix_ << ": pid " << getpid() << ": Sending segment summary for " << tracks.size() << " tracks.");
    MPFSegmentSummaryMessage summary_msg(settings_.job_id, segment_info.segment_number,
                                         segment_info.start_frame,
                                         segment_info.end_frame, detection_type_,
                                         message, tracks, frame_timestamps);
    summary_report_sender_.SendMsg(summary_msg);
}



void SingleStageComponentExecutor::FixTracks(const VideoSegmentInfo &segment_info,
                                             std::vector<MPFVideoTrack> &tracks) {
    ExecutorUtils::DropOutOfSegmentDetections(logger_, segment_info, tracks);
    ExecutorUtils::DropLowConfidenceDetections(confidence_threshold_, tracks);
    if (frame_transformer_ == nullptr && !tracks.empty()) {
        throw std::logic_error("Cannot apply reverse transform before reading any frames.");
    }

    for (auto &track : tracks) {
        for (auto &frame_loc_pair : track.frame_locations) {
            frame_transformer_->ReverseTransform(frame_loc_pair.second, frame_loc_pair.first);
        }
    }
}


std::vector<MPFVideoTrack> SingleStageComponentExecutor::TryGetRemainingTracks() {
    try {
        return component_.EndSegment();
    }
    catch (...) {
        return { };
    }
}
MPFSegmentReadyMessage SingleStageComponentExecutor::GetNextSegmentReadyMsg(std::chrono::milliseconds &timeout_msec){
    MPFSegmentReadyMessage seg_msg;
    StandardInWatcher *std_in_watcher = StandardInWatcher::GetInstance();
    while (true) {
        bool got_msg = segment_ready_reader_.GetMsgNoWait(seg_msg);
        if (got_msg) return (seg_msg);
        else {
            std_in_watcher->InterruptibleSleep(timeout_msec);
        }
    }
}


MPFFrameReadyMessage SingleStageComponentExecutor::GetNextFrameToProcess(
    MPF::BasicAmqMessageReader<MPFFrameReadyMessage> &reader,
    const int next_frame_index,
    const std::chrono::milliseconds &timeout_msec) {

    MPFFrameReadyMessage frame_msg;
    StandardInWatcher *std_in_watcher = StandardInWatcher::GetInstance();
    while (true) {
        bool got_msg = reader.GetMsgNoWait(frame_msg);
        if (got_msg) {
            if (frame_msg.frame_index == next_frame_index) {
                return (frame_msg);
            }
            else {
                // skipping the frame, so release it.
                release_frame_sender_.SendMsg(frame_msg);
            }
        }
        else {
            std_in_watcher->InterruptibleSleep(timeout_msec);
        }
    }
}


log4cxx::LoggerPtr SingleStageComponentExecutor::GetLogger(const std::string &app_dir) {
    std::string log_config_file = app_dir + "/../config/Log4cxx-streaming_executor-Config.xml";
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
