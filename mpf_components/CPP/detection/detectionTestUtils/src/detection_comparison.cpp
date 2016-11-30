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

#include "detection_comparison.h"

#include <stdio.h>

#include <vector>
#include <string>
#include <iostream>
#include <cmath>

#include "utils.h"
#include "detection_utils.h"

using std::string;
using std::vector;
using std::cout;
using std::endl;
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

MPFVideoTrack DetectionComparison::FindTrack(const MPFVideoTrack &query_track, const std::vector <MPFVideoTrack> &tracks_to_search, int &found_index) {
    found_index = -1;

    MPFImageLocation first_query_detection = query_track.frame_locations.begin()->second;

    Rect first_query_rect = DetectionUtils::ImageLocationToCvRect(first_query_detection);

    for (unsigned int i = 0; i < tracks_to_search.size(); i++) {
        MPFVideoTrack known_track = tracks_to_search[i];

        if (abs(query_track.start_frame - known_track.start_frame) <= 5) {
            for (std::map<int, MPFImageLocation>::const_iterator it = known_track.frame_locations.begin(); it != known_track.frame_locations.end(); ++it) {
                MPFImageLocation known_detection = it->second;
                Rect known_detection_rect = DetectionUtils::ImageLocationToCvRect(known_detection);

                Rect intersection = first_query_rect & known_detection_rect;

                if (intersection.area() > 0) {
                    found_index = i;

                    return MPFVideoTrack(known_track);
                }
            }
        }
    }

    return MPFVideoTrack(-1, -1);
}

int DetectionComparison::CompareTracks(const MPFVideoTrack &query_track, const MPFVideoTrack &found_track) {
    int query_track_start_frame = query_track.start_frame;
    int found_track_start_frame = found_track.start_frame;

    int query_track_stop_frame = query_track.stop_frame;
    int found_track_stop_frame = found_track.stop_frame;

    int loop_start_index = 0;
    int query_track_index_modifier = found_track_start_frame - query_track_start_frame;
    if (query_track_index_modifier < 0) {
        loop_start_index = abs(query_track_index_modifier);
    }

    int loop_end_count = -1;
    if (query_track_stop_frame < found_track_stop_frame) {
        loop_end_count = query_track_stop_frame - found_track_start_frame;
    } else {
        loop_end_count = static_cast<int>(found_track.frame_locations.size());
    }

    int found_faces = 0;
    for (int k = loop_start_index; k < loop_end_count; k++) {
        MPFImageLocation found_known_detection = found_track.frame_locations.at(found_track_start_frame + k);
        MPFImageLocation query_detection = query_track.frame_locations.at(query_track_start_frame+ k + query_track_index_modifier);
        Rect found_known_detection_rect = DetectionUtils::ImageLocationToCvRect(found_known_detection);
        Rect query_detection_rect = DetectionUtils::ImageLocationToCvRect(query_detection);
        Rect intersection = found_known_detection_rect & query_detection_rect;
        int target_intersection_area = static_cast<int>(floor(static_cast<float>(found_known_detection_rect.area()) * 0.1));
        int intersection_area = intersection.area();

        if (intersection_area > target_intersection_area) {
            ++found_faces;
        } else {
            printf("\tThe intersection was %d of %d which is %f\n", intersection_area, target_intersection_area, static_cast<float>(intersection_area)/static_cast<float>(target_intersection_area));
        }
    }

    return found_faces;
}

float DetectionComparison::CompareDetectionOutput(const std::vector <MPFVideoTrack> &query_tracks, const std::vector <MPFVideoTrack> &known_tracks) {
    float total_score = 0.0;
    int total_known_frames = 0;

    for (unsigned int i = 0; i < known_tracks.size(); ++i) {
        total_known_frames += static_cast<int>(known_tracks[i].frame_locations.size());
    }

    int successfully_found_frames = 0;
    int total_found_frames = 0;
    for (unsigned int i = 0; i < query_tracks.size(); ++i) {
        total_found_frames += static_cast<int>(query_tracks[i].frame_locations.size());
    }

    float track_count_factor = 1.0;
    if (total_found_frames > total_known_frames) {
        printf("More objects detected than exist: ");
        track_count_factor = fabs(static_cast<float>(total_known_frames) / static_cast<float>(total_found_frames));
    } else if (total_found_frames < total_known_frames) {
        printf("Less objects detected than exist: ");
        track_count_factor = fabs(static_cast<float>(total_found_frames) / static_cast<float>(total_known_frames));
    } else {
        printf("Exact number of objects detected that exist, but still need to check locations: ");
    }

    printf("%d of %d\n", total_found_frames, total_known_frames);

    vector <MPFVideoTrack> known_tracks_copy(known_tracks);
    while (!known_tracks_copy.empty()) {
        vector <MPFVideoTrack> query_tracks_copy(query_tracks);
        MPFVideoTrack known_track = known_tracks_copy.front();
        int found_track_index = -1;
        MPFVideoTrack found_track = FindTrack(known_track, query_tracks_copy, found_track_index);

        while (found_track_index != -1) {
            int found_faces = CompareTracks(found_track, known_track);
            successfully_found_frames += found_faces;
            query_tracks_copy.erase(query_tracks_copy.begin() + found_track_index);
            found_track = FindTrack(known_track, query_tracks_copy, found_track_index);
        }
        known_tracks_copy.erase(known_tracks_copy.begin());
    }

    printf("\t\tSuccessfully found frames:\t%d\n", successfully_found_frames);
    printf("\t\tTotal known frames:\t\t%d\n", total_known_frames);
    printf("\t\tTrack count factor:\t\t%f\n", track_count_factor);
    printf("\t\t\tCombined: (%d/%d)*%f\n", successfully_found_frames, total_known_frames, track_count_factor);
    total_score = (static_cast<float>(successfully_found_frames) / static_cast<float>(total_known_frames)) * track_count_factor;

    printf("Total score: %1.3f\n", total_score);

    return total_score;
}

float DetectionComparison::CompareDetectionOutput(const std::vector <MPFImageLocation> &query_tracks,
                                                    const std::vector <MPFImageLocation> &known_tracks) {
    float total_score = 1.0;
    int total_known_frames = static_cast<int>(known_tracks.size());
    int successfully_found_frames = 0;
    int total_found_frames = static_cast<int>(query_tracks.size());

    float track_count_factor = 1.0;
    if (total_found_frames > total_known_frames) {
        printf("More objects detected than exist: \n");
        track_count_factor = fabs(static_cast<float>(total_known_frames) / static_cast<float>(total_found_frames));
    } else if (total_found_frames < total_known_frames) {
        printf("Less objects detected than exist: \n");
        track_count_factor = fabs(static_cast<float>(total_found_frames) / static_cast<float>(total_known_frames));
    } else {
        printf("Exact number of objects detected that exist, but still need to check locations: \n");
    }

    printf("%d of %d\n", total_found_frames, total_known_frames);

    float overlapTotal = 0.0;
    int overlapCount = 0;
    for (int i = 0; i < query_tracks.size(); i++) {
        float bestOverlap = 0.0;
        Rect query_detection_rect = DetectionUtils::ImageLocationToCvRect(query_tracks[i]);
        for (int j = 0; j < known_tracks.size(); j++) {
            Rect found_known_detection_rect = DetectionUtils::ImageLocationToCvRect(known_tracks[j]);
            Rect intersection = found_known_detection_rect & query_detection_rect;
            float overlap = (intersection.area() > found_known_detection_rect.area()) ?
                            static_cast<float>(found_known_detection_rect.area()) / static_cast<float>(intersection.area()) :
                            static_cast<float>(intersection.area()) / static_cast<float>(found_known_detection_rect.area());
            bestOverlap = (overlap > bestOverlap) ? overlap : bestOverlap;
        }
        overlapTotal += bestOverlap;
        overlapCount++;
    }

    total_score = track_count_factor * (overlapTotal / static_cast<float>(overlapCount));
    printf("Total score: %1.3f\n", total_score);
    return total_score;
}

