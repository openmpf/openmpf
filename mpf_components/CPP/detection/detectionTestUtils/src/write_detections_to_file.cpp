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

#include "write_detections_to_file.h"

#include <ctime>
#include <string>
#include <vector>

#include "utils.h"
#include "detection_utils.h"

using namespace std;
using namespace cv;

using namespace MPF;
using namespace COMPONENT;

WriteDetectionsToFile::WriteDetectionsToFile() {}
WriteDetectionsToFile::~WriteDetectionsToFile() {}

bool WriteDetectionsToFile::FileExists(const std::string &filepath) {
	if (FILE *file = fopen(filepath.c_str(), "r")) {
		fclose(file);
		return true;
	} else {
		return false;
	}
}

void WriteDetectionsToFile::WriteVideoTracks(const std::string csv_filepath, const std::vector<MPFVideoTrack> &tracks) {
	string path = string(csv_filepath);
	if (FileExists(csv_filepath.c_str())) {
		time_t t = time(0);
		struct tm * now = localtime(&t);

		string timeString = "";
		timeString = std::to_string((now->tm_year + 1900));
		timeString = timeString + "_" + std::to_string((now->tm_mon + 1));
		timeString = timeString + "_" + std::to_string((now->tm_mday));

		int beginExt = csv_filepath.find_last_of(".");
		string noExtStr = csv_filepath.substr(0, beginExt);

		string new_path = noExtStr + "_" + timeString + ".tracks";

		path = new_path;

		std::ifstream  src(csv_filepath.c_str());
		std::ofstream  dst(new_path.c_str());

		dst << src.rdbuf();

		dst.close();
	}

	if (!tracks.empty()) {
		ofstream my_file;

		my_file.open(path.c_str());
		if (!my_file.good()) {
			printf("\nFailure to open output file \n");
			return;
		}

		my_file << path << endl;

		for(unsigned int i = 0; i < tracks.size(); i++) {
			my_file << "#" << i << endl;

			my_file << tracks[i].start_frame << endl;
			my_file << tracks[i].stop_frame << endl;

			for (std::map<int, MPFImageLocation>::const_iterator it = tracks[i].frame_locations.begin(); it != tracks[i].frame_locations.end(); ++it) {
				my_file << it->second.x_left_upper << ","
						<< it->second.y_left_upper << ","
						<< it->second.width << ","
						<< it->second.height << endl;
			}
		}

		if(my_file.is_open()) {
			my_file.close();
		}
	} else {
		printf("\n--No tracks found--\n");
	}
}

void WriteDetectionsToFile::WriteVideoTracks(const std::string csv_filepath, const std::vector<MPFImageLocation> &locations) {
	string path = string(csv_filepath);

	if (FileExists(csv_filepath.c_str())) {
		time_t t = time(0);
		struct tm * now = localtime(&t);

		string timeString = "";
		timeString = std::to_string((now->tm_year + 1900));
		timeString = timeString + "_" + std::to_string((now->tm_mon + 1));
		timeString = timeString + "_" + std::to_string((now->tm_mday));

		int beginExt = csv_filepath.find_last_of(".");
		string noExtStr = csv_filepath.substr(0, beginExt);

		string new_path = noExtStr + "_" + timeString + ".tracks";

		path = new_path;

		std::ifstream  src(csv_filepath.c_str());
		std::ofstream  dst(new_path.c_str());

		dst << src.rdbuf();

		dst.close();
	}

	if (!locations.empty()) {
		ofstream my_file;

		my_file.open(path.c_str());
		if(!my_file.good()) {
			printf("\nFailure to open output file \n");
			return;
		}

		my_file << path << endl;

		for (unsigned int i = 0; i < locations.size(); i++) {
			my_file << locations[i].x_left_upper << ","
					<< locations[i].y_left_upper << ","
					<< locations[i].width << ","
					<< locations[i].height << endl;
		}

		if (my_file.is_open()) {
			my_file.close();
		}
	} else {
		printf("\n--No tracks found--\n");
	}
}


