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


#include "frame_transformers/FrameRotator.h"

namespace MPF { namespace COMPONENT {


    FrameRotator::FrameRotator(IFrameTransformer::Ptr innerTransform, int rotationDegrees)
            : BaseDecoratedTransformer(std::move(innerTransform))
            , rotationDegrees_(rotationDegrees) {

        // Checked prior to construction in FrameTransformer factory
        assert(rotationDegrees_ == 90 || rotationDegrees_ == 180 || rotationDegrees_ == 270);
    }


    void FrameRotator::DoFrameTransform(cv::Mat &frame) {
        switch (rotationDegrees_) {
            case 90:
                cv::transpose(frame, frame);
                // Flip around y-axis
                cv::flip(frame, frame, 1);
                return;
            case 180:
                // Flip around both axes
                cv::flip(frame, frame, -1);
                return;
            case 270:
                cv::transpose(frame, frame);
                // Flip around x-axis
                cv::flip(frame, frame, 0);
                return;
        }
    }


    void FrameRotator::DoReverseTransform(MPFImageLocation &detectionLocation) {
        const cv::Point &topLeft(GetRevertedTopLeftCorner(detectionLocation));

        detectionLocation.x_left_upper = topLeft.x;
        detectionLocation.y_left_upper = topLeft.y;

        if (rotationDegrees_ == 90 || rotationDegrees_ == 270) {
            std::swap(detectionLocation.width, detectionLocation.height);
        }
    }


    cv::Point FrameRotator::GetRevertedTopLeftCorner(const MPFImageLocation &imageLocation) const {
        cv::Rect detectionRect(imageLocation.x_left_upper, imageLocation.y_left_upper,
                               imageLocation.width, imageLocation.height);

        const cv::Size &originalFrameSize(GetInnerFrameSize());

        switch (rotationDegrees_) {
            case 90:
                return cv::Point(detectionRect.tl().y,
                                 originalFrameSize.height - detectionRect.br().x);
            case 180:
                return cv::Point(originalFrameSize.width - detectionRect.br().x,
                                 originalFrameSize.height - detectionRect.br().y);
            case 270:
                return cv::Point(originalFrameSize.width - detectionRect.br().y,
                                 detectionRect.tl().x);
            default:
                return detectionRect.tl();
        }
    }


    void FrameRotator::AddDetectionProperties(MPFImageLocation &imageLocation) {
        imageLocation.detection_properties["ROTATION"] = std::to_string(rotationDegrees_);
    }


    cv::Size FrameRotator::GetFrameSize() {
        const cv::Size &innerSize(GetInnerFrameSize());
        if (rotationDegrees_ == 90 || rotationDegrees_ == 270) {
            return cv::Size(innerSize.height, innerSize.width);
        }
        else {
            return innerSize;
        }
    }


}}
