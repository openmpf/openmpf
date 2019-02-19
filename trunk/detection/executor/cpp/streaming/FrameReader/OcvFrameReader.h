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

#ifndef OPENMPF_OCV_FRAME_READER_H
#define OPENMPF_OCV_FRAME_READER_H

#include <map>
#include <string>
#include <vector>

#include <opencv2/opencv.hpp>
#include <opencv2/highgui.hpp>

#include <log4cxx/logger.h>

#include <MPFStreamingDetectionComponent.h>
#include "ExecutorErrors.h"
#include "JobSettings.h"
#include "StreamingVideoCapture.h"
#include "MPFMessagingConnection.h"
#include "MPFMessage.h"
#include "BasicAmqMessageReader.h"
#include "BasicAmqMessageSender.h"
#include "MPFFrameStore.h"

namespace MPF {
namespace COMPONENT {

class OcvFrameReader {

  public:
    OcvFrameReader(const log4cxx::LoggerPtr &logger,
                   const std::string &log_prefix,
                   MPF::MPFMessagingConnection &connection,
                   const MPF::COMPONENT::MPFStreamingVideoJob &job,
                   MPF::COMPONENT::JobSettings &&settings);

    ~OcvFrameReader() = default;

    MPF::COMPONENT::ExitCode RunJob();


  private:

    log4cxx::LoggerPtr logger_;
    std::string log_prefix_;
    MPF::COMPONENT::JobSettings settings_;
    MPF::BasicAmqMessageReader<MPFFrameReadyMessage> release_frame_reader_;

    MPF::BasicAmqMessageSender<MPFJobStatusMessage> job_status_sender_;
    MPF::BasicAmqMessageSender<MPFSegmentReadyMessage> stage1_segment_ready_sender_;
    MPF::BasicAmqMessageSender<MPFSegmentReadyMessage> stage2_segment_ready_sender_;
    MPF::BasicAmqMessageSender<MPFFrameReadyMessage> frame_ready_sender_;
    MPF::MPFFrameStore frame_store_;
    MPF::COMPONENT::StreamingVideoCapture video_capture_;

    template <MPF::COMPONENT::RetryStrategy RETRY_STRATEGY>
    void Run();

    template <MPF::COMPONENT::RetryStrategy RETRY_STRATEGY>
    void ReadFrame(cv::Mat &frame);

    bool ReleaseFrames();

    long GetTimestampMillis();

};
}}


#endif //OPENMPF_OCV_FRAME_READER_H
