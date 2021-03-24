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


BoundingBoxVideoHandle::BoundingBoxVideoHandle(std::string destinationPath, const std::string &encoder,
                                               int vp9Crf, bool border, const ResolutionConfig &resCfg,
                                               MPF::COMPONENT::MPFVideoCapture videoCapture) :
        destinationPath_(std::move(destinationPath)), videoCapture_(std::move(videoCapture)) {

    int destinationFrameWidth  = videoCapture_.GetFrameSize().width;
    int destinationFrameHeight = videoCapture_.GetFrameSize().height;

    if (border) {
        destinationFrameWidth  += resCfg.framePadding;
        destinationFrameHeight += resCfg.framePadding;
    }

    std::string command = std::string("ffmpeg") +
        " -pixel_format bgr24" +
        " -video_size " + std::to_string(destinationFrameWidth) +
            "x" + std::to_string(destinationFrameHeight) +
        " -framerate " + std::to_string(videoCapture_.GetFrameRate()) +
        " -f rawvideo" +
        " -i -" +
        // https://trac.ffmpeg.org/ticket/5276
        // Use yuv420p to encode webm files that can be played with current browsers.
        " -pix_fmt yuv420p";

    if ("vp9" == encoder) { // .webm
        command = command +
            " -c:v libvpx-vp9" +
            // https://trac.ffmpeg.org/wiki/Encode/VP9
            // Two-pass is the recommended encoding method for libvpx-vp9 as some quality-enhancing encoder features are
            // only available in 2-pass mode. Constant quality 2-pass is invoked by setting -b:v to zero and specifiying
            // a quality level using the -crf switch.
            " -crf " + std::to_string(vp9Crf) + " -b:v 0";
    }
    else if ("h264" == encoder) { // .mp4
        command = command +
            " -c:v libx264";
    }
    else { // "mjpeg" .avi
        command = command +
            " -c:v mjpeg";
    }

    command = command +
        // https://stackoverflow.com/a/20848224
        // H.264 needs even dimensions. VLC and mpv players sometimes have issues with odd dimensions.
        " -vf \"pad=ceil(iw/2)*2:ceil(ih/2)*2\"" +
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
