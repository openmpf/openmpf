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


#ifndef OPENMPF_OCV_FRAME_READER_H
#define OPENMPF_OCV_FRAME_READER_H

#include <map>
#include <string>
#include <vector>

#include <opencv2/opencv.hpp>
#include <opencv2/highgui.hpp>

#include <log4cxx/logger.h>

#include "ExecutorErrors.h"
#include "MPFFrameReader.h"
#include "StreamingVideoCapture.h"

namespace MPF {

class OcvFrameReader : public MPFFrameReader {

  public:
    OcvFrameReader(const MPFFrameReaderJob &job, log4cxx::LoggerPtr &logger)
    ~OcvFrameReader() = default;

    ExitCode RunJob(const std::string &ini_path);


  private:
    std::string job_name_;
    log4cxx::LoggerPtr logger_;
    MPFFrameStore frame_store_;
    MPF::StreamingVideoCapture video_capture_;

    int segment_length_;        // number of frames per processing
                                // segment.
    int frame_buffer_segment_capacity_; // how many segments can be
                                        // stored in the frame buffer
                                        // before we need to start
                                        // reusing storage.
    int frame_num_rows_;
    int frame_num_cols_;
    int frame_type_; // Corresponds to the OpenCV type, e.g., CV_8UC3
    size_t frame_byte_size_;     // Size of each frame in bytes
    long current_segment_num_;
    long current_frame_index_;

    bool IsBeginningOfSegment(int frame_number) const;

    template <RetryStrategy RETRY_STRATEGY>
    void Run();

    template <RetryStrategy RETRY_STRATEGY>
    void ReadFrame(cv::Mat &frame);


};
}}

#endif //OPENMPF_OCV_FRAME_READER_H
