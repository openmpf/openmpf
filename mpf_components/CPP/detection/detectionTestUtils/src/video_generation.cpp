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

#include "video_generation.h"

#include <stdio.h>

#include <cstdlib>
#include <algorithm>
#include <string>
#include <vector>

#include "detection_utils.h"

using std::string;
using std::vector;
using std::remove_if;
using std::map;
using std::pair;

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
using cv::imshow;

using namespace MPF;
using namespace COMPONENT;

using DETECTIONTEST::VideoGeneration;

struct frame_starts_before {
  inline bool operator() (const MPFVideoTrack& struct1, const MPFVideoTrack& struct2) {
    return (struct1.start_frame < struct2.start_frame);
  }
};

VideoGeneration::VideoGeneration() {}

VideoGeneration::~VideoGeneration() {}

bool VideoGeneration::Init(bool display_window, bool print_debug_info) {
  imshow_on = display_window;
  print_extra_info = print_debug_info;
  return true;
}

vector<MPFImageLocation> VideoGeneration::GetImageLocationsAtFrameIndex(int frame_index, const std::vector<MPFVideoTrack> &tracks,
		std::vector<int> &track_indexes) {
  vector<MPFImageLocation> locations;
  int track_index = 0;
  for (vector<MPFVideoTrack>::const_iterator face_track = tracks.begin(); face_track != tracks.end(); ++face_track) {
    if (face_track->frame_locations.find(frame_index) != face_track->frame_locations.end()) {
      locations.push_back(face_track->frame_locations.at(frame_index));
      track_indexes.push_back(track_index);
    }
    ++track_index;
  }
  return locations;
}

Mat VideoGeneration::RotateFace(const cv::Mat &src) {
  Mat rot_mat(2, 3, CV_32FC1);
  Mat warp_rotate_dst;

  Point center = Point(src.cols/2, src.rows/2);

  double angle =  rand() % 11 - 5;;

  double scale = static_cast<double>(rand() % 10)/100.0 + 0.9;

  rot_mat = getRotationMatrix2D(center, angle, scale);

  warpAffine(src, warp_rotate_dst, rot_mat, src.size());

  if (imshow_on) {
    imshow("warp_rotate_window", warp_rotate_dst);
    waitKey(5);
  }

  return warp_rotate_dst;
}

Rect VideoGeneration::GetRandomRect(const Mat &image, const Rect &face_rect, const vector<Rect> &existing_rects) {
  Mat mask = Mat::zeros(image.size(), image.type());

  int x_pos = rand() % (image.cols - face_rect.width);
  int y_pos = rand() % (image.rows - face_rect.height);

  Rect new_rect(x_pos, y_pos, face_rect.width, face_rect.height);

  rectangle(mask, new_rect, Scalar(255, 255, 255), CV_FILLED);

  if (imshow_on) {
    imshow("random mask", mask); waitKey(5);
  }

  return new_rect;
}

Rect VideoGeneration::StepRectThroughFrame(const Mat &image, const Rect &rect,
		int x_step, int y_step, const vector<Rect> &existing_rects) {
  Mat mask = Mat::zeros(image.size(), image.type());

  int x_pos = rect.x + x_step;
  int y_pos = rect.y + y_step;

  Rect face_rect(x_pos, y_pos, rect.width, rect.height);

  if (face_rect.x + face_rect.width >= image.cols ||
    face_rect.y + face_rect.height >= image.rows) {
    return Rect(0, 0, 0, 0);
  } else {
    rectangle(mask, face_rect, Scalar(255, 255, 255), CV_FILLED);
    if (imshow_on) {
      imshow("random mask", mask); waitKey(5);
    }
  }

	return face_rect;
}

int GetCompletedTracksCount(int current_frame_index, const vector<MPFVideoTrack> &tracks) {
  int tracks_completed_count = 0;
  for (vector<MPFVideoTrack>::const_iterator face_track = tracks.begin(); face_track != tracks.end(); ++face_track) {
    if (current_frame_index > face_track->stop_frame) {
      ++tracks_completed_count;
    }
  }
  return tracks_completed_count;
}

int VideoGeneration::WriteTrackOutputVideo(const string video_in_uri, const vector<MPFVideoTrack> &tracks,
		const string video_out_filepath) {
  VideoCapture input_video(video_in_uri);
  if (!input_video.isOpened()) {
    printf("Could not open the input video: %s\n", video_in_uri.c_str());
    return -1;
  }

  Size size = Size(static_cast<int>(input_video.get(CV_CAP_PROP_FRAME_WIDTH)), static_cast<int>(input_video.get(CV_CAP_PROP_FRAME_HEIGHT)));

  VideoWriter output_video;

  double fps = input_video.get(CV_CAP_PROP_FPS);
  output_video.open(video_out_filepath, CV_FOURCC('M', 'J', 'P', 'G'), fps, size, true);

  if (!output_video.isOpened()) {
    printf("Could not open the output video for write: %s\n", video_out_filepath.c_str());
    return -1;
  }

  Mat src;

  int frame_index = 0;
  for (;;) {
    input_video >> src;
    if (src.empty())
      break;

    vector<int> track_indexes;
    vector<MPFImageLocation> face_rects = GetImageLocationsAtFrameIndex(frame_index, tracks, track_indexes);

    DetectionUtils::DrawText(src, frame_index);

    if (!track_indexes.empty()) {
      vector<MPFVideoTrack> tracks_to_draw;
      for (unsigned int i = 0; i < track_indexes.size(); ++i) {
        MPFVideoTrack track = MPFVideoTrack(tracks[track_indexes[i]]);
        tracks_to_draw.push_back(track);
      }

      int tracks_completed_count = GetCompletedTracksCount(frame_index, tracks);
      DetectionUtils::DrawTracks(src, tracks_to_draw, face_rects, tracks_completed_count, track_indexes);
    }

    for (vector<MPFImageLocation>::iterator it = face_rects.begin(); it != face_rects.end(); ++it) {
      Rect face_rect(it->x_left_upper, it->y_left_upper, it->width, it->height);
      rectangle(src, face_rect, Scalar(0, 255, 0));
    }

    if (imshow_on) {
      imshow("Output Video Creation", src);
      waitKey(5);
    }

    output_video << src;

    ++frame_index;
  }

  return 0;
}

int VideoGeneration::CreateTestVideoAndTrackOutput(const vector<Mat> &faces, int video_length, bool use_scaling_and_rotation,
		const string video_out_filepath, vector<MPFVideoTrack> &tracks) {
  if (faces.empty()) {
    return -1;
  }

  Size video_size = Size(1000, 750);
  VideoWriter output_video;
  double fps = 30;

  output_video.open(video_out_filepath, CV_FOURCC('M', 'J', 'P', 'G'), fps, video_size, true);
  if (!output_video.isOpened()) {
    printf("Could not open the output video for write: %s\n", video_out_filepath.c_str());
    return -1;
  }

  Mat src;
  Mat blank_frame = Mat(video_size, CV_8UC3, Scalar(127, 127, 127));

  vector<Rect> random_rects;
  vector<Rect> stepped_rects;
  vector<MPFVideoTrack> current_tracks;

  for (unsigned int i = 0; i < faces.size(); ++i) {
    Mat face = Mat(faces[i]);
    Rect face_rect = Rect(0, 0, face.cols, face.rows);

    Rect random_rect;

    int intersection_index = -1;
    do {
      random_rect = GetRandomRect(blank_frame, face_rect);
    } while (DetectionUtils::IsExistingRectIntersection(random_rect, stepped_rects, intersection_index));

    random_rects.push_back(random_rect);
    stepped_rects.push_back(random_rect);

    MPFVideoTrack face_track;
    face_track.start_frame = 0;

    MPFImageLocation face_detection(random_rect.x, random_rect.y, random_rect.width, random_rect.height);
    face_track.frame_locations.insert(pair<int, MPFImageLocation>(0, face_detection));
    current_tracks.push_back(face_track);
  }

  int frame_index = 0;
  for (; frame_index < video_length; ) {
    src = blank_frame.clone();

    for (unsigned int i = 0; i < random_rects.size(); ++i) {
      if (frame_index > 0) {
        int step_x = rand() % 3 + 0;
        int step_y = rand() % 2 + 0;
        stepped_rects[i] = StepRectThroughFrame(src, stepped_rects[i], step_x, step_y, stepped_rects);
      }

      if (stepped_rects[i].area() == 0) {
        current_tracks[i].stop_frame = frame_index;
        tracks.push_back(current_tracks[i]);
        current_tracks[i].start_frame = frame_index;
        current_tracks[i].stop_frame = -1;
        current_tracks[i].frame_locations.clear();

        Mat face = Mat(faces[i]);
        Rect face_rect = Rect(0, 0, face.cols, face.rows);
        Rect random_rect;

        int intersection_index = -1;
        do {
          random_rect = GetRandomRect(blank_frame, face_rect);
        } while (DetectionUtils::IsExistingRectIntersection(random_rect, stepped_rects, intersection_index));

        stepped_rects[i] = random_rect;
      }

      MPFImageLocation face_detection(stepped_rects[i].x, stepped_rects[i].y, stepped_rects[i].width, stepped_rects[i].height);
      current_tracks[i].frame_locations.insert(pair<int, MPFImageLocation>(frame_index, face_detection));

      Mat subview = src(stepped_rects[i]);
      Mat face = Mat(faces[i]);
      if (use_scaling_and_rotation) {
        face = RotateFace(face);
      }
      face.copyTo(subview);
    }

    DetectionUtils::DrawText(src, frame_index);

    if (imshow_on) {
      imshow("src", src); waitKey(5);
    }

    output_video.write(src);

    ++frame_index;
  }

  output_video.release();
  for (vector<MPFVideoTrack>::iterator iter = current_tracks.begin(); iter != current_tracks.end(); ++iter) {
    iter->stop_frame = frame_index - 1;
    tracks.push_back(*iter);
  }

  return 0;
}
