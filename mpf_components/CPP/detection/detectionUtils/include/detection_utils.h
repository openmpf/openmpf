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

#ifndef TRUNK_DETECTION_COMMON_UTILS_DETECTION_UTILS_H_
#define TRUNK_DETECTION_COMMON_UTILS_DETECTION_UTILS_H_

#include "detection_base.h"

#include "opencv2/core/core.hpp"
#include "opencv2/objdetect/objdetect.hpp"
#include "opencv2/video/tracking.hpp"
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/calib3d/calib3d.hpp"
#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/features2d/features2d.hpp"


class DetectionUtils {
  public:
    static cv::Rect ImageLocationToCvRect(const MPF::COMPONENT::MPFImageLocation &image_location);
    static MPF::COMPONENT::MPFImageLocation CvRectToImageLocation(const cv::Rect &rect,
                                                                  double confidence = -1.0);

    static bool IsExistingRectIntersection(const cv::Rect new_rect,
                                           const std::vector<cv::Rect> &existing_rects,
                                           int &intersection_index);

    static void DrawText(cv::Mat &image, int frame_index);
    static void DrawTracks(cv::Mat &image,
                           const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks_to_draw,
                           const std::vector<MPF::COMPONENT::MPFImageLocation> &locations,
                           int tracks_completed_count,
                           std::vector<int> track_indexes = std::vector<int>());
};

#endif  // TRUNK_DETECTION_COMMON_UTILS_DETECTION_UTILS_H_
