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

#include <dlfcn.h>
#include "StreamingComponentHandle.h"
#include "InternalComponentError.h"

namespace MPF { namespace COMPONENT {

    StreamingComponentHandle::StreamingComponentHandle(const std::string &lib_path,
                                                       const MPFStreamingVideoJob &job)
            : lib_handle_(dlopen(lib_path.c_str(), RTLD_NOW), dlclose)
            , loaded_component_(LoadComponent(lib_handle_.get(), job)) {
    }


    StreamingComponentHandle::loaded_component_t StreamingComponentHandle::LoadComponent(
            void *lib_handle, const MPFStreamingVideoJob &job) {

        if (lib_handle == nullptr) {
            throw std::runtime_error(std::string("Failed to open component library: ") + dlerror());
        }

        auto create_component_fn
                = LoadFunction<MPFStreamingDetectionComponent* (const MPFStreamingVideoJob*)>(
                        lib_handle, "streaming_component_creator");

        auto delete_component_fn
                = LoadFunction<void (MPFStreamingDetectionComponent*)>(lib_handle, "streaming_component_deleter");


        loaded_component_t loaded_component(nullptr, delete_component_fn);
        try {
            loaded_component = { create_component_fn(&job),  delete_component_fn};
        }
        catch (...) {
            WrapComponentException("constructor");
        }

        if (loaded_component == nullptr) {
            throw std::runtime_error("Failed to load component because the component_creator function returned null.");
        }
        return loaded_component;
    }


    template<typename TFunc>
    TFunc* StreamingComponentHandle::LoadFunction(void *lib_handle, const char * symbol_name) {
        auto result = reinterpret_cast<TFunc*>(dlsym(lib_handle, symbol_name));
        if (result == nullptr) {
            throw std::runtime_error(std::string("dlsym failed for ") + symbol_name + ": " + dlerror());
        }
        return result;
    }


    std::string StreamingComponentHandle::GetDetectionType() {
        try {
            return loaded_component_->GetDetectionType();
        }
        catch (...) {
            WrapComponentException("GetDetectionType");
        }
    }


    void StreamingComponentHandle::BeginSegment(const VideoSegmentInfo &segment_info) {
        try {
            loaded_component_->BeginSegment(segment_info);
        }
        catch (...) {
            WrapComponentException("BeginSegment");
        }
    }

    bool StreamingComponentHandle::ProcessFrame(const cv::Mat &frame, int frame_number) {
        try {
            return loaded_component_->ProcessFrame(frame, frame_number);
        }
        catch (...) {
            WrapComponentException("ProcessFrame");
        }
    }

    std::vector<MPFVideoTrack> StreamingComponentHandle::EndSegment() {
        try {
            return loaded_component_->EndSegment();
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