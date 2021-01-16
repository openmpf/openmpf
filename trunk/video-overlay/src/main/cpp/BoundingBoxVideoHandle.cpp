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

#include "BoundingBoxVideoHandle.h"

BoundingBoxVideoHandle::BoundingBoxVideoHandle(std::string sourceVideoPath, std::string destinationVideoPath ) :
        videoCapture_(sourceVideoPath),
        videoWriter_(destinationVideoPath, cv::VideoWriter::fourcc('M','J','P','G'),
            videoCapture_.GetFrameRate(), videoCapture_.GetFrameSize(), true) {
    if (!videoCapture_.IsOpened()) {
        throw std::runtime_error("Unable to open source video: " + sourceVideoPath);
    }
    if (!videoWriter_.isOpened()) {
        throw std::runtime_error("Unable to open destination video: " + destinationVideoPath);
    }
}

cv::Size BoundingBoxVideoHandle::GetFrameSize() {
    return videoCapture_.GetFrameSize();
}

bool BoundingBoxVideoHandle::Read(cv::Mat &frame) {
    return videoCapture_.Read(frame);
}

void BoundingBoxVideoHandle::HandleMarkedFrame(const cv::Mat& frame) {
    videoWriter_ << frame;
}

