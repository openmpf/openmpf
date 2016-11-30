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

#ifndef MPF_AUDIO_AND_VIDEO_DETECTION_COMPONENT_ADAPTER_H_
#define MPF_AUDIO_AND_VIDEO_DETECTION_COMPONENT_ADAPTER_H_

#include <vector>
#include <string>
#include <map>

#include "detection_base.h"

namespace MPF {
    namespace COMPONENT {

        class MPFAudioAndVideoDetectionComponentAdapter : public MPFDetectionComponent {
        public:
            virtual ~MPFAudioAndVideoDetectionComponentAdapter() { }

            MPFDetectionError GetDetections(const MPFImageJob &job, std::vector<MPFImageLocation> &locations) override {
                return MPFDetectionError::MPF_UNSUPPORTED_DATA_TYPE;
            }


            virtual bool Supports(MPFDetectionDataType data_type) {
                return MPFDetectionDataType::AUDIO == data_type;
            };

            virtual std::string GetDetectionType() = 0;

        protected:
            MPFAudioAndVideoDetectionComponentAdapter() = default;
        };

    } // namespace COMPONENT
} // namespace MPF

#endif  // MPF_AUDIO_AND_VIDEO_DETECTION_COMPONENT_ADAPTER_H_
