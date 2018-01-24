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


#ifndef MPF_STREAMINGCOMPONENTHANDLE_H
#define MPF_STREAMINGCOMPONENTHANDLE_H


#include <functional>
#include <memory>
#include <string>
#include <vector>

#include <dlfcn.h>

#include <MPFStreamingDetectionComponent.h>


namespace MPF { namespace COMPONENT {

    class StreamingComponentHandle {
    public:
        StreamingComponentHandle(const std::string &lib_path, const MPFStreamingVideoJob &job);

        std::string GetDetectionType();

        void BeginSegment(const VideoSegmentInfo &segment_info);

        bool ProcessFrame(const cv::Mat &frame, int frame_number);

        std::vector<MPFVideoTrack> EndSegment();


    private:
        using loaded_component_t
            = std::unique_ptr<MPFStreamingDetectionComponent, void(*)(MPFStreamingDetectionComponent*)>;

        std::unique_ptr<void, decltype(&dlclose)> lib_handle_;

        loaded_component_t loaded_component_;

        static loaded_component_t LoadComponent(void* lib_handle, const MPFStreamingVideoJob &job);

        template <typename TFunc>
        static TFunc* LoadFunction(void* lib_handle, const char * symbol_name);

        [[noreturn]] static void WrapComponentException(const std::string &component_method);
    };

}}


#endif //MPF_STREAMINGCOMPONENTHANDLE_H
