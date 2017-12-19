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

namespace MPF { namespace COMPONENT {

    StreamingComponentHandle::StreamingComponentHandle(const std::string &lib_path) {
        lib_handle_ = dlopen(lib_path.c_str(), RTLD_NOW);
        if (lib_handle_ == nullptr) {
            std::string dl_error_msg = dlerror();
            throw std::runtime_error("Failed to open component library: " + dl_error_msg);
        }

        auto component_creator = (create_t*) dlsym(lib_handle_, "component_creator");
        if (component_creator == nullptr) {
            std::string dl_error_msg = dlerror();
            dlclose(lib_handle_);
            throw std::runtime_error("dlsym failed for component_creator: " + dl_error_msg);
        }

        component_deleter_ = (destroy_t*) dlsym(lib_handle_, "component_deleter");
        if (component_deleter_ == nullptr) {
            std::string dl_error_msg = dlerror();
            dlclose(lib_handle_);
            throw std::runtime_error("dlsym failed for component_deleter: " + dl_error_msg);
        }

        loaded_component_ = component_creator();
    }


    StreamingComponentHandle::~StreamingComponentHandle() {
        if (loaded_component_ != nullptr) {
           component_deleter_(loaded_component_);
        }
        if (lib_handle_ != nullptr) {
            dlclose(lib_handle_);
        }
    }

    void StreamingComponentHandle::SetRunDirectory(const std::string &run_dir) {
        loaded_component_->SetRunDirectory(run_dir);

    }

    bool StreamingComponentHandle::Init() {
        return loaded_component_->Init();
    }

    bool StreamingComponentHandle::Close() {
        return loaded_component_->Close();
    }

    MPFComponentType StreamingComponentHandle::GetComponentType() {
        return loaded_component_->GetComponentType();
    }

    bool StreamingComponentHandle::Supports(MPFDetectionDataType data_type) {
        return loaded_component_->Supports(data_type);
    }

    std::string StreamingComponentHandle::GetDetectionType() {
        return loaded_component_->GetDetectionType();
    }

    MPFDetectionError StreamingComponentHandle::SetupJob(const MPFJob &job) {
        return loaded_component_->SetupJob(job);
    }

    MPFDetectionError StreamingComponentHandle::ProcessFrame(const cv::Mat &frame, bool &activityFound) {
        return loaded_component_->ProcessFrame(frame, activityFound);
    }

    MPFDetectionError StreamingComponentHandle::GetVideoTracks(std::vector<MPFVideoTrack> &tracks) {
        return loaded_component_->GetVideoTracks(tracks);
    }
}}