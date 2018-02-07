/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
    if (frame.empty()) {
        throw std::runtime_error("Encountered empty frame.");
    }

    if (frame_number % 3 != 0) {
        LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Did not find activity in frame " << frame_number);
        return false;
    }

    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Found activity in frame " << frame_number);
    MPFImageLocation detection(0, 0, frame.cols, frame.rows, 0.75, { {{"propName1", "propVal1"}} });

    bool add_to_existing_track = frame_number % 6 == 0 && !segment_detections_.empty();
    if (add_to_existing_track) {
        MPFVideoTrack &track = segment_detections_.back();
        track.frame_locations.emplace(frame_number, std::move(detection));
        track.stop_frame = frame_number;
        return false;
    }

    MPFVideoTrack track(frame_number, frame_number, detection.confidence);
    track.frame_locations.emplace(frame_number, std::move(detection));
    segment_detections_.push_back(std::move(track));
    return segment_detections_.size() == 1;
}


std::vector<MPFVideoTrack> StreamingHelloWorld::EndSegment() {
    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Getting video tracks.")
    std::vector<MPFVideoTrack> results;
    // swap with stack variable so that result vector is moved instead of copied.
    results.swap(segment_detections_);
    return results;
}


std::string StreamingHelloWorld::GetDetectionType() {
    return "HELLO";
}


StreamingHelloWorld::~StreamingHelloWorld() {
    LOG4CXX_INFO(hw_logger_, "[" << job_name_ << "] Destroying component");
}

EXPORT_MPF_STREAMING_COMPONENT(StreamingHelloWorld);

