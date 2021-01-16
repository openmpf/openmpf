/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

#include <opencv2/imgcodecs.hpp>
#include "BoundingBoxImageHandle.h"

BoundingBoxImageHandle::BoundingBoxImageHandle(std::string sourceImagePath, std::string destinationImagePath ) :
        sourceImagePath_(sourceImagePath),
        destinationImagePath_(destinationImagePath),
        videoCapture_(sourceImagePath_) {
    if (!videoCapture_.IsOpened()) {
        throw std::runtime_error("Unable to open source image: " + sourceImagePath_);
    }
}

cv::Size BoundingBoxImageHandle::GetFrameSize() {
    return videoCapture_.GetFrameSize();
}

bool BoundingBoxImageHandle::Read(cv::Mat &frame) {
    if (frameRead_) {
        return false; // if the image has already been read once, there is nothing more to do
    }
    if (!videoCapture_.Read(frame) || frame.empty()) {
        throw std::runtime_error("Unable to read source image: " + sourceImagePath_);
    }
    frameRead_ = true;
    return true;
}

void BoundingBoxImageHandle::HandleMarkedFrame(const cv::Mat& frame) {
    if (!cv::imwrite(destinationImagePath_, frame, { cv::IMWRITE_PNG_COMPRESSION, 9 })) {
        throw std::runtime_error("Failed to write image: " + destinationImagePath_);
    }
}

