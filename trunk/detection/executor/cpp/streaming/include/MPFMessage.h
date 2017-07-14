/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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
    MPF::COMPONENT::Properties msg_properties;
  protected:
    MPFMessage(const MPF::COMPONENT::Properties &p) 
            : msg_properties(p) {}
};

// So that the other processing components can attach to the Frame
// Storage Buffer, the SegmentReady message needs to include
// parameters needed to do that in the Properties.

struct MPFSegmentReadyMessage : MPFMessage {

    int segment_number;
    MPFSegmentReadyMessage(const int num,
                           const MPF::COMPONENT::Properties &props)
            : MPFMessage(props),
              segment_number(num) {}
};

struct MPFFrameReadyMessage : MPFMessage {

    int segment_number;
    int frame_index;
    uint64_t frame_offset_bytes;
    MPFFrameReadyMessage(const int num,
                         const int index,
                         const uint64_t offset,
                         const MPF::COMPONENT::Properties &props) 
            : MPFMessage(props),
              segment_number(num),
              frame_index(index),
              frame_offset_bytes(offset) {}
};  

struct MPFReleaseFrameMessage : MPFMessage {

    uint64_t frame_offset_bytes;
    MPFReleaseFrameMessage(const uint64_t offset,
                         const MPF::COMPONENT::Properties &props) 
            : MPFMessage(props),
              frame_offset_bytes(offset) {}
};

struct MPFJobStatusMessage : MPFMessage {
    std::string status_message;
    MPFJobStatusMessage(const std::string &msg,
                        const MPF::COMPONENT::Properties &props) 
            : MPFMessage(props), status_message(msg) {}
};

struct MPFSegmentSummaryMessage : MPFMessage {
    int segment_number;
    std::vector<MPF::COMPONENT::MPFVideoTrack> tracks;
    MPFSegmentSummaryMessage(int n,
                             const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks,
                             const MPF::COMPONENT::Properties &props) 
            : MPFMessage(props),
              segment_number(n),
              tracks(tracks) {}
};

struct MPFVideoWrittenMessage : MPFMessage {
    int segment_number;
    std::string video_output_pathname;
    MPFVideoWrittenMessage(int n,
                        const std::string path,
                        const MPF::COMPONENT::Properties &props) 
            : MPFMessage(props),
              segment_number(n),
              video_output_pathname(path) {}
};

class MPFReceiver {
  public:
    virtual ~MPFReceiver() = default;
    virtual MPFMessage* getMessage() = 0;
  protected:
    MPFReceiver() = default;
    std::string queue_name_;
};


class MPFSender {
  public:
    virtual ~MPFSender() = default;
    virtual void putMessage(MPFMessage *msg) = 0;
  protected:
    MPFSender() = default;
    std::string queue_name_;
};


} // namespace MPF

#endif // MPF_MESSAGE_H_
