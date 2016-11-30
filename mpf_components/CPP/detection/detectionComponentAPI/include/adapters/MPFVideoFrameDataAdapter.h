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

#ifndef MPF_VIDEO_FRAME_DATA_ADAPTER_H_
#define MPF_VIDEO_FRAME_DATA_ADAPTER_H_

#include <vector>
#include <string>

#include "detection_base.h"
#include "MPFVideoDetectionComponentAdapter.h"
#include "VideoSegmentToFramesConverter.h"

namespace MPF {

namespace COMPONENT {

class MPFVideoFrameDataDetectionAdapter : public MPFVideoDetectionComponentAdapter {

  public:
    virtual ~MPFVideoFrameDataDetectionAdapter() {}

    virtual MPFDetectionError GetDetectionsFromVideoFrameData(
        const MPFVideoFrameData &video_byte_data,
        const Properties &job_properties,
        const std::string& job_name,
        std::vector <MPFVideoTrack> &tracks) = 0;

  protected:
    MPFVideoFrameDataDetectionAdapter() = default;

};

} // namespace COMPONENT
} // namespace MPF

#endif  // MPF_VIDEO_FRAME_DATA_ADAPTER_H
