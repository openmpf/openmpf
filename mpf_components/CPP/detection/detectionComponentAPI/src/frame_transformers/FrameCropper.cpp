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

#include "frame_transformers/FrameCropper.h"


namespace MPF { namespace COMPONENT {


    FrameCropper::FrameCropper(IFrameTransformer::Ptr innerTransform, const cv::Rect &regionOfInterest)
            : BaseDecoratedTransformer(std::move(innerTransform))
            , regionOfInterest_(GetIntersectingRegion(regionOfInterest)) {
    }


    void FrameCropper::DoFrameTransform(cv::Mat &frame) {
        frame = frame(regionOfInterest_);
    }


    void FrameCropper::DoReverseTransform(MPFImageLocation &imageLocation) {
        imageLocation.x_left_upper += regionOfInterest_.x;
        imageLocation.y_left_upper += regionOfInterest_.y;
    }


    cv::Size FrameCropper::GetFrameSize() {
        return regionOfInterest_.size();
    }


    cv::Rect FrameCropper::GetIntersectingRegion(const cv::Rect &regionOfInterest) const {
        cv::Rect frameRect(cv::Point(0, 0), GetInnerFrameSize());
        return regionOfInterest & frameRect;
    }


    void FrameCropper::AddDetectionProperties(MPFImageLocation &imageLocation) {
        imageLocation.detection_properties.insert({
              { "SEARCH_REGION_TOP_LEFT_X", std::to_string(regionOfInterest_.x) },
              { "SEARCH_REGION_TOP_LEFT_Y", std::to_string(regionOfInterest_.y) },
              { "SEARCH_REGION_BOTTOM_RIGHT_X", std::to_string(regionOfInterest_.br().x) },
              { "SEARCH_REGION_BOTTOM_RIGHT_Y", std::to_string(regionOfInterest_.br().y) }
        });
    }

}}
