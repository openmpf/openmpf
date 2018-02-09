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


#ifndef MPF_STREAMINGVIDEOCAPTURE_H
#define MPF_STREAMINGVIDEOCAPTURE_H


#include <string>
#include <chrono>

#include <opencv2/opencv.hpp>

#include "ExecutorUtils.h"

namespace MPF { namespace COMPONENT {


    class StreamingVideoCapture {
    public:
        StreamingVideoCapture(log4cxx::LoggerPtr &logger, const std::string &video_uri);

        bool Read(cv::Mat &frame);

        void ReadWithRetry(cv::Mat &frame);

        bool ReadWithRetry(cv::Mat &frame, const std::chrono::milliseconds &timeout);


    private:
        log4cxx::LoggerPtr logger_;

        std::string video_uri_;

        cv::VideoCapture cvVideoCapture_;


        bool DoReadRetry(cv::Mat &frame);

        void BetweenRetrySleep(const ExecutorUtils::sleep_duration_t &duration) const;
    };

}}



#endif //MPF_STREAMINGVIDEOCAPTURE_H
