/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

#pragma once

#include <string_view>
#include <vector>

#include <MPFDetectionComponent.h>
#include <MPFDetectionObjects.h>

#include "detection.pb.h"
#include "JobContext.h"

namespace MPF::COMPONENT::ProtobufResponseUtil {
    namespace detail {
        namespace mpf_buffers = org::mitre::mpf::wfm::buffers;

        mpf_buffers::DetectionResponse InitDetectionResponse(const JobContext& context);

        std::vector<unsigned char> Serialize(
                const mpf_buffers::DetectionResponse& detection_response);

        void AddToProtobuf(
                const JobContext& context,
                const std::vector<MPFVideoTrack> &tracks,
                mpf_buffers::DetectionResponse& response);

        void AddToProtobuf(
                const JobContext& context,
                const std::vector<MPFImageLocation>& tracks,
                mpf_buffers::DetectionResponse& response);

        void AddToProtobuf(
                const JobContext& context,
                const std::vector<MPFAudioTrack>& tracks,
                mpf_buffers::DetectionResponse& response);

        void AddToProtobuf(
                const JobContext& context,
                const std::vector<MPFGenericTrack>& tracks,
                mpf_buffers::DetectionResponse& response);
    }


    template <typename TResp>
    std::vector<unsigned char> PackResponse(const JobContext& context, const TResp& results) {
        auto detection_response = detail::InitDetectionResponse(context);
        detail::AddToProtobuf(context, results, detection_response);
        return detail::Serialize(detection_response);
    }

    std::vector<unsigned char> PackErrorResponse(
            const JobContext& context, MPFDetectionError error_code,
            std::string_view explanation);
}
