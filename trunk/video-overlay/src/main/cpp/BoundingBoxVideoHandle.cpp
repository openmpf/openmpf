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

#include <cstdlib>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <utility>


BoundingBoxVideoHandle::BoundingBoxVideoHandle(const std::string &sourcePath, std::string destinationPath, int crf,
                                               bool border, const ResolutionConfig &resCfg,
                                               MPF::COMPONENT::MPFVideoCapture &videoCapture) :
        destinationPath_(std::move(destinationPath)), videoCapture_(std::move(videoCapture)) {

    int destinationFrameWidth  = videoCapture_.GetFrameSize().width;
    int destinationFrameHeight = videoCapture_.GetFrameSize().height;

    if (border) {
        // destinationFrameWidth  += (resCfg.framePadding / 2); // TODO
        // destinationFrameHeight += (resCfg.framePadding / 2);
        destinationFrameWidth  += resCfg.framePadding;
        destinationFrameHeight += resCfg.framePadding;
    }

    std::cout << "destinationFrameWidth: " << destinationFrameWidth << std::endl; // DEBUG
    std::cout << "destinationFrameHeight: " << destinationFrameHeight << std::endl; // DEBUG

    std::string command = std::string("ffmpeg") +
        " -pixel_format bgr24" +
        " -video_size " + std::to_string(destinationFrameWidth) +
            "x" + std::to_string(destinationFrameHeight) +
        " -framerate " + std::to_string(videoCapture_.GetFrameRate()) +
        " -f rawvideo" +
        " -i -" +
        " -pix_fmt yuv420p" + // https://trac.ffmpeg.org/ticket/5276
        " -c:v libvpx-vp9" +
        " -crf " + std::to_string(crf) + " -b:v 0" + // https://trac.ffmpeg.org/wiki/Encode/VP9
        " -threads 2" +
        " -y" + // overwrite file if it exists
        " '" + destinationPath_ + "'";

    pipe_ = popen(command.c_str(), "w");
    if (pipe_ == nullptr) {
        throw std::runtime_error("Unable to write markup because the ffmpeg process failed to start.");
    }
}

BoundingBoxVideoHandle::~BoundingBoxVideoHandle() {
    if (pipe_ != nullptr) {
        std::cerr << "Error: Pipe should have been closed and set to nullptr." << std::endl;
        std::exit(EXIT_FAILURE);
    }
}

cv::Size BoundingBoxVideoHandle::GetFrameSize() const {
    return videoCapture_.GetFrameSize();
}

bool BoundingBoxVideoHandle::Read(cv::Mat &frame) {
    return videoCapture_.Read(frame);
}

void BoundingBoxVideoHandle::HandleMarkedFrame(const cv::Mat& frame) {

    // std::cout << "frame.size(): " << frame.size() << std::endl; // DEBUG

    // Properly handle non-continuous cv::Mats. For example, if the left or right side of the frame was cropped off then
    // the matrix will be non-continuous. This is because cropping doesn't copy the matrix, it creates a submatrix
    // pointing in to the original un-cropped frame. To avoid writing sections we need to skip we copy data row by row.
    for (int row = 0; row < frame.rows; ++row) {
        fwrite(frame.ptr(row), frame.elemSize(), frame.cols, pipe_);
    }
}

void BoundingBoxVideoHandle::Close() {
    fflush(pipe_);

    int returnCode = pclose(pipe_);
    pipe_ = nullptr;

    if (returnCode != 0) {
        std::stringstream errorMsg;
        errorMsg << "Unable to write markup because the ffmpeg process ";

        if (WIFEXITED(returnCode)) {
            int ffmpegExitStatus = WEXITSTATUS(returnCode);
            errorMsg << "exited with exit code: " << ffmpegExitStatus;
        }
        else {
            errorMsg << "did not exit normally";
        }

        if (WIFSIGNALED(returnCode)) {
            errorMsg << " due to signal number: " << WTERMSIG(returnCode);
        }
        errorMsg << '.';

        throw std::runtime_error(errorMsg.str());
    }

    // Check if destination file exists and if it's empty.
    std::ifstream destinationFile(destinationPath_);
    if (destinationFile.peek() == std::ifstream::traits_type::eof()) {
        throw std::runtime_error("Failed to write \"" + destinationPath_ +
                                 "\". An error probably occurred during encoding.");
    }
}
