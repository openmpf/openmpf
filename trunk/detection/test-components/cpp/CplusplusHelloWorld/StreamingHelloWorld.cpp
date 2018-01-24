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


StreamingHelloWorld::StreamingHelloWorld(const MPFStreamingVideoJob &job)
        : MPFStreamingDetectionComponent(job)
        , hw_logger_(GetLogger(job.run_directory))
        , job_name_(job.job_name) {

    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Initialized StreamingHelloWorld component.")
}


log4cxx::LoggerPtr StreamingHelloWorld::GetLogger(const std::string &run_directory) {
    log4cxx::xml::DOMConfigurator::configure(run_directory + "/CplusplusHelloWorld/config/Log4cxxConfig.xml");
    return log4cxx::Logger::getLogger("StreamingHelloWorldTest");
}


void StreamingHelloWorld::BeginSegment(const VideoSegmentInfo &segment_info) {
    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Preparing to process segment " << segment_info.segment_number)
}


bool StreamingHelloWorld::ProcessFrame(const cv::Mat &frame, int frame_number) {
    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Processing frame with size: " << frame.size())
    if (frame_number % 3 == 0) {
        LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Found activity in frame " << frame_number);
        return true;
    }

    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Did not find activity in frame " << frame_number);
    return false;
}

std::vector<MPFVideoTrack> StreamingHelloWorld::EndSegment() {
    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Getting video tracks.")
    MPFVideoTrack track1(10, 15, 0.5);
    track1.frame_locations[10] = MPFImageLocation(10, 15, 78, 63, 0.5,
                                                  { {"propName1", "propVal1"}, {"propName2", "propVal2"} });
    track1.frame_locations[15] = MPFImageLocation(10, 15, 78, 63, 0.9,
                                                  { {"propName3", "propVal3"} });
    MPFVideoTrack track2(150, 200, 0.75);
    track2.frame_locations[150] = MPFImageLocation(100, 150, 78, 63, 0.75);
    track2.frame_locations[200] = MPFImageLocation(100, 150, 78, 63, 0.9);

    return { track1, track2 };
}


std::string StreamingHelloWorld::GetDetectionType() {
    return "HELLO";
}


StreamingHelloWorld::~StreamingHelloWorld() {
    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Destroying component");
}

EXPORT_MPF_STREAMING_COMPONENT(StreamingHelloWorld);

