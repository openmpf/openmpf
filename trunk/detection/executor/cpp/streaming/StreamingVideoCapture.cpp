/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

#include <stdexcept>

#include <frame_transformers/FrameTransformerFactory.h>

#include "ExecutorErrors.h"
#include "ExecutorUtils.h"
#include "StandardInWatcher.h"

#include "StreamingVideoCapture.h"


namespace MPF { namespace COMPONENT {

    StreamingVideoCapture::StreamingVideoCapture(const log4cxx::LoggerPtr &logger, const std::string &video_uri,
                                                 const MPFStreamingVideoJob &job)
            : logger_(logger)
            , video_uri_(video_uri)
            , job_(job)
            , cv_video_capture_(video_uri_) {

        if (!cv_video_capture_.isOpened()) {
            throw FatalError(ExitCode::UNABLE_TO_CONNECT_TO_STREAM,
                             "Unable to connect to stream: " + video_uri);
        }
    }


    bool StreamingVideoCapture::Read(cv::Mat &frame) {
        return (this->*current_read_impl_)(frame);
    }


    bool StreamingVideoCapture::ReadAndInitialize(cv::Mat &frame) {
        if (cv_video_capture_.read(frame)) {
            frame_transformer_ = FrameTransformerFactory::GetTransformer(job_, frame.size());
            frame_transformer_->TransformFrame(frame, 0);
            // Now that everything is initialized, make Read() call DefaultRead from now on.
            current_read_impl_ = &StreamingVideoCapture::DefaultRead;
            return true;
        }
        return false;
    }


    bool StreamingVideoCapture::DefaultRead(cv::Mat &frame) {
        if (cv_video_capture_.read(frame)) {
            // TODO: Pass in frame number instead of zero.
            // Passing in zero won't hurt for now since the frame number is only used for feed forward.
            frame_transformer_->TransformFrame(frame, 0);
            return true;
        }
        return false;
    }


    void StreamingVideoCapture::ReadWithRetry(cv::Mat &frame) {
        if (Read(frame)) {
            return;
        }

        LOG4CXX_WARN(logger_, "Failed to read frame. Will retry forever.")
        ExecutorUtils::RetryWithBackOff(
                [this, &frame] {
                    return DoReadRetry(frame);
                },
                [this] (const ExecutorUtils::sleep_duration_t &duration) {
                    BetweenRetrySleep(duration);
                }
        );
    }


    bool StreamingVideoCapture::ReadWithRetry(cv::Mat &frame, const std::chrono::milliseconds &timeout) {
        if (Read(frame)) {
            return true;
        }

        using namespace std::chrono;
        if (timeout <= milliseconds::zero()) {
            return false;
        }

        LOG4CXX_WARN(logger_, "Failed to read frame. Will retry for up to " << timeout.count() << " ms. ");

        return ExecutorUtils::RetryWithBackOff(
                timeout,
                [this, &frame] {
                    return DoReadRetry(frame);
                },
                [this] (const ExecutorUtils::sleep_duration_t &duration) {
                    BetweenRetrySleep(duration);
                });
    }


    bool StreamingVideoCapture::DoReadRetry(cv::Mat &frame) {
        bool reopened = cv_video_capture_.open(video_uri_);
        if (!reopened) {
            LOG4CXX_WARN(logger_, "Failed to re-connect to video stream.");
            return false;
        }

        LOG4CXX_WARN(logger_, "Successfully re-connected to video stream.");

        bool was_read = Read(frame);
        if (was_read) {
            LOG4CXX_WARN(logger_, "Successfully read frame after re-connecting to video stream.");
            return true;
        }

        LOG4CXX_WARN(logger_, "Failed to read frame after successfully re-connecting to video stream.");
        return false;
    }


    void StreamingVideoCapture::BetweenRetrySleep(const ExecutorUtils::sleep_duration_t &duration) const {
        using namespace std::chrono;

        LOG4CXX_WARN(logger_, "Sleeping for " << duration_cast<milliseconds>(duration).count()
                                              << " ms before trying to read frame again.");

        StandardInWatcher::GetInstance()->InterruptibleSleep(duration);
    }


    void StreamingVideoCapture::ReverseTransform(std::vector<MPFVideoTrack> &tracks) const {
        if (frame_transformer_ == nullptr && !tracks.empty()) {
            throw std::logic_error("Cannot apply reverse transform before reading any frames.");
        }

        for (auto &track : tracks) {
            for (auto &frame_loc_pair : track.frame_locations) {
                frame_transformer_->ReverseTransform(frame_loc_pair.second, frame_loc_pair.first);
            }
        }
    }
}}
