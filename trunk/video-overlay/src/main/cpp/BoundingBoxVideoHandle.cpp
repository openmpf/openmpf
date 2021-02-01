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
                                               int framePadding) :
        videoCapture_(sourceVideoPath) {
    std::string destinationExtension = destinationVideoPath.substr(destinationVideoPath.find_last_of(".") + 1);

    int fourcc;
    std::string encoder;
    if (destinationExtension == "mp4") {
        // From https://docs.opencv.org/4.5.0/dd/d9e/classcv_1_1VideoWriter.html#ac3478f6257454209fa99249cc03a5c59 :
        //
        //  "List of codes can be obtained at Video Codecs by FOURCC page (https://www.fourcc.org/codecs.php).
        //   FFMPEG backend with MP4 container natively uses other values as fourcc code"
        //
        // Providing "H264", "X264", or "AVC1" to cv::VideoWriter::fourcc() doesn't work on CentOS.
        // Thus, we use a hardcoded value for the H264 codec.
        fourcc = 0X00000021;
        encoder = "H264";
    } else { // .avi
        fourcc = cv::VideoWriter::fourcc('M','J','P','G');
        encoder = "MJPG";
    }

    cv::Size destinationVideoFrameSize(videoCapture_.GetFrameSize().width  + 2 * framePadding,
                                       videoCapture_.GetFrameSize().height + 2 * framePadding);

    videoWriter_ = cv::VideoWriter(destinationVideoPath, fourcc, videoCapture_.GetFrameRate(),
                                   destinationVideoFrameSize, true);

    if (!videoCapture_.IsOpened()) {
        throw std::runtime_error("Unable to open source video capture for: " + sourceVideoPath);
    }

    if (!videoWriter_.isOpened()) {
        // destination file should exist prior to creating the video writer
        std::string errorMsg = "Unable to open destination video writer to: " + destinationVideoPath
                + ". First, check that the file exists with proper permissions. Second, use \"ffmpeg -codecs\" to"
                + " check if the " + encoder + " encoder is installed.";
        throw std::runtime_error(errorMsg);
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

bool BoundingBoxVideoHandle::MarkExemplar() {
    return true;
}

bool BoundingBoxVideoHandle::ShowFrameNumbers() {
    return true;
}
