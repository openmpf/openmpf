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

namespace MPF {

struct MPFMessage {
    std::string job_name_;
    uint32_t job_number_;
    virtual ~MPFMessage() = default;
  protected:
    MPFMessage(const std::string &job_name, const uint32_t job_number)
            : job_name_(job_name), job_number_(job_number) {}
};

struct MPFSegmentReadyMessage : MPFMessage {

    uint32_t segment_number_;
    MPFSegmentReadyMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint32_t seg_num)
            : MPFMessage(job_name, job_number),
              segment_number_(num) {}
    ~MPFSegmentReadyMessage() = default;
};

struct MPFFrameReadyMessage : MPFMessage {

    uint32_t segment_number_;
    uint32_t frame_index_;
    uint64_t frame_offset_bytes_;
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

struct MPFReleaseFrameMessage : MPFMessage {

    uint64_t frame_offset_bytes_;
    MPFReleaseFrameMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint64_t offset)
            : MPFMessage(job_name, job_number),
              frame_offset_bytes_(offset) {}
    ~MPFReleaseFrameMessage() = default;
};

struct MPFJobStatusMessage : MPFMessage {
    std::string status_message_;
    MPFJobStatusMessage(const std::string &job_name,
                        const uint32_t job_number,
                        const std::string &msg)
            : MPFMessage(job_name, job_number), status_message_(msg) {}
    ~MPFJobStatusMessage() = default;
};

struct MPFSegmentSummaryMessage : MPFMessage {
    int segment_number_;
    std::vector<MPF::COMPONENT::MPFVideoTrack> tracks_;
    MPFSegmentSummaryMessage(const std::string &job_name,
                             const uint32_t job_number,
                             int seg_num,
                             const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks)
            : MPFMessage(job_name, job_number),
              segment_number_(seg_num),
              tracks_(tracks) {}
    ~MPFSegmentSummaryMessage() = default;
};

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


} // namespace MPF

#endif // MPF_MESSAGE_H_
