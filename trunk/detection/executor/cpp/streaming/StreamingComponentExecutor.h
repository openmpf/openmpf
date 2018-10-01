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


#ifndef MPF_STREAMINGCOMPONENTEXECUTOR_H
#define MPF_STREAMINGCOMPONENTEXECUTOR_H

#include <string>
#include <log4cxx/logger.h>

#include "ExecutorErrors.h"
#include "StreamingComponentHandle.h"
#include "BasicAmqMessageSender.h"
#include "StreamingVideoCapture.h"


namespace MPF { namespace COMPONENT {

    class StreamingComponentExecutor {

    public:
        static ExitCode RunJob(const std::string &ini_path);


    private:
        log4cxx::LoggerPtr logger_;

        const std::string log_prefix_;

        JobSettings settings_;

        BasicAmqMessageSender sender_;

        MPFStreamingVideoJob job_;

        StreamingComponentHandle component_;

        // StreamingVideoCapture video_capture_;

        const std::string detection_type_;

        const double confidence_threshold_;


        StreamingComponentExecutor(
                const log4cxx::LoggerPtr &logger,
                const std::string &log_prefix,
                JobSettings &&settings,
                BasicAmqMessageSender &&sender,
                MPFStreamingVideoJob &&job,
                StreamingComponentHandle &&component,
                const std::string &detection_type);



        static StreamingComponentExecutor Create(
                const log4cxx::LoggerPtr &logger, const std::string &log_prefix,
                JobSettings &&settings, MPFStreamingVideoJob &&job);


        template <RetryStrategy RETRY_STRATEGY>
        void Run();

        // template <RetryStrategy RETRY_STRATEGY>
        // void ReadFrame(cv::Mat &frame);

        void FixTracks(const VideoSegmentInfo &segment_info,
                       std::vector<MPFVideoTrack> &tracks);

        bool IsBeginningOfSegment(int frame_number) const;

        std::vector<MPFVideoTrack> TryGetRemainingTracks();

        static long GetTimestampMillis();

        static std::string GetAppDir();

        static log4cxx::LoggerPtr GetLogger(const std::string &app_dir);
    };

}}



#endif //MPF_STREAMINGCOMPONENTEXECUTOR_H
