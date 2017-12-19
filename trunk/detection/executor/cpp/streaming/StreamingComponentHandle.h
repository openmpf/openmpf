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


#include<string>
#include <MPFDetectionComponent.h>

namespace MPF { namespace COMPONENT {

    class StreamingComponentHandle {
    public:
        explicit StreamingComponentHandle(const std::string &lib_path);
        ~StreamingComponentHandle();


        void SetRunDirectory(const std::string &run_dir);

        bool Init();

        bool Close();

        MPFComponentType GetComponentType();

        bool Supports(MPFDetectionDataType data_type);

        std::string GetDetectionType();

        MPFDetectionError SetupJob(const MPFJob &job);

        MPFDetectionError ProcessFrame(const cv::Mat &frame, bool &activityFound);

        MPFDetectionError GetVideoTracks(std::vector<MPFVideoTrack> &tracks);


    private:
        void *lib_handle_ = nullptr;

        typedef MPFStreamingDetectionComponent* create_t();
        typedef void destroy_t(MPFStreamingDetectionComponent*);

        destroy_t* component_deleter_ = nullptr;

        MPFStreamingDetectionComponent* loaded_component_ = nullptr;
    };

}}


#endif //MPF_STREAMINGCOMPONENTHANDLE_H
