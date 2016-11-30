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

#include "detection_utils.h"

#include <stdio.h>

#include <vector>
#include <string>
#include <cstdlib>

#include "utils.h"

using namespace MPF;
using namespace COMPONENT;

using std::string;
using std::vector;
using std::remove_if;
using std::map;

using cv::FeatureDetector;
using cv::FONT_HERSHEY_PLAIN;
using cv::Point;
using cv::Point2f;
using cv::Mat;
using cv::Rect;
using cv::Scalar;
using cv::Size;
using cv::RotatedRect;
using cv::Size2f;
using cv::VideoCapture;
using cv::VideoWriter;
using cv::namedWindow;
using cv::waitKey;
using cv::KeyPoint;
using cv::resize;
using cv::rectangle;
using cv::GoodFeaturesToTrackDetector;

MPFImageLocation DetectionUtils::CvRectToImageLocation(const Rect &rect, double confidence) {
  return MPFImageLocation(rect.x, rect.y, rect.width, rect.height, static_cast<float>(confidence));
}

Rect DetectionUtils::ImageLocationToCvRect(const MPFImageLocation &location) {
    return Rect(location.x_left_upper, location.y_left_upper, location.width, location.height);
}

bool DetectionUtils::IsExistingRectIntersection(const Rect new_rect, const vector<Rect> &existing_rects, int &intersection_index) {
  intersection_index = -1;

  if (!existing_rects.empty()) {
    for (vector<Rect>::const_iterator rect = existing_rects.begin(); rect != existing_rects.end(); ++rect) {
      ++intersection_index;

      Rect existing_rect(*rect);

      // opencv allows for this comparison
      if (new_rect == existing_rect) {
        // assuming the index is equal - TODO: not the best way to check - could cause an infinite loop
        continue;
      }

      Rect intersection = existing_rect & new_rect;  // (rectangle intersection)

      if (intersection.area() > 0) {
        return true;
      }
    }
    // reset intersection_index to -1 before return - intersection_index could be used as the check
    // rather than the return bool
    intersection_index = -1;
    return false;
  }

  return false;
}

void DetectionUtils::DrawText(Mat &image, int frame_index) {
  // assuming there won't be more than 10 chars in the frame_index
    string text = std::to_string(frame_index);
  int length = text.size();

  int font_face = FONT_HERSHEY_PLAIN;
  double font_scale = 0.7;
  int thickness = 1;

  Point text_origin((image.cols - 20 - 5*length), (image.rows - 10));

  putText(image, text, text_origin, font_face, font_scale,
      Scalar(0, 255, 0), thickness, 8);
}

void DetectionUtils::DrawTracks(cv::Mat &image, const std::vector<MPFVideoTrack> &tracks_to_draw, const std::vector<MPFImageLocation> &current_locations,
                              int tracks_completed_count, vector<int> track_indexes) {
  int y_pos = image.rows - 10;
  if (tracks_to_draw.empty()) {
    return;
  } else {
    y_pos = y_pos - (tracks_to_draw.size() - 1)*10;
  }

  for (vector<Point2f>::size_type i = 0; i < tracks_to_draw.size(); i++) {
    int track_index = tracks_completed_count + i;

    MPFVideoTrack track = tracks_to_draw[i];

    MPFImageLocation latest_location;
    if (current_locations.empty()) {
      // assume this is displaying during tracking
      latest_location = track.frame_locations.rbegin()->second; // last element in map
    } else {
      // display post tracking or output video generation
      latest_location = current_locations[i];
      if (!track_indexes.empty()) {
        track_index = track_indexes[i];
      }
    }

    string text = std::to_string(track_index) + ": ";
    text = text + std::to_string(latest_location.x_left_upper) + ",";
    text = text + std::to_string(latest_location.y_left_upper) + ",";
    text = text + std::to_string(latest_location.width) + ",";
    text = text + std::to_string(latest_location.height) + ",";
    text = text + std::to_string(latest_location.confidence);

    Point text_origin(10, y_pos);

    int font_face = FONT_HERSHEY_PLAIN;
    double font_scale = 0.7;
    int thickness = 1;

    putText(image, text, text_origin, font_face, font_scale,
        Scalar(0, 255, 0), thickness, 8);

    // try to place the track index at the bottom center of the bounding box!!
    // TODO: could be out of image bounds...
    int x = latest_location.x_left_upper + latest_location.width/2 - 6;
    int y = latest_location.y_left_upper + latest_location.height + 20;
    text_origin = Point(x, y);

    font_scale = 1.2;
    thickness = 2;
    putText(image, std::to_string(track_index), text_origin, font_face, font_scale,
        Scalar(255, 0, 255), thickness, 8);

    y_pos += 10;
  }
}
