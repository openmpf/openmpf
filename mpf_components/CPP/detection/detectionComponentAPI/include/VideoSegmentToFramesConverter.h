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

#ifndef MPF_VIDEO_SEGMENT_TO_FRAMES_CONVERTER_H
#define MPF_VIDEO_SEGMENT_TO_FRAMES_CONVERTER_H

#include <vector>
#include <cmath>
#include <string>
#include <iostream>

#include "detection_base.h"
#include "detectionComponentUtils.h"
#include "MPFVideoCapture.h"

namespace MPF {

namespace COMPONENT {

struct MPFVideoFrameData {
    int start_frame;
    int stop_frame;
    int width;
    int height;
    int num_channels;
    int bytes_per_channel;
    int frame_interval;
    int frames_in_segment;
    int fps;
    std::vector<uint8_t *> data;
};


// Convert a video segment to MPFVideoFrameData struct.  This function
// can be used to convert a video opened with the MPFVideoCapture into
// a vector of byte arrays, for components that require the video data
// in that form.

std::pair<MPFDetectionError,std::string>
convertSegmentToFrameData(const MPFVideoJob &job,
                          MPFVideoCapture &cap,
                          MPFVideoFrameData &output) {

    int bytes_per_channel;
    int num_channels;
    int start_frame = job.start_frame;
    int stop_frame = job.stop_frame;
    int frame_interval = DetectionComponentUtils::GetProperty<int>(job.job_properties, "FRAME_INTERVAL", 1);
    MPFDetectionError rc = MPF_DETECTION_SUCCESS;
    std::string err_msg;

    output.data.clear();

    //Get video properties
    int frame_count = cap.GetFrameCount();
    int fps = cap.GetFrameRate();

    cap.SetFramePosition(start_frame);
    int frame_num = cap.GetCurrentFramePosition();

    // Check to see if we got to the end of the video before
    // reaching the first frame.  If so, return an empty vector.
    if (frame_num >= frame_count) {
        rc = MPF_DETECTION_SUCCESS;
        err_msg = "[" + job.job_name +
                "] start_frame exceeds the number of frames in the video: frame count = " + std::to_string(frame_count);
        return std::make_pair(rc, err_msg);
    }

    int end_frame = std::min(stop_frame, (frame_count - 1));

    output.width = cap.GetFrameSize().width;
    output.height = cap.GetFrameSize().height;
        
    cv::Mat frame;
    uint8_t* frame_data;
    int frames_in_segment;
    try {
        //Get the first frame so that we can figure out how much memory
        //needs to be allocated for the data.
        cap.Read(frame);
        if (frame.empty() || frame.rows == 0 || frame.cols == 0) {
            rc = MPF_BAD_FRAME_SIZE;
            err_msg = "[" + job.job_name + "] Empty frame encountered at frame " +
                    std::to_string(cap.GetCurrentFramePosition());
            return std::make_pair(rc, err_msg);
        }

        bytes_per_channel = frame.elemSize1();  // size of one channel
                                                // in bytes
        num_channels = frame.channels();

        //Calculate total frame data size in bytes

        frames_in_segment = stop_frame - start_frame + 1;

        // Adjust the frame count to account for the frame interval
        float ratio = static_cast<float>(frames_in_segment)/static_cast<float>(frame_interval);
        frames_in_segment = static_cast<int>(std::ceil(ratio));

        int pixels_per_frame = frame.rows*frame.cols;
        int frame_data_byte_size = pixels_per_frame*num_channels*bytes_per_channel;
        int total_data_size = frames_in_segment*frame_data_byte_size;

        //Allocate a block of memory, and carve it up into a vector of
        //byte pointers.
        frame_data = new (std::nothrow) uint8_t[total_data_size];
        if (NULL == frame_data) {
            rc = MPF_MEMORY_ALLOCATION_FAILED;
            err_msg = "[" + job.job_name + "] memory allocation failed: byte size = " +
                    std::to_string(total_data_size);
            return std::make_pair(rc, err_msg);
        }

        for (int i = 0; i < frames_in_segment; i++) {
            output.data.push_back(frame_data + (i*frame_data_byte_size));
        }

        // Store the first frame into the first byte pointer, then while
        // there are still frames to process, copy the data from each frame
        // to the next byte pointer.
        // The frame returned from the MPFVideoCapture may have been
        // cropped. In that case, the data pointer we get from it points
        // somewhere in the middle of the original frame.  That is,
        // the new cropped frame is not a smaller contiguous copy of
        // the data from the region of interest, but simply a new data
        // structure that points into the original frame.  To account
        // for this, we copy one row at a time into the byte vector to
        // create a new contiguous data block.

        int bytes_per_row = frame.cols*num_channels*bytes_per_channel;
        uint8_t *start_addr = output.data[0];

        for (int i = 0; i < frame.rows; i++) {
            uint8_t *ptr = &frame.at<uint8_t>(i,0);
            uint8_t *dst_addr = start_addr + i*bytes_per_row;
            memcpy(dst_addr, ptr, bytes_per_row);
        }

        // frame skip is one less than the frame interval, since the
        // act of reading a frame advances the current frame position
        // by 1.  So for a frame interval of 1, where we want to
        // process every frame, the frame skip added to the current
        // frame position needs to be 0.
        int frame_skip = frame_interval - 1;
        cap.SetFramePosition(cap.GetCurrentFramePosition() + frame_skip);
        int k = cap.GetCurrentFramePosition();

        for (int buffer_index = 1; buffer_index < frames_in_segment; buffer_index++) {

            cap.Read(frame);
            if (frame.empty() || frame.rows == 0 || frame.cols == 0) {
                rc = MPF_DETECTION_SUCCESS;
                err_msg = "[" + job.job_name + "] Empty frame encountered at frame " + std::to_string(k);
                stop_frame = k - 1;
                break;
            }

            start_addr = output.data[buffer_index];
            for (int i = 0; i < frame.rows; i++) {
                uint8_t *ptr = &frame.at<uint8_t>(i,0);
                uint8_t *dst_addr = start_addr + i*bytes_per_row;
                memcpy(dst_addr, ptr, bytes_per_row);
            }

            frame.release();
            cap.SetFramePosition(cap.GetCurrentFramePosition() + frame_skip);
            k = cap.GetCurrentFramePosition();
        }
    } catch (...) {
        return (DetectionComponentUtils::HandleDetectionException(MPFDetectionDataType::VIDEO));
    }

    if (rc != MPF_DETECTION_SUCCESS) {
        cap.Release();
        delete [] frame_data;
        output.data.clear();
        return std::make_pair(rc, err_msg);
    }

    output.start_frame = start_frame;
    output.stop_frame = stop_frame;
    output.frame_interval = frame_interval;
    output.num_channels = num_channels;
    output.bytes_per_channel = bytes_per_channel;
    output.frames_in_segment = frames_in_segment;
    output.fps = fps;

    return std::make_pair(MPF_DETECTION_SUCCESS, err_msg);
}

} // namespace COMPONENT
} // namespace MPF

#endif  // MPF_VIDEO_SEGMENT_TO_FRAMES_CONVERTER_H
