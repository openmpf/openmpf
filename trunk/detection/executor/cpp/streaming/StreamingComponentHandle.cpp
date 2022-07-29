/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

#include "StreamingComponentHandle.h"
#include "ExecutorErrors.h"

namespace MPF { namespace COMPONENT {

    StreamingComponentHandle::StreamingComponentHandle(const std::string &lib_path,
                                                       const MPFStreamingVideoJob &job)
    try : component_(lib_path, "streaming_component_creator", "streaming_component_deleter", &job)
    {
    }
    catch (const std::exception &ex) {
        throw FatalError(ExitCode::COMPONENT_LOAD_ERROR, ex.what());
    }



    std::string StreamingComponentHandle::GetDetectionType() {
        try {
            return component_->GetDetectionType();
        }
        catch (...) {
            WrapComponentException("GetDetectionType");
        }
    }


    void StreamingComponentHandle::BeginSegment(const VideoSegmentInfo &segment_info) {
        try {
            component_->BeginSegment(segment_info);
        }
        catch (...) {
            WrapComponentException("BeginSegment");
        }
    }

    bool StreamingComponentHandle::ProcessFrame(const cv::Mat &frame, int frame_number) {
        try {
            return component_->ProcessFrame(frame, frame_number);
        }
        catch (...) {
            WrapComponentException("ProcessFrame");
        }
    }

    std::vector<MPFVideoTrack> StreamingComponentHandle::EndSegment() {
        try {
            return component_->EndSegment();
        }
        catch (...) {
            WrapComponentException("EndSegment");
        }
    }


    void StreamingComponentHandle::WrapComponentException(const std::string &component_method) {
        try {
            throw;
        }
        catch (const std::exception &ex) {
            throw InternalComponentError(component_method, ex.what());
        }
        catch (...) {
            throw InternalComponentError(component_method);
        }
    }
}}