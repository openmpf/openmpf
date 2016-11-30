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

#ifndef TRUNK_DETECTION_GTEST_READ_DETECTIONS_FROM_FILE_H_
#define TRUNK_DETECTION_GTEST_READ_DETECTIONS_FROM_FILE_H_

#include <fstream>
#include <string>
#include <vector>

#include "opencv2/opencv.hpp"

#include "detection_base.h"

class ReadDetectionsFromFile {
  public:
    ReadDetectionsFromFile();
    ~ReadDetectionsFromFile();

    static bool FileExists(const std::string filepath);

    bool  ReadVideoTracks(std::string &csv_filepath,
                          std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks);
    bool  ReadImageLocations(std::string &csv_filepath,
                             std::vector<MPF::COMPONENT::MPFImageLocation> &detections);
};

#endif  // TRUNK_DETECTION_GTEST_READ_DETECTIONS_FROM_FILE_H_
