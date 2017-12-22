/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

#include "MPFDetectionComponent.h"
#include "MPFMessageUtils.h"

namespace MPF {

struct MPFMessage {
    std::string job_name_;
    uint32_t job_number_;
    virtual ~MPFMessage() = default;
  protected:
    MPFMessage() : job_name_(""), job_number_(0) {}
    MPFMessage(const std::string &job_name, const uint32_t job_number)
            : job_name_(job_name), job_number_(job_number) {}
};


struct MPFSegmentSummaryMessage : MPFMessage {
    int segment_number_;
    int segment_start_frame_;
    int segment_stop_frame_;
    std::string detection_type_;  // such as: "FACE", "PERSON", "MOTION"
    MPF::COMPONENT::MPFDetectionError segment_error_;
    std::vector<MPF::COMPONENT::MPFVideoTrack> tracks_;
    MPFSegmentSummaryMessage() = default;
    MPFSegmentSummaryMessage(const std::string &job_name,
                             const uint32_t job_number,
                             const int seg_num,
                             const int start_frame,
                             const int stop_frame,
                             const std::string type,
                             MPF::COMPONENT::MPFDetectionError error,
                             const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks)
            : MPFMessage(job_name, job_number),
              segment_number_(seg_num),
              segment_start_frame_(start_frame),
              segment_stop_frame_(stop_frame),
              detection_type_(type),
              segment_error_(error),
              tracks_(tracks) {}
    ~MPFSegmentSummaryMessage() = default;
};

struct MPFActivityAlertMessage : MPFMessage {
    uint32_t segment_number_;
    uint32_t frame_index_;
    long activity_time_;
    MPFActivityAlertMessage() = default;
    MPFActivityAlertMessage(const std::string &job_name,
                            const uint32_t job_number,
                            const uint32_t seg_num,
                            const uint32_t frame_num,
                            const long time)
            : MPFMessage(job_name, job_number),
              segment_number_(seg_num),
              frame_index_(frame_num),
              activity_time_(time) {}
    ~MPFActivityAlertMessage() = default;
};

struct MPFJobStatusMessage : MPFMessage {
    std::string status_message_;
    long status_change_time_;
    MPFJobStatusMessage() = default;
    MPFJobStatusMessage(const std::string &job_name,
                        const uint32_t job_number,
                        const std::string &msg,
                        long status_change_time)
            : MPFMessage(job_name, job_number), status_message_(msg), status_change_time_(status_change_time) {}
    ~MPFJobStatusMessage() = default;
};

/****************************************************************/
//TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture
#if 0
struct MPFSegmentReadyMessage : MPFMessage {

    uint32_t segment_number_;
    MPFSegmentReadyMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint32_t seg_num)
            : MPFMessage(job_name, job_number),
              segment_number_(seg_num) {}
    ~MPFSegmentReadyMessage() = default;
};

//TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture
struct MPFFrameReadyMessage : MPFMessage {

    uint32_t segment_number_;
    uint32_t frame_index_;
    uint64_t frame_offset_bytes_;
    MPFFrameReadyMessage() : segment_number_(0), frame_index_(0), frame_offset_bytes_(0) {}
    MPFFrameReadyMessage(const std::string &job_name,
                         const uint32_t job_number,
                         const uint32_t seg_num,
                         const uint32_t index,
                         const uint64_t offset)
            : MPFMessage(job_name, job_number),
              segment_number_(seg_num),
              frame_index_(index),
              frame_offset_bytes_(offset) {}
    ~MPFFrameReadyMessage() = default;
};  

//TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture
struct MPFReleaseFrameMessage : MPFMessage {

    uint64_t frame_offset_bytes_;
    MPFReleaseFrameMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint64_t offset)
            : MPFMessage(job_name, job_number),
              frame_offset_bytes_(offset) {}
    ~MPFReleaseFrameMessage() = default;
};

//TODO: For future use. Untested.
// Not used in single process, single pipeline stage, architecture
struct MPFVideoWrittenMessage : MPFMessage {
    int segment_number_;
    std::string video_output_pathname_;
    MPFVideoWrittenMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint32_t seg_num,
                           const std::string &path)
            : MPFMessage(job_name, job_number),
              segment_number_(seg_num),
              video_output_pathname_(path) {}
    ~MPFVideoWrittenMessage() = default;
};
#endif

} // namespace MPF

#endif // MPF_MESSAGE_H_
