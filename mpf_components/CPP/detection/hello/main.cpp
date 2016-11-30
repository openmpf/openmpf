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

#include <string>
#include <vector>
#include <iostream>
#include <sstream>
#include <algorithm>
#include <log4cxx/simplelayout.h>
#include <log4cxx/consoleappender.h>
#include <log4cxx/logmanager.h>
#include "detectionComponentUtils.h"
#include "helloWorld.h"

using namespace MPF;
using namespace COMPONENT;

int main(int argc, char* argv[]) {

    if ( (argc != 2) && (argc != 4) && (argc != 5)) {
        std::cout << "Usage: " << argv[0] << " IMAGE_DATA_URI" << std::endl;
        std::cout << "Usage: " << argv[0] << " VIDEO_DATA_URI VIDEO_START_FRAME VIDEO_STOP_FRAME [VIDEO_FRAME_INTERVAL]" << std::endl;
        return 0;
    }

    std::string uri(argv[1]);

    std::map<std::string, std::string> algorithm_properties;
    MPFDetectionDataType media_type = IMAGE;

    if (argc > 2) {
        media_type = VIDEO;
    }

    // set up logger
    log4cxx::LoggerPtr logger = log4cxx::Logger::getRootLogger();
    log4cxx::SimpleLayoutPtr layout = new log4cxx::SimpleLayout();
    log4cxx::ConsoleAppenderPtr console_appender = new log4cxx::ConsoleAppender(layout);
    logger->addAppender(console_appender);
    logger->setLevel(log4cxx::Level::getDebug());

    // instantiate the test component: this component is only
    // used for testing the component registration functionality
    HelloWorld hw;

    if (hw.Init()) {

        switch(media_type) {

        case IMAGE:
        {
            std::vector<MPFImageLocation> detections;
            MPFImageJob job("Testing", uri, algorithm_properties, { });
            hw.GetDetections(job, detections);
            LOG4CXX_INFO(logger, "Number of image locations = " << detections.size());

            for (int i = 0; i < detections.size(); i++) {
                LOG4CXX_INFO(logger, "Location number " << i
                             << ", metadata = "
                             << DetectionComponentUtils::GetProperty<std::string>(detections[i].detection_properties, "METADATA", "")
                             << ", x left upper = " << detections[i].x_left_upper
                             << ", y left upper = " << detections[i].y_left_upper
                             << ", width = " << detections[i].width
                             << ", height = " << detections[i].height
                             << ", confidence = " << detections[i].confidence);
            }

            break;
        }

        case VIDEO:
        {
            int start_frame = atoi(argv[2]);
            int stop_frame = atoi(argv[3]);

            int FRAME_INTERVAL = 1;
            if (argc >= 5) {
                FRAME_INTERVAL = atoi(argv[4]);
            }

            std::ostringstream stringStream;
            stringStream << FRAME_INTERVAL;
            algorithm_properties.insert(std::pair<std::string, std::string>(
                                            "FRAME_INTERVAL", stringStream.str()));
            std::vector<MPFVideoTrack> tracks;
            MPFVideoJob job("Testing", uri, start_frame, stop_frame, algorithm_properties, { });
            hw.GetDetections(job, tracks);
            LOG4CXX_INFO(logger, "Number of video tracks = " << tracks.size());

            for (int i = 0; i < tracks.size(); i++) {
                LOG4CXX_INFO(logger, "Track number " << i
                             << ", start frame = " << tracks[i].start_frame
                             << ", stop frame = " << tracks[i].stop_frame
                             << ", number of locations = " << tracks[i].frame_locations.size()
                             << ", condfidence = " << tracks[i].confidence
                             << ", metadata = "
                             << DetectionComponentUtils::GetProperty<std::string>(tracks[i].detection_properties, "METADATA", ""));

                for (std::map<int,MPFImageLocation>::iterator it = tracks[i].frame_locations.begin(); it != tracks[i].frame_locations.end(); ++it) {
                    LOG4CXX_DEBUG(logger, "Location frame = " << it->first
                                  << ", x left upper = " << it->second.x_left_upper
                                  << ", y left upper = " << it->second.y_left_upper
                                  << ", width = " << it->second.width
                                  << ", height = " << it->second.height
                                  << ", confidence = " << it->second.confidence
                                  << ", metadata = "
                                  << DetectionComponentUtils::GetProperty<std::string>(it->second.detection_properties, "METADATA", ""));
                }
            }

            break;
        }

        default:
        {
            LOG4CXX_ERROR(logger, "Error - invalid detection type");
            break;
        }
        }

    } else {
        LOG4CXX_ERROR(logger, "Error - could not initialize detection component");
    }

    hw.Close();
    return 0;
}


