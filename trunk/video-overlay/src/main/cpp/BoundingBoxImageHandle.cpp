/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

#include "BoundingBoxImageHandle.h"

#include <stdexcept>
#include <utility>

#include <opencv2/imgcodecs.hpp>


BoundingBoxImageHandle::BoundingBoxImageHandle(std::string sourcePath, std::string destinationPath) :
        sourcePath_(std::move(sourcePath)),
        destinationPath_(std::move(destinationPath)),
        videoCapture_(sourcePath_) {
}

cv::Size BoundingBoxImageHandle::GetFrameSize() const {
    return videoCapture_.GetFrameSize();
}

bool BoundingBoxImageHandle::Read(cv::Mat &frame) {
    if (frameRead_) {
        return false; // if the image has already been read once, there is nothing more to do
    }
    if (!videoCapture_.Read(frame) || frame.empty()) {
        throw std::runtime_error("Unable to read source image: " + sourcePath_);
    }
    frameRead_ = true;
    return true;
}

void BoundingBoxImageHandle::HandleMarkedFrame(const cv::Mat& frame) {
    if (!cv::imwrite(destinationPath_, frame, { cv::IMWRITE_PNG_COMPRESSION, 9 })) {
        throw std::runtime_error("Failed to write image: " + destinationPath_);
    }
}
