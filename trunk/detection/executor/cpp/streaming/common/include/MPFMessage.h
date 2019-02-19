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


namespace MPF {


struct MPFSegmentSummaryMessage {
    long job_id;
    int segment_number;
    int segment_start_frame;
    int segment_stop_frame;
    std::string detection_type;  // such as: "FACE", "PERSON", "MOTION"
    std::string segment_error;
    std::vector<MPF::COMPONENT::MPFVideoTrack> tracks;
    std::unordered_map<int, long> timestamps;
    MPFSegmentSummaryMessage() = default;
    MPFSegmentSummaryMessage(const long job_id,
                             const int seg_num,
                             const int start_frame,
                             const int stop_frame,
                             const std::string &type,
                             const std::string &error,
                             const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks,
                             const std::unordered_map<int, long> &timestamps)
            : job_id(job_id)
            , segment_number(seg_num)
            , segment_start_frame(start_frame)
            , segment_stop_frame(stop_frame)
            , detection_type(type)
            , segment_error(error)
            , tracks(tracks)
            , timestamps(timestamps) {}
    ~MPFSegmentSummaryMessage() = default;
};

struct MPFActivityAlertMessage {
    long job_id;
    int frame_index;
    long activity_time;
    MPFActivityAlertMessage() = default;
    MPFActivityAlertMessage(const long job_id,
                            const int frame_num,
                            const long time)
            : job_id(job_id)
            , frame_index(frame_num)
            , activity_time(time) {}
    ~MPFActivityAlertMessage() = default;
};

struct MPFJobStatusMessage {
    long job_id;
    std::string status_message;
    long status_change_time;
    MPFJobStatusMessage() = default;
    MPFJobStatusMessage(const long job_id,
                        const std::string &msg,
                        long status_change_time)
            : job_id(job_id)
            , status_message(msg)
            , status_change_time(status_change_time) {}
    ~MPFJobStatusMessage() = default;
};


struct MPFSegmentReadyMessage {
    long job_id;
    int segment_number;
    int frame_width;
    int frame_height;
    int cvType;  // OpenCV data type specifier, e.g.., CV_8U = 0,
                 // CV_32F = 5, etc.
    int bytes_per_pixel;
    MPFSegmentReadyMessage() = default;
    MPFSegmentReadyMessage(const long job_id,
                           const int seg_num,
                           const int width,
                           const int height,
                           const int type,
                           const int bytes)
            : job_id(job_id)
            , segment_number(seg_num)
            , frame_width(width)
            , frame_height(height)
            , cvType(type)
            , bytes_per_pixel(bytes) {}
    ~MPFSegmentReadyMessage() = default;
};


struct MPFFrameReadyMessage {
    long job_id;
    int segment_number;
    int frame_index;
    long frame_timestamp;
    bool process_this_frame;
    MPFFrameReadyMessage() = default;

    MPFFrameReadyMessage(const long job_id,
                         const int seg_num,
                         const int index,
                         const long timestamp,
                         const bool process_frame_flag)
            : job_id(job_id)
            , segment_number(seg_num)
            , frame_index(index)
            , frame_timestamp(timestamp)
            , process_this_frame(process_frame_flag) {}

    ~MPFFrameReadyMessage() = default;
};  


#if 0
//TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture

struct MPFReleaseFrameMessage {
    long job_id;
    int frame_index;
    MPFReleaseFrameMessage() = default;
    MPFReleaseFrameMessage(const long job_id,
                           const int index)
            : job_id(job_id)
            , frame_index(index) {}

    ~MPFReleaseFrameMessage() = default;
};

struct MPFVideoWrittenMessage {
    long job_id;
    int segment_number;
    std::string video_output_pathname;
    MPFVideoWrittenMessage() = default;
    MPFVideoWrittenMessage(const long job_id,
                           const int seg_num,
                           const std::string &path)
            : job_id(job_id)
            , segment_number(seg_num)
            , video_output_pathname(path) {}
    ~MPFVideoWrittenMessage() = default;
};
#endif

} // namespace MPF

#endif // MPF_MESSAGE_H_
