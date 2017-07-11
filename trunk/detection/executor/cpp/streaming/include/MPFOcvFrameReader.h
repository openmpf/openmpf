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



#ifndef OPENMPF_COMPONENTS_OCVFRAMEREADER_H
#define OPENMPF_COMPONENTS_OCVFRAMEREADER_H

#include <map>
#include <string>
#include <vector>

#include <opencv2/opencv.hpp>
#include <opencv2/highgui.hpp>

#include "MPFFrameReader.h"

namespace MPF { namespace COMPONENT {

class OcvFrameReaderComponent : public MPFFrameReaderComponent {

  public:
    OcvFrameReaderComponent() = default;
    ~OcvFrameReaderComponent() {
        CloseFrameStore();
    }

    virtual MPFFrameReaderError AttachToStream(
                                           const MPFFrameReaderJob &job,
                                           Properties &stream_properties) override;
    virtual MPFFrameReaderError CloseFrameBuffer(
                                           MPFFrameBuffer* MPFFrameBuffer) override;
    virtual MPFFrameReaderError CreateFrameStore(MPFFrameReaderJob &job,
                                                 Properties &stream_properties) override;
    virtual MPFFrameReaderError CloseFrameStore() override;
    virtual MPFFrameReaderError Run(MPFFrameReaderJob &job) override;

  private:
    cv::VideoCapture video_capture_;
    uint8_t *frame_buffer_start_addr_;  // virtual address of the
                                        // start of the frame buffer.
    int num_frames_in_segment_;
    int frame_buffer_segment_capacity_; // how many segments can be
                                        // stored in the frame buffer
                                        // before we need to start
                                        // reusing storage.
    int frame_rows_;
    int frame_cols_;
    int frame_type_; // Corresponds to the OpenCV type, e.g., CV_8UC3
    size_t frame_byte_size_;
    size_t frame_buffer_byte_size_;
    int frame_buffer_file_descriptor_;
    int num_segments_;
    std::string frame_buffer_name;
    MPFFrameStore frame_store_;

    SendSegmentJobMessage();
    SendFrameMessage();
    GetFreeFrameMessage();
};
}}

#endif //OPENMPF_COMPONENTS_OCVFRAMEREADER_H
