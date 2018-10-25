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
#include "StandardInWatcher.h"

using std::string;
using namespace cv;
using MPF::COMPONENT::ExitCode;
using MPF::COMPONENT::FatalError;
using MPF::COMPONENT::RetryStrategy;

namespace MPF {
namespace COMPONENT {

OcvFrameReader::OcvFrameReader(const log4cxx::LoggerPtr &logger,
                               const std::string &log_prefix,
                               MPF::MPFMessagingConnection &connection,
                               const MPF::COMPONENT::MPFStreamingVideoJob &job,
                               MPF::COMPONENT::JobSettings &&settings) 
        : logger_(logger)
        , log_prefix_(log_prefix)
        , settings_(std::move(settings))
        , msg_reader_(settings_, connection.Get())
        , msg_sender_(settings_, connection.Get())
        , frame_store_(settings_)
        , video_capture_(logger_, settings_.stream_uri, job) {}



ExitCode OcvFrameReader::RunJob() {
    try {

        RetryStrategy retry_config = settings_.retry_strategy;

        switch (retry_config) {
            case RetryStrategy::NEVER_RETRY:
                Run<RetryStrategy::NEVER_RETRY>();
                break;
            case RetryStrategy::NO_ALERT_NO_TIMEOUT:
                Run<RetryStrategy::NO_ALERT_NO_TIMEOUT>();
                break;
            case RetryStrategy::NO_ALERT_WITH_TIMEOUT:
                Run<RetryStrategy::NO_ALERT_WITH_TIMEOUT>();
                break;
            case RetryStrategy::ALERT_NO_TIMEOUT:
                Run<RetryStrategy::ALERT_NO_TIMEOUT>();
                break;
            case RetryStrategy::ALERT_WITH_TIMEOUT:
                Run<RetryStrategy::ALERT_WITH_TIMEOUT>();
                break;
        }

        LOG4CXX_INFO(logger_, log_prefix_ << "Job has successfully completed because the quit command was received.");
        return ExitCode::SUCCESS;
    }
    catch (const cms::CMSException &ex) {
        LOG4CXX_ERROR(logger_, log_prefix_ << "Exiting due to message broker error: " << ex.what());
        return ExitCode::MESSAGE_BROKER_ERROR;
    }
    catch (const FatalError &ex) {
        LOG4CXX_ERROR(logger_, log_prefix_ << "Exiting due to error: " << ex.what());
        return ex.GetExitCode();
    }
    catch (const std::exception &ex) {
        LOG4CXX_ERROR(logger_, log_prefix_ << "Exiting due to error: " << ex.what());
        return ExitCode::UNEXPECTED_ERROR;
    }
    catch (...) {
        LOG4CXX_ERROR(logger_, log_prefix_ << "Exiting due to error.");
        return ExitCode::UNEXPECTED_ERROR;
    }
}


template <RetryStrategy RETRY_STRATEGY>
void OcvFrameReader::Run() {
    int frame_index = -1;
    int segment_number = -1;
    long frame_byte_size = 0;

    LOG4CXX_INFO(logger_, log_prefix_ << "Connecting to stream at: " << settings_.stream_uri);
    msg_sender_.SendInProgressNotification(GetTimestampMillis());

    StandardInWatcher *std_in_watcher = StandardInWatcher::GetInstance();

        // int frame_interval = std::max(1, DetectionComponentUtils::GetProperty(job_.job_properties,
        //                                                                       "FRAME_INTERVAL", 1));

    while (!std_in_watcher->QuitReceived()) {

        cv::Mat frame;
        try {
            ReadFrame<RETRY_STRATEGY>(frame);
        }
        catch (const InterruptedException &ex) {
            // Quit was received while trying to read frame.
            break;
        }

        frame_byte_size = frame.rows * frame.cols * frame.elemSize();
        frame_index++;
        if (frame_index % settings_.segment_size == 0) {
            // Increment the segment number and send the segment ready message.
            segment_number++;
            msg_sender_.SendSegmentReady(segment_number, frame.cols, frame.rows,
                                         frame.type(), frame_byte_size);
        }

        // Put the frame into the frame store.
        frame_store_.StoreFrame(frame, frame_index);

        // Send the frame ready message.
        msg_sender_.SendFrameReady(segment_number, frame_index, GetTimestampMillis());

        // If we have reached capacity in the frame store, then we
        // need to start deleting frames from the frame store.
        if (frame_store_.AtCapacity()) {
            MPFReleaseFrameMessage release_frame_msg;
            bool got_msg;
            while ((!std_in_watcher->QuitReceived()) &&
                   (!got_msg = msg_reader_.GetReleaseFrameMsgNoWait(release_frame_msg))) {
                continue;
            }
            if (got_msg) {
                frame_store_.DeleteFrame(release_frame_msg.frame_index);
            }
        }
    }

}



template<>
void OcvFrameReader::ReadFrame<MPF::COMPONENT::RetryStrategy::NEVER_RETRY>(cv::Mat &frame) {
    if (!video_capture_.Read(frame)) {
        throw FatalError(ExitCode::STREAM_STALLED, "It is no longer possible to read frames.");
    }
}

template<>
void OcvFrameReader::ReadFrame<MPF::COMPONENT::RetryStrategy::NO_ALERT_NO_TIMEOUT>(cv::Mat &frame) {
    video_capture_.ReadWithRetry(frame);
}


template<>
void OcvFrameReader::ReadFrame<MPF::COMPONENT::RetryStrategy::NO_ALERT_WITH_TIMEOUT>(cv::Mat &frame) {
    if (!video_capture_.ReadWithRetry(frame, settings_.stall_timeout)) {
        throw FatalError(ExitCode::STREAM_STALLED, "It is no longer possible to read frames.");
    }
}


template<>
void OcvFrameReader::ReadFrame<MPF::COMPONENT::RetryStrategy::ALERT_NO_TIMEOUT>(cv::Mat &frame) {
    if (video_capture_.ReadWithRetry(frame, settings_.stall_alert_threshold)) {
        return;
    }
    msg_sender_.SendStallAlert(GetTimestampMillis());

    video_capture_.ReadWithRetry(frame);
    msg_sender_.SendInProgressNotification(GetTimestampMillis());

}

template<>
void OcvFrameReader::ReadFrame<MPF::COMPONENT::RetryStrategy::ALERT_WITH_TIMEOUT>(cv::Mat &frame) {
    if (video_capture_.ReadWithRetry(frame, settings_.stall_alert_threshold)) {
        return;
    }
    msg_sender_.SendStallAlert(GetTimestampMillis());

    if (video_capture_.ReadWithRetry(frame, settings_.stall_timeout)) {
        msg_sender_.SendInProgressNotification(GetTimestampMillis());
        return;
    }
    throw FatalError(ExitCode::STREAM_STALLED, "It is no longer possible to read frames.");
}


long OcvFrameReader::GetTimestampMillis() {
    using namespace std::chrono;
    auto time_since_epoch = system_clock::now().time_since_epoch();
    return duration_cast<milliseconds>(time_since_epoch).count();
}

}}

