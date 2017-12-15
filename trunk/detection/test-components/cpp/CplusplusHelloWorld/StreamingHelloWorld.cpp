/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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


#include <log4cxx/xml/domconfigurator.h>
#include "StreamingHelloWorld.h"

using namespace MPF::COMPONENT;

using namespace std;
using log4cxx::Logger;
using log4cxx::xml::DOMConfigurator;



bool StreamingHelloWorld::Init() {
    string run_dir = GetRunDirectory();
    if (run_dir.empty()) {
        run_dir = ".";
    }

    std::string plugin_path = run_dir + "/CplusplusHelloWorld";
    std::string config_path = plugin_path + "/config";

    log4cxx::xml::DOMConfigurator::configure(config_path + "/Log4cxxConfig.xml");

    hw_logger_ = log4cxx::Logger::getLogger("HelloWorldTest");

    LOG4CXX_INFO(hw_logger_, "Running from directory " << run_dir);

    return true;
}


MPFDetectionError StreamingHelloWorld::SetupJob(const MPFJob &job) {
    LOG4CXX_INFO(hw_logger_, "[" << job.job_name << "] Setting up job.")
    job_name_ = job.job_name;
    return MPF_DETECTION_SUCCESS;
}


MPFDetectionError StreamingHelloWorld::ProcessFrame(const cv::Mat &frame, bool &activityFound) {
    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Processing frame with size: " << frame.size())
    activityFound = !frame.empty();
    return MPF_DETECTION_SUCCESS;
}


MPFDetectionError StreamingHelloWorld::GetVideoTracks(std::vector<MPFVideoTrack> &tracks) {
    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Getting video tracks.")
    MPFVideoTrack track1(10, 15, 0.5);
    track1.frame_locations[10] = MPFImageLocation(10, 15, 78, 63, 0.5,
                                                  { {"propName1", "propVal1"}, {"propName2", "propVal2"} });
    track1.frame_locations[15] = MPFImageLocation(10, 15, 78, 63, 0.9,
                                                  { {"propName3", "propVal3"} });
    tracks.push_back(std::move(track1));

    MPFVideoTrack track2(150, 200, 0.75);
    track2.frame_locations[150] = MPFImageLocation(100, 150, 78, 63, 0.75);
    track2.frame_locations[200] = MPFImageLocation(100, 150, 78, 63, 0.9);
    tracks.push_back(std::move(track2));

    return MPF_DETECTION_SUCCESS;
}


std::string StreamingHelloWorld::GetDetectionType() {
    return "HELLO";
}


bool StreamingHelloWorld::Close() {
    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Closing component");
    return true;
}

StreamingHelloWorld::~StreamingHelloWorld() {
    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Destroying component");
}


MPF_COMPONENT_CREATOR(StreamingHelloWorld);
MPF_COMPONENT_DELETER();