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

#include "OcvFrameReader.h"
#include "detectionComponentUtils.h"

using namespace MPF;
using namespace COMPONENT;
using std::string;
using namespace cv;

MPFFrameReaderError OcvFrameReader::AttachToStream(
    const MPFFrameReaderJob &job,
    Properties &stream_properties) {

    bool success;
    string stream_uri = DetectionComponentUtils::GetProperty<string>(job.job_properties,
                                                                     "STREAM_URI", "");
    if (stream_uri.empty()) {
        return FRAME_READER_INVALID_VIDEO_URI;
    }
    
    success = video_capture_.open(stream_uri);
    if (!success || !video_capture_.isOpened()) {
        return FRAME_READER_COULD_NOT_OPEN_STREAM;
    }

    stream_properties["STREAM_POSITION_MSEC"] = video_capture_.get(CAP_PROP_POS_MSEC);
    stream_properties["STREAM_FRAMES_PER_SEC"] = video_capture_.get(CAP_PROP_FPS);

    Mat first;
    success = video_capture_.read(first);
    if (!success) {
        return FRAME_READER_COULD_NOT_READ_STREAM;
    }
    stream_properties["FRAME_ROWS"] = std::to_string(first.rows);
    stream_properties["FRAME_COLS"] = std::to_string(first.cols);
    stream_properties["FRAME_NUM_PIXELS"] = std::to_string(first.total());
    stream_properties["FRAME_PIXEL_SIZE"] = std::to_string(first.elemSize());
    stream_properties["FRAME_PIXEL_TYPE"] = std::to_string(first.type());
    stream_properties["FRAME_IS_CONTINUOUS"] = first.isContinuous() ? "true" : "false";

    return FRAME_READER_SUCCESS;
}


MPFFrameReaderError OcvFrameReader::OpenFrameStore(MPFFrameReaderJob &job,
                                                   Properties &stream_properties){

    string store_name = DetectionComponentUtils::GetProperty<string>(job.job_properties,
                                                               "FRAME_STORE_NAME", "");
    if (store_name.empty()) {
        return FRAME_READER_INVALID_PROPERTY;
    }

    num_segments_ = 
            DetectionComponentUtils::GetProperty<int>(job.job_properties,
                                                      "NUM_SEGMENTS", 0);
    if (num_segments_ == 0) {
        return FRAME_READER_INVALID_PROPERTY;
    }


    frame_num_rows_ = 
            DetectionComponentUtils::GetProperty<int>(stream_properties,
                                                      "FRAME_ROWS", 0);
    if (frame_num_rows_ == 0) {
        return FRAME_READER_BAD_FRAME_SIZE;
    }

    int frame_num_cols_ = 
            DetectionComponentUtils::GetProperty<int>(stream_properties,
                                                      "FRAME_COLS", 0);
    if (frame_num_cols_ == 0) {
        return FRAME_READER_BAD_FRAME_SIZE;
    }

    int elem_size = 
            DetectionComponentUtils::GetProperty<int>(stream_properties,
                                                      "FRAME_PIXEL_SIZE", 0);
    if (elem_size == 0) {
        return FRAME_READER_BAD_FRAME_SIZE;
    }

    frame_byte_size_ = frame_num_cols_*frame_num_rows_*elem_size;
    frame_buffer_byte_size_ = frame_byte_size_*num_segments_;

    string err_str;
    frame_store_.Create(store_name, frame_byte_size_, err_str);
    if (!err_str.empty()) {
        return FRAME_READER_MEMORY_ALLOCATION_FAILED;
    }
    return FRAME_READER_SUCCESS;
}

MPFFrameReaderError OcvFrameReader::CloseFrameStore() {
    string err_str;
    frame_store_.Close(err_str);
    if (!err_str.empty()) {
        return FRAME_READER_COULD_NOT_CLOSE_STREAM;
    }
    return FRAME_READER_SUCCESS;
}


MPFFrameReaderError OcvFrameReader::ReadAndStoreFrame(MPFFrameReaderJob &job,
                                                      const size_t offset)
{
    int failcount = 0;
    string err_string;
    uint8_t* frame_addr = frame_store_.GetFrameAddress(offset,
                                                       frame_byte_size_,
                                                       err_string);
    if (err_string.empty()) {
        return FRAME_READER_BAD_OFFSET;
    }
    Mat new_mat(frame_num_rows_, frame_num_cols_, frame_type_, frame_addr);
    bool success = video_capture_.read(new_mat);

    float stall_timeout = 
            DetectionComponentUtils::GetProperty<float>(job.job_properties,
                                                        "STALL_DETECTION_THRESHOLD",
                                                        0.0);
    if (frame_num_rows_ == 0) {
        return FRAME_READER_BAD_FRAME_SIZE;
    }
    while(!success) {
        // TODO: This is where we need to keep track of a timeout
        failcount++;
        success = video_capture_.read(new_mat);
    }

    return FRAME_READER_SUCCESS;
    
}

