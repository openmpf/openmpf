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

#include <map>
#include <log4cxx/logmanager.h>
#include <log4cxx/xml/domconfigurator.h>
#include "helloWorld.h"

using namespace MPF;
using namespace COMPONENT;

using namespace std;
using log4cxx::Logger;
using log4cxx::xml::DOMConfigurator;

//-----------------------------------------------------------------------------
HelloWorld::HelloWorld() {

}

//-----------------------------------------------------------------------------
HelloWorld::~HelloWorld() {

}

//-----------------------------------------------------------------------------
/* virtual */ bool HelloWorld::Init() {
    
    // Determine where the executable is running
    string run_dir = GetRunDirectory();
    if (run_dir.empty()) {
        run_dir = ".";
    }

    // Configure logger
    string plugin_dir = run_dir + "/HelloWorldTest";
    log4cxx::xml::DOMConfigurator::configure(run_dir + "/config/Log4cxxConfig.xml");
    hw_logger_ = log4cxx::Logger::getLogger("HelloWorldTest");

    LOG4CXX_INFO(hw_logger_, "Running from directory " << run_dir);

    return true;

}

//-----------------------------------------------------------------------------
/* virtual */ bool HelloWorld::Close() {

    return true;

}

//-----------------------------------------------------------------------------
// Video case

MPFDetectionError HelloWorld::GetDetections(const MPFVideoJob &job, vector<MPFVideoTrack> &tracks) {
    LOG4CXX_INFO(hw_logger_, "[" << job.job_name << "] Processing complete. Found " << tracks.size() << " video tracks.");
    return MPF_DETECTION_SUCCESS;
}


//-----------------------------------------------------------------------------
// Audio case

MPFDetectionError HelloWorld::GetDetections(const MPFAudioJob &job, vector<MPFAudioTrack> &tracks) {
    LOG4CXX_INFO(hw_logger_, "[" << job.job_name << "] Processing complete. Found " << tracks.size() << " audio tracks.");
    return MPF_DETECTION_SUCCESS;
}

//-----------------------------------------------------------------------------
// Image case

MPFDetectionError HelloWorld::GetDetections(const MPFImageJob &job, vector<MPFImageLocation> &locations) {
    LOG4CXX_INFO(hw_logger_, "[" << job.job_name << "] Processing complete. Found " << locations.size() << " image locations.");

    return MPF_DETECTION_SUCCESS;
}

//-----------------------------------------------------------------------------
bool HelloWorld::Supports(MPFDetectionDataType data_type) {
    return data_type == MPFDetectionDataType::IMAGE || data_type == MPFDetectionDataType::VIDEO;
}

//-----------------------------------------------------------------------------
std::string HelloWorld::GetDetectionType() {
    return "HELLO";
}



MPF_COMPONENT_CREATOR(HelloWorld);
MPF_COMPONENT_DELETER();

