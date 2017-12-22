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

    StreamingComponentHandle::StreamingComponentHandle(const std::string &lib_path,
                                                       const std::string &app_dir,
                                                       const MPFStreamingVideoJob &job)
            : lib_handle_(dlopen(lib_path.c_str(), RTLD_NOW), dlclose)
            , loaded_component_(LoadComponent(lib_handle_.get())) {

        loaded_component_->SetRunDirectory(app_dir + "/../plugins");
        bool init_success = loaded_component_->Init();
        if (!init_success) {
            throw std::runtime_error("The loaded component's Init method failed.");
        }
        loaded_component_->SetupJob(job);
    }


    StreamingComponentHandle::loaded_component_t StreamingComponentHandle::LoadComponent(void *lib_handle) {
        if (lib_handle == nullptr) {
            throw std::runtime_error(std::string("Failed to open component library: ") + dlerror());
        }

        auto create_component_fn
                = LoadFunction<MPFStreamingDetectionComponent* ()>(lib_handle, "component_creator");
        auto delete_component_fn
                = LoadFunction<void (MPFStreamingDetectionComponent*)>(lib_handle, "component_deleter");

        loaded_component_t loaded_component(create_component_fn(),
                                            [delete_component_fn](MPFStreamingDetectionComponent *component)
                                            {
                                                component->Close();
                                                delete_component_fn(component);
                                            }
        );

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


    MPFComponentType StreamingComponentHandle::GetComponentType() {
        return loaded_component_->GetComponentType();
    }

    bool StreamingComponentHandle::Supports(MPFDetectionDataType data_type) {
        return loaded_component_->Supports(data_type);
    }

    std::string StreamingComponentHandle::GetDetectionType() {
        return loaded_component_->GetDetectionType();
    }

    MPFDetectionError StreamingComponentHandle::ProcessFrame(const cv::Mat &frame, bool &activityFound) {
        return loaded_component_->ProcessFrame(frame, activityFound);
    }

    MPFDetectionError StreamingComponentHandle::GetVideoTracks(std::vector<MPFVideoTrack> &tracks) {
        return loaded_component_->GetVideoTracks(tracks);
    }


}}