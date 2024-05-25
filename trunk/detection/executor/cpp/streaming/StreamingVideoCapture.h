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


#ifndef MPF_STREAMINGVIDEOCAPTURE_H
#define MPF_STREAMINGVIDEOCAPTURE_H


#include <chrono>
#include <string>
#include <vector>

#include <opencv2/opencv.hpp>

#include <MPFStreamingDetectionComponent.h>
#include <frame_transformers/IFrameTransformer.h>

#include "ExecutorUtils.h"

namespace MPF { namespace COMPONENT {


    class StreamingVideoCapture {
    public:
        StreamingVideoCapture(const log4cxx::LoggerPtr &logger, const std::string &video_uri,
                              const MPFStreamingVideoJob &job);

        bool Read(cv::Mat &frame);

        void ReadWithRetry(cv::Mat &frame);

        bool ReadWithRetry(cv::Mat &frame, const std::chrono::milliseconds &timeout);

        void ReverseTransform(std::vector<MPFVideoTrack> &track) const;


    private:
        log4cxx::LoggerPtr logger_;

        const MPFStreamingVideoJob job_;

        std::string video_uri_;

        cv::VideoCapture cv_video_capture_;

        // Points to ReadAndInitialize until the first frame is read. Then, it will point to DefaultRead
        bool (StreamingVideoCapture::*current_read_impl_)(cv::Mat &frame) = &StreamingVideoCapture::ReadAndInitialize;

        IFrameTransformer::Ptr frame_transformer_;

        bool ReadAndInitialize(cv::Mat &frame);

        bool DefaultRead(cv::Mat &frame);

        bool DoReadRetry(cv::Mat &frame);

        void BetweenRetrySleep(const ExecutorUtils::sleep_duration_t &duration) const;
    };

}}



#endif //MPF_STREAMINGVIDEOCAPTURE_H
