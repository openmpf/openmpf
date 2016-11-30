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

#include "read_detections_from_file.h"

#include <stdio.h>

#include <string>
#include <vector>

using std::vector;
using std::string;
using std::ifstream;
using std::pair;

using namespace MPF;
using namespace COMPONENT;

ReadDetectionsFromFile::ReadDetectionsFromFile() {}
ReadDetectionsFromFile::~ReadDetectionsFromFile() {}

bool ReadDetectionsFromFile::ReadVideoTracks(string &csv_filepath,
                                               vector<MPFVideoTrack> &tracks) {

    ifstream in_file(csv_filepath.c_str());

    if(!in_file.is_open()) {
        printf("ERROR - failed to open the input file: %s\n", csv_filepath.c_str());
	return false;
    }

    std::string delimiter = ",";
    size_t pos = 0;
    std::string token;

    int next_face_track_index = 0;
    MPFVideoTrack face_track;

    std::string line;
    while (std::getline(in_file, line)) {
        if(!line.empty()) {

            if(line[0] == '#') {
                // create first track
                string sub = line.substr(1);
                // std::cout << "line: " << line << std::endl;
                // std::cout << "substring: " << sub << std::endl;

                int face_track_index = atoi(sub.c_str());
                // std::cout << "face_track_index = " << face_track_index
                //       << " next_face_track_index = " << next_face_track_index
                //       << std::endl;
                if(next_face_track_index == face_track_index) {
                    // push back the track before resetting it
                    if(next_face_track_index > 0) {
                        tracks.push_back(face_track);
                    }

                    MPFVideoTrack default_face_track;
                    // reset the face_track object
                    face_track = default_face_track;
                    ++next_face_track_index;
                }

                // get start frame and stop frame
                getline(in_file, line);
                face_track.start_frame = atoi(line.c_str());
                getline(in_file, line);
                face_track.stop_frame = atoi(line.c_str());

                continue;
            }

            if((face_track.start_frame != -1) && (face_track.stop_frame != -1)) {
                int delim_index = 0;
                MPFImageLocation face_detection;
                while ((pos = line.find(delimiter)) != std::string::npos) {
                    token = line.substr(0, pos);

                    switch(delim_index) {
                    case 0:
                      face_detection.x_left_upper = atoi(token.c_str());
                      break;
                    case 1:
                      face_detection.y_left_upper = atoi(token.c_str());
                      break;
                    case 2:
                      face_detection.width = atoi(token.c_str());
                      // ugly hack to keep the loop going
                      line = line + ",";
                      break;
                    case 3:
                      face_detection.height = atoi(token.c_str());
                      break;
                    default:
                      break;
                    }

                    ++delim_index;

                    // std::cout << token << std::endl;
                    line.erase(0, pos + delimiter.length());
                }

                // at 4 can assume that it is a face detection - can't think of any reason to output confidence
                if(delim_index == 4) {
                    int frame_index = face_track.start_frame + face_track.frame_locations.size();
                    face_track.frame_locations.insert(pair<int, MPFImageLocation>(frame_index, face_detection));
                }
            }
        }

    }


    // if finished reading and the face_track has data then it needs to be pushed back as well
    if((face_track.start_frame != -1) &&
       (face_track.stop_frame != -1) &&
       (!face_track.frame_locations.empty())) {
		tracks.push_back(face_track);
	}

	// close the file - if this is called again it will use the same file!!
	if(in_file.is_open()) {
		in_file.close();
	}

	return true;
}

bool ReadDetectionsFromFile::ReadImageLocations(string &csv_filepath,
                                                  vector<MPFImageLocation> &detections) {

    ifstream in_file(csv_filepath.c_str());

    if(!in_file.is_open()) {
      std::cout << "ERROR - failed to open the input file: " << csv_filepath << std::endl;
        return false;
    }

    std::string delimiter = ",";
    size_t pos = 0;
    std::string token;

    std::string line;
    while (std::getline(in_file, line)) {
        // std::cout << "line: " << line << std::endl;
        if(!line.empty()) {

            int delim_index = 0;
            MPFImageLocation detection;
            while ((pos = line.find(delimiter)) != std::string::npos) {
                token = line.substr(0, pos);

                switch(delim_index) {
                case 0:
                  detection.x_left_upper = atoi(token.c_str());
                  break;
                case 1:
                  detection.y_left_upper = atoi(token.c_str());
                  break;
                case 2:
                  detection.width = atoi(token.c_str());
                  line = line + ",";
                  break;
                case 3:
                  detection.height = atoi(token.c_str());
                  break;
                default:
                  break;
                }

                ++delim_index;

                // std::cout << token << std::endl;
                line.erase(0, pos + delimiter.length());
            }
            detections.push_back(detection);
        }

    }

    // close the file - if this is called again it will use the same file!!
    if(in_file.is_open()) {
      in_file.close();
    }

    return true;
}
