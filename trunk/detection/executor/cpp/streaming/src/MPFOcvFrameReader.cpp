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

#include "MPFFrameReader.h"
#include "detectionComponentUtils.h"

using namespace MPF;
using namespace COMPONENT;
using std::string;
using namespace cv;

MPFFrameReaderError OcvFrameReaderComponent::AttachToStream(
    const MPFFrameReaderJob &job,
    Properties &stream_properties) {

    bool success;
    string stream_uri = DetectionComponentUtils::GetProperty<string>(job.job_properties,
                                                                     "STREAM_URI", "");
    if (stream_uri.empty()) {
        return MPF_INVALID_VIDEO_URI;
    }
    
    success = video_capture_.open(stream_uri);
    if (!success || !video_capture_.isOpened()) {
        return MPF_COULD_NOT_OPEN_STREAM;
    }

    stream_properties["STREAM_POSITION_MSEC"] = video_capture_.get(CAP_PROP_POS_MSEC);
    stream_properties["STREAM_FRAMES_PER_SEC"] = video_capture_.get(CAP_PROP_FPS);

    Mat first;
    success = video_capture_.read(first);
    if (!success) {
        return MPF_COULD_NOT_READ_STREAM;
    }
    stream_properties["FRAME_ROWS"] = std::to_string(first.rows);
    stream_properties["FRAME_COLS"] = std::to_string(first.cols);
    stream_properties["FRAME_NUM_PIXELS"] = std::to_string(first.total());
    stream_properties["FRAME_PIXEL_SIZE"] = std::to_string(first.elem_size());
    stream_properties["FRAME_PIXEL_TYPE"] = std::to_string(first.type());
    stream_properties["FRAME_IS_CONTINUOUS"] = first.isContiguous() ? "true" : "false";

    return MPF_FRAME_READER_SUCCESS;
}


MPFFrameReaderError OcvFrameReaderComponent::OpenFrameStore(MPFFrameReaderJob &job,
                                                            Properties &stream_properties){

    string store_name = MPFDetectionUtils::GetProperty<string>(job.job_properties,
                                                               "FRAME_STORE_NAME", "");
    if (store_name.empty()) {
        return MPF_INVALID_PROPERTY;
    }

    num_segments_ = MPFDetectionUtils::GetProperty<int>(job.job_properties,
                                                           "NUM_SEGMENTS", 0);
    if (num_segments == 0) {
        return MPF_INVALID_PROPERTY;
    }


    int num_rows = MPFDetectionUtils::GetProperty<int>(stream_properties,
                                                    "FRAME_ROWS", 0);
    if (num_rows == 0) {
        return MPF_BAD_FRAME_SIZE;
    }

    int num_cols = MPFDetectionUtils::GetProperty<int>(stream_properties,
                                                    "FRAME_COLS", 0);
    if (num_cols == 0) {
        return MPF_BAD_FRAME_SIZE;
    }

    int elem_size = MPFDetectionUtils::GetProperty<int>(stream_properties,
                                                    "FRAME_PIXEL_SIZE", 0);
    if (elem_size == 0) {
        return MPF_BAD_FRAME_SIZE;
    }

    frame_byte_size_ = num_cols*num_rows*elem_size;
    frame_buffer_byte_size_ = frame_byte_size_*num_segments_;

    string err_str;
    frame_store_.Create(store_name, frame_byte_size_, err_str);
    if (!err_str.empty()) {
        return MPF_MEMORY_ALLOCATION_FAILED;
    }
    return MPF_FRAME_READER_SUCCESS;
}

MPFFrameReaderError OcvFrameReaderComponent::CloseFrameStore() {
    string err_str;
    frame_store_.Close(err_str);
    if (!err_str.empty()) {
        return MPF_FRAME_READER_FAILED;
    }
    return MPF_FRAME_READER_SUCCESS;
}

MPFFrameReaderError OcvFrameReaderComponent::Run(MPFFrameReaderJob &job) {}

