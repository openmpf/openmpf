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



#ifndef OPENMPF_OCVFRAMEREADER_H
#define OPENMPF_OCVFRAMEREADER_H

#include <map>
#include <string>
#include <vector>

#include <opencv2/opencv.hpp>
#include <opencv2/highgui.hpp>

#include <log4cxx/logger.h>

#include "MPFFrameReader.h"

namespace MPF { namespace COMPONENT {

class OcvFrameReader : public MPFFrameReader {

  public:
    OcvFrameReader(log4cxx::LoggerPtr &logger) : logger_(logger) {}
    ~OcvFrameReader() {
        CloseFrameStore();
    }

    virtual MPFFrameReaderError AttachToStream(
                              const MPFFrameReaderJob &job,
                              Properties &stream_properties) override;
    virtual MPFFrameReaderError OpenFrameStore(
                              MPFFrameReaderJob &job,
                              Properties &stream_properties) override;
    virtual MPFFrameReaderError CloseFrameStore() override;
    virtual MPFFrameReaderError ReadAndStoreFrame(MPFFrameReaderJob &job,
                                                  const size_t offset) override;


  private:
    cv::VideoCapture video_capture_;
    log4cxx::LoggerPtr logger_;
    int segment_length_;        // number of frames per processing
                                // segment.
    int num_segments_;          // This together with segment_length
                                // determines the total frame capacity
                                // of the frame storage buffer.
    uint8_t *frame_buffer_start_addr_;  // virtual address of the
                                        // start of the frame buffer.
    int frame_buffer_segment_capacity_; // how many segments can be
                                        // stored in the frame buffer
                                        // before we need to start
                                        // reusing storage.
    int frame_num_rows_;
    int frame_num_cols_;
    int frame_type_; // Corresponds to the OpenCV type, e.g., CV_8UC3
    size_t frame_byte_size_;     // Size of each frame in bytes
    size_t frame_buffer_byte_size_;  // Total size of the frame store;
                                     // needed for mapping the shared
                                     // storage file into memory.
    int frame_buffer_file_descriptor_;
};
}}

#endif //OPENMPF_OCVFRAMEREADER_H
