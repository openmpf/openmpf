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

//TODO: All functions in this file are for future use and are untested.
// Not used in single process, single pipeline stage, architecture

#include "OcvFrameReader.h"
#include "JobSettings.h"
#include "detectionComponentUtils.h"

using namespace MPF;
using namespace COMPONENT;
using std::string;
using namespace cv;

OcvFrameReader::OcvFrameReader(const MPFFrameReaderJob &job, log4cxx::LoggerPtr &logger) 
        : job_name_(job.job_name)
        , logger_(logger)
        , frame_store_(job.job_properties)
        , video_capture_(logger, job) {}



ExitCode OcvFrameReader::RunJob(const std::string &ini_path) {
    std::string app_dir = ExecutorUtils::GetAppDir();
    std::string config_file = app_dir + "/../config/Log4cxx-framereader-Config.xml";
    log4cxx::xml::DOMConfigurator::configure(config_file);
    log4cxx::LoggerPtr logger = log4cxx::Logger::getLogger("org.mitre.mpf.framereader");
    if (logger->getAllAppenders().empty()) {
        log4cxx::BasicConfigurator::configure();
        LOG4CXX_WARN(logger, "Unable to load log configuration file at "
                     << config_file << ". Logging to standard out instead.");
    }

    std::string log_prefix;
    try {
        LOG4CXX_INFO(logger, "Loading job settings from: " << ini_path);

        JobSettings settings = JobSettings::FromIniFile(ini_path);
        std::string job_name = "Streaming Job #" + std::to_string(settings.job_id);
        LOG4CXX_INFO(logger, "Initializing " << job_name);

        log_prefix = "[" + job_name + "] ";
        MPFFrameReaderJob job(job_name, settings.stream_uri, 
                              settings.job_properties,
                              settings.media_properties);

        RetryStrategy retry_config = settings.retry_strategy;
        OcvFrameReader frame_reader(job, logger);

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

template <RetryStrategy RETRY_STRATEGY>
void OcvFrameReader::Run() {
    int frame_number = -1;
    std::unordered_map<int, long> frame_timestamps;

    try {
        LOG4CXX_INFO(logger_, log_prefix_ << "Connecting to stream at: " << settings_.stream_uri);
        sender_.SendInProgressNotification(GetTimestampMillis());

        StandardInWatcher *std_in_watcher = StandardInWatcher::GetInstance();

        int frame_interval = std::max(1, DetectionComponentUtils::GetProperty(job_.job_properties, "FRAME_INTERVAL", 1));

        while (!std_in_watcher->QuitReceived()) {
            cv::Mat frame;
            try {
                ReadFrame<RETRY_STRATEGY>(frame);
            }
            catch (const InterruptedException &ex) {
                // Quit was received while trying to read frame.
                break;
            }
            if (frame_number < 0) {
                //Set a bunch of member variables based on the 
            frame_number++;
            frame_timestamps[frame_number] = GetTimestampMillis();

            if (IsBeginningOfSegment(frame_number)) {
                int segment_number = frame_number / settings_.segment_size;
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
void OcvFrameReader::ReadFrame<RetryStrategy::NEVER_RETRY>(cv::Mat &frame) {
    if (!video_capture_.Read(frame)) {
        throw FatalError(ExitCode::STREAM_STALLED, "It is no longer possible to read frames.");
    }
}

template<>
void OcvFrameReader::ReadFrame<RetryStrategy::NO_ALERT_NO_TIMEOUT>(cv::Mat &frame) {
    video_capture_.ReadWithRetry(frame);
}


template<>
void OcvFrameReader::ReadFrame<RetryStrategy::NO_ALERT_WITH_TIMEOUT>(cv::Mat &frame) {
    if (!video_capture_.ReadWithRetry(frame, settings_.stall_timeout)) {
        throw FatalError(ExitCode::STREAM_STALLED, "It is no longer possible to read frames.");
    }
}


template<>
void OcvFrameReader::ReadFrame<RetryStrategy::ALERT_NO_TIMEOUT>(cv::Mat &frame) {
    if (video_capture_.ReadWithRetry(frame, settings_.stall_alert_threshold)) {
        return;
    }
    sender_.SendStallAlert(GetTimestampMillis());

    video_capture_.ReadWithRetry(frame);
    sender_.SendInProgressNotification(GetTimestampMillis());

}

template<>
void OcvFrameReader::ReadFrame<RetryStrategy::ALERT_WITH_TIMEOUT>(cv::Mat &frame) {
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



    bool OcvFrameReader::IsBeginningOfSegment(int frame_number) const {
        return frame_number % settings_.segment_size == 0;
    }
