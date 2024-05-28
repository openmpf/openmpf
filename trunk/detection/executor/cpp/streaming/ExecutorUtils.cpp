/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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


#include "ExecutorUtils.h"

namespace MPF { namespace COMPONENT { namespace ExecutorUtils {


    void DropLowConfidenceDetections(double confidence_threshold, std::vector<MPFVideoTrack> &tracks) {
        if (confidence_threshold <= LOWEST_CONFIDENCE_THRESHOLD) {
            return;
        }

        for (auto &track : tracks) {
            for (auto it = track.frame_locations.begin(); it != track.frame_locations.end(); ) {
                if (it->second.confidence < confidence_threshold) {
                    it = track.frame_locations.erase(it);
                }
                else {
                    ++it;
                }
            }

            if (!track.frame_locations.empty()) {
                track.start_frame = track.frame_locations.begin()->first;
                track.stop_frame = track.frame_locations.rbegin()->first;
            }
        }

        auto new_end = std::remove_if(tracks.begin(), tracks.end(), [](const MPFVideoTrack &track) {
            return track.frame_locations.empty();
        });
        tracks.erase(new_end, tracks.end());
    }


    void DropOutOfSegmentDetections(log4cxx::LoggerPtr &logger, const VideoSegmentInfo &segment,
                                    std::vector<MPFVideoTrack> &tracks) {
        for (auto &track : tracks) {
            auto &frame_locations = track.frame_locations;
            if (frame_locations.empty()) {
                continue;
            }

            int first_detection = frame_locations.begin()->first;
            int last_detection = frame_locations.rbegin()->first;
            if (first_detection >= segment.start_frame && last_detection <= segment.end_frame) {
                if (track.start_frame != first_detection || track.stop_frame != last_detection) {
                    LOG4CXX_WARN(logger, "Found video track that starts at " << track.start_frame << " and ends at "
                            << track.stop_frame << ", but its first detection is at frame " << first_detection
                            << " and its last detection is at frame " << last_detection
                            << ". Setting track start frame to " << first_detection << " and stop frame to "
                            << last_detection << ".");

                    track.start_frame = first_detection;
                    track.stop_frame = last_detection;
                }
                continue;
            }

            LOG4CXX_WARN(logger,
                     "Found track containing detections outside of current segment while processing segment "
                     << segment.segment_number << ". All detections before frame "
                     << segment.start_frame << " and after " << segment.end_frame << " will be dropped.");

            auto lower = frame_locations.lower_bound(segment.start_frame);
            frame_locations.erase(frame_locations.begin(), lower);

            auto upper = frame_locations.upper_bound(segment.end_frame);
            frame_locations.erase(upper, frame_locations.end());

            if (!frame_locations.empty()) {
                track.start_frame = frame_locations.begin()->first;
                track.stop_frame = frame_locations.rbegin()->first;
            }
        }

        // Shifts items toward front of container, but doesn't resize
        auto new_end = std::remove_if(tracks.begin(), tracks.end(), [](const MPFVideoTrack &track) {
            return track.frame_locations.empty();
        });

        long num_removed = tracks.end() - new_end;
        if (num_removed > 0) {
            LOG4CXX_WARN(logger, "Found " << num_removed << " tracks with no detections while processing segment "
                    << segment.segment_number << ". Dropping " << num_removed << " empty tracks.");
            tracks.erase(new_end, tracks.end());
        }
    }
}}}