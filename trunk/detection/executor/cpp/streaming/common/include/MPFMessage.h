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

#ifndef MPF_MESSAGE_H_
#define MPF_MESSAGE_H_

#include <string>
#include <unordered_map>

#include "MPFDetectionObjects.h"
// #include "MPFMessageUtils.h"


namespace MPF {

struct MPFMessage {
    std::string job_name;
    int job_number;
    virtual ~MPFMessage() = default;
  protected:
    MPFMessage() : job_name(""), job_number(0) {}
    MPFMessage(const std::string &job_name, const int job_number)
            : job_name(job_name), job_number(job_number) {}
};


struct MPFSegmentSummaryMessage : MPFMessage {
    int segment_number;
    long segment_start_frame;
    long segment_stop_frame;
    std::string detection_type;  // such as: "FACE", "PERSON", "MOTION"
    std::string segment_error;
    std::vector<MPF::COMPONENT::MPFVideoTrack> tracks;
    std::unordered_map<int, long> timestamps;
    MPFSegmentSummaryMessage() = default;
    MPFSegmentSummaryMessage(const std::string &job_name,
                             const int job_number,
                             const int seg_num,
                             const int start_frame,
                             const int stop_frame,
                             const std::string &type,
                             const std::string &error,
                             const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks,
                             const std::unordered_map<int, long> &timestamps)
            : MPFMessage(job_name, job_number),
              segment_number(seg_num),
              segment_start_frame(start_frame),
              segment_stop_frame(stop_frame),
              detection_type(type),
              segment_error(error),
              tracks(tracks),
              timestamps(timestamps) {}
    ~MPFSegmentSummaryMessage() = default;
};

struct MPFActivityAlertMessage : MPFMessage {
    int segment_number;
    long frame_index;
    long activity_time;
    MPFActivityAlertMessage() = default;
    MPFActivityAlertMessage(const std::string &job_name,
                            const int job_number,
                            const int seg_num,
                            const int frame_num,
                            const long time)
            : MPFMessage(job_name, job_number),
              segment_number(seg_num),
              frame_index(frame_num),
              activity_time(time) {}
    ~MPFActivityAlertMessage() = default;
};

struct MPFJobStatusMessage : MPFMessage {
    std::string status_message;
    long status_change_time;
    MPFJobStatusMessage() = default;
    MPFJobStatusMessage(const std::string &job_name,
                        const int job_number,
                        const std::string &msg,
                        long status_change_time)
            : MPFMessage(job_name, job_number), status_message(msg), status_change_time(status_change_time) {}
    ~MPFJobStatusMessage() = default;
};


struct MPFSegmentReadyMessage : MPFMessage {

    int segment_number;
    int frame_width;
    int frame_height;
    int cvType;  // OpenCV data type specifier, e.g.., CV_8U = 0,
                 // CV_32F = 5, etc.
    int bytes_per_pixel;
    MPFSegmentReadyMessage(const std::string &job_name,
                           const int job_number,
                           const int seg_num)
            : MPFMessage(job_name, job_number),
              segment_number(seg_num) {}
    ~MPFSegmentReadyMessage() = default;
};


struct MPFFrameReadyMessage : MPFMessage {

    int segment_number;
    int frame_index;
    long frame_offset;
    long frame_timestamp;
    MPFFrameReadyMessage() : segment_number(0), frame_index(0), frame_offset(0), frame_timestamp(0) {}
    MPFFrameReadyMessage(const std::string &job_name,
                         const int job_number,
                         const int seg_num,
                         const int index,
                         const long offset,
                         const long timestamp)
            : MPFMessage(job_name, job_number),
              segment_number(seg_num),
              frame_index(index),
              frame_offset(offset),
              frame_timestamp(timestamp) {}
    ~MPFFrameReadyMessage() = default;
};  


struct MPFReleaseFrameMessage : MPFMessage {

    long frame_index;
    long frame_offset;
    MPFReleaseFrameMessage() : MPFMessage(0, 0)
                             , frame_index(0)
                             , frame_offset(0) {}

    MPFReleaseFrameMessage(const std::string &job_name,
                           const int job_number,
                           const long frame_index,
                           const long offset)
            : MPFMessage(job_name, job_number)
            , frame_index(frame_index)
            , frame_offset(offset) {}

    ~MPFReleaseFrameMessage() = default;
};

#if 0
//TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture
struct MPFVideoWrittenMessage : MPFMessage {
    int segment_number;
    std::string video_output_pathname;
    MPFVideoWrittenMessage(const std::string &job_name,
                           const int job_number,
                           const int seg_num,
                           const std::string &path)
            : MPFMessage(job_name, job_number),
              segment_number(seg_num),
              video_output_pathname(path) {}
    ~MPFVideoWrittenMessage() = default;
};
#endif

} // namespace MPF

#endif // MPF_MESSAGE_H_
