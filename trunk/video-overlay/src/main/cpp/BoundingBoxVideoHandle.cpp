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

BoundingBoxVideoHandle::BoundingBoxVideoHandle(std::string sourceVideoPath, std::string destinationVideoPath,
                                               int framePadding) : videoCapture_(sourceVideoPath) {
    int destinationVideoFrameWidth  = videoCapture_.GetFrameSize().width  + 2 * framePadding;
    int destinationVideoFrameHeight = videoCapture_.GetFrameSize().height + 2 * framePadding;

    std::string command = std::string("ffmpeg") +
        " -pixel_format bgr24" +
        " -video_size " + std::to_string(destinationVideoFrameWidth) +
            "x" + std::to_string(destinationVideoFrameHeight) +
        " -framerate " + std::to_string(videoCapture_.GetFrameRate()) +
        " -f rawvideo" +
        " -i -" +
        " -pix_fmt yuv420p" + // https://trac.ffmpeg.org/ticket/5276
        " -c:v libvpx-vp9" +
        " -crf 31 -b:v 0" + // https://trac.ffmpeg.org/wiki/Encode/VP9
        " -threads 2" +
        // " -loglevel verbose" + // DEBUG
        " -y" + // overwrite file if it exists
        " '" + destinationVideoPath + "'";

    pipe_ = popen(command.c_str(), "w");
    if (pipe_ == nullptr) {
        throw std::runtime_error("Unable to write markup because ffmpeg process failed to start.");
    }
}

BoundingBoxVideoHandle::~BoundingBoxVideoHandle() {
    if (pipe_ != nullptr) {
        exit(EXIT_FAILURE);
    }
}

cv::Size BoundingBoxVideoHandle::GetFrameSize() {
    return videoCapture_.GetFrameSize();
}

bool BoundingBoxVideoHandle::Read(cv::Mat &frame) {
    return videoCapture_.Read(frame);
}

void BoundingBoxVideoHandle::HandleMarkedFrame(const cv::Mat& frame) {
    size_t sizeInBytes = frame.step[0] * frame.rows; // https://stackoverflow.com/a/26441073
    fwrite(frame.data, sizeof(unsigned char), sizeInBytes, pipe_);
}

bool BoundingBoxVideoHandle::MarkExemplar() {
    return true;
}

bool BoundingBoxVideoHandle::ShowFrameNumbers() {
    return true;
}

void BoundingBoxVideoHandle::Close() {
    fflush(pipe_);
    pclose(pipe_);
    pipe_ = nullptr;
}
