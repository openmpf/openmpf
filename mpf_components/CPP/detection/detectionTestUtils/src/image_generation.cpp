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

#include "image_generation.h"

#include <cstdlib>
#include <stdio.h>
#include <algorithm>
#include <string>
#include <vector>

#include "detection_utils.h"

using std::string;
using std::vector;
using std::remove_if;
using std::map;

using cv::FeatureDetector;
using cv::Point;
using cv::Point2f;
using cv::Mat;
using cv::Rect;
using cv::Scalar;
using cv::Size;
using cv::RotatedRect;
using cv::Size2f;
using cv::namedWindow;
using cv::waitKey;
using cv::KeyPoint;
using cv::resize;
using cv::rectangle;
using cv::imshow;
using cv::imread;
using cv::imwrite;

using namespace MPF;
using namespace COMPONENT;

ImageGeneration::ImageGeneration() {}

ImageGeneration::~ImageGeneration() {}

bool ImageGeneration::Init(bool display_window, bool print_debug_info) {
  imshow_on = display_window;
  print_extra_info = print_debug_info;

  return true;
}


Mat ImageGeneration::RotateFace(const cv::Mat &src) {
  Mat rot_mat(2, 3, CV_32FC1);
  Mat warp_rotate_dst;

  Point center = Point(src.cols/2, src.rows/2);

  double angle =  rand() % 11 - 5;;

  double scale = static_cast<double>(rand() % 10)/100.0 + 0.9;;

  rot_mat = getRotationMatrix2D(center, angle, scale);

  warpAffine(src, warp_rotate_dst, rot_mat, src.size());

  if (imshow_on) {
    imshow("warp_rotate_window", warp_rotate_dst);
    waitKey(5);
  }

  return warp_rotate_dst;
}

Rect ImageGeneration::GetRandomRect(const Mat &image, const Rect &face_rect, const vector<Rect> &existing_rects) {
  Mat mask = Mat::zeros(image.size(), image.type());

  int x_pos = rand() % (image.cols - face_rect.width);
  int y_pos = rand() % (image.rows - face_rect.height);

  Rect new_rect(x_pos, y_pos, face_rect.width, face_rect.height);

  rectangle(mask, new_rect, Scalar(255, 255, 255), CV_FILLED);

  if (imshow_on) {
    imshow("random mask", mask);
    waitKey(5);
  }

  return new_rect;
}


int ImageGeneration::WriteDetectionOutputImage(const string image_in_uri, const vector<MPFVideoTrack> &detections, const string image_out_filepath) {
  Mat input_image = imread(image_in_uri.c_str());
  if (input_image.empty()) {
    printf("Could not open the input image: %s\n", image_in_uri.c_str());
    return -1;
  }

  for (unsigned int i = 0; i < detections.size(); i++) {
    for (std::map<int, MPFImageLocation>::const_iterator it = detections[i].frame_locations.begin(); it != detections[i].frame_locations.end(); ++it) {
      Rect face_rect(it->second.x_left_upper, it->second.y_left_upper, it->second.width, it->second.height);
      rectangle(input_image, face_rect, Scalar(0, 255, 0));
    }
  }

  imwrite(image_out_filepath, input_image);

  return 0;
}

int ImageGeneration::WriteDetectionOutputImage(const string image_in_uri, const vector<MPFImageLocation> &detections, const string image_out_filepath) {
  Mat input_image = imread(image_in_uri.c_str());
  if (input_image.empty()) {
    printf("Could not open the input image: %s\n", image_in_uri.c_str());
    return -1;
  }

  for (unsigned int i = 0; i < detections.size(); i++) {
    Rect face_rect(detections[i].x_left_upper, detections[i].y_left_upper, detections[i].width, detections[i].height);
    rectangle(input_image, face_rect, Scalar(0, 255, 0));
  }

  imwrite(image_out_filepath, input_image);

  return 0;
}

int ImageGeneration::CreateTestImageAndDetectionOutput(const vector<Mat> &faces, bool use_scaling_and_rotation,
    const string image_out_filepath, vector<MPFImageLocation> &detections) {
  if (faces.empty()) {
    return -1;
  }

  Size image_size = Size(2000, 2000);
  Mat blank_frame = Mat(image_size, CV_8UC3, Scalar(127, 127, 127));
  Mat src = blank_frame.clone();

  vector<Rect> random_rects;

  for (unsigned int i = 0; i < faces.size(); ++i) {
    Mat face = Mat(faces[i]);
    Rect face_rect = Rect(0, 0, face.cols, face.rows);

    Rect random_rect;

    int intersection_index = -1;
    do {
      random_rect = GetRandomRect(blank_frame, face_rect);
    } while (DetectionUtils::IsExistingRectIntersection(random_rect, random_rects, intersection_index));

    random_rects.push_back(random_rect);
    MPFImageLocation location(random_rect.x, random_rect.y, random_rect.width, random_rect.height);
    detections.push_back(location);

    Mat subview = src(random_rects[i]);

    if (use_scaling_and_rotation) {
      face = RotateFace(face);
    }
    face.copyTo(subview);
  }
  if (imshow_on) {
    imshow("src", src); waitKey(5);
  }

  imwrite(image_out_filepath, src);

  return 0;
}
