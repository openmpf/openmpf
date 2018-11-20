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


#ifndef MPF_FRAME_STORE_H_
#define MPF_FRAME_STORE_H_

#include <string>
#include <memory>
#include <opencv2/opencv.hpp>

#include "JobSettings.h"

namespace MPF {

class FrameStoreImpl;

class MPFFrameStore {

 public:

    explicit MPFFrameStore(const MPF::COMPONENT::JobSettings &settings);
    ~MPFFrameStore() = default;

    // Takes frame data from an OpenCV Mat and stores it
    void StoreFrame(const cv::Mat &frame, const size_t frame_index);

    // Copies frame data from storage into an OpenCV Mat.
    void GetFrame(cv::Mat &frame, const size_t frame_index);

    // Deletes the copy of the frame data for this frame index.
    void DeleteFrame(const size_t frame_index);

    bool AtCapacity() { return (frames_in_store_ >= capacity_); }

  private:
    size_t frame_byte_size_;
    int capacity_;
    int frames_in_store_;
    MPF::FrameStoreImpl *impl_ptr_;
    std::string key_prefix_;
    std::string CreateKey(const size_t index);

};

} // namespace MPF

#endif // MPF_FRAME_STORE_H_
