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

#ifndef TRUNK_DETECTION_GTEST_VIDEO_GENERATION_H_
#define TRUNK_DETECTION_GTEST_VIDEO_GENERATION_H_

#include <string>
#include <vector>

#include "opencv2/core/core.hpp"
#include "opencv2/objdetect/objdetect.hpp"
#include "opencv2/video/tracking.hpp"
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/calib3d/calib3d.hpp"
#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/features2d/features2d.hpp"
#include "opencv2/nonfree/nonfree.hpp"

#include "detection_base.h"

namespace DETECTIONTEST {
class VideoGeneration {
  private:
    bool imshow_on;
    bool print_extra_info;

  public:
    VideoGeneration();
    ~VideoGeneration();

    bool Init(bool display_window = false, bool print_debug_info = false);

    std::vector<MPF::COMPONENT::MPFImageLocation> GetImageLocationsAtFrameIndex(int frame_index, const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks,
                                                                                std::vector<int> &track_indexes);

    int WriteTrackOutputVideo(const std::string video_in_uri, const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks,
                              const std::string video_out_filepath);

    cv::Mat RotateFace(const cv::Mat &src);
    cv::Rect GetRandomRect(const cv::Mat &image, const cv::Rect &face_rect, const std::vector<cv::Rect> &existing_rects = std::vector<cv::Rect>());
    cv::Rect StepRectThroughFrame(const cv::Mat &image, const cv::Rect &rect,
                                  int x_step, int y_step, const std::vector<cv::Rect> &existing_rects = std::vector<cv::Rect>());

    int CreateTestVideoAndTrackOutput(const std::vector<cv::Mat> &faces, int video_length,
                                      bool use_scaling_and_rotation,
                                      const std::string video_out_filepath, std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks);
};
}  // namespace DETECTIONTEST
#endif  // TRUNK_DETECTION_GTEST_VIDEO_GENERATION_H_
