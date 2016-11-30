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

#include "frame_transformers/FrameTransformerFactory.h"

#include "frame_transformers/FrameCropper.h"
#include "frame_transformers/FrameFlipper.h"
#include "frame_transformers/FrameRotator.h"
#include "frame_transformers/NoOpFrameTransformer.h"
#include "MPFInvalidPropertyException.h"
#include "detectionComponentUtils.h"

using std::map;
using std::string;

namespace MPF { namespace COMPONENT { namespace FrameTransformerFactory {

    namespace {

        cv::Rect GetRegionOfInterest(const Properties &jobProperties, const cv::Size &frameSize) {

            int regionTopLeftXPos = DetectionComponentUtils::GetProperty<int>(
                    jobProperties, "SEARCH_REGION_TOP_LEFT_X_DETECTION", -1);
            if (regionTopLeftXPos < 0) {
                regionTopLeftXPos = 0;
            }

            int regionTopLeftYPos = DetectionComponentUtils::GetProperty<int>(
                    jobProperties, "SEARCH_REGION_TOP_LEFT_Y_DETECTION", -1);
            if (regionTopLeftYPos < 0) {
                regionTopLeftYPos = 0;
            }

            cv::Point topLeft(regionTopLeftXPos, regionTopLeftYPos);

            int regionBottomRightXPos = DetectionComponentUtils::GetProperty<int>(
                    jobProperties, "SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION", -1);
            if (regionBottomRightXPos <= 0) {
                regionBottomRightXPos = frameSize.width;
            }

            int regionBottomRightYPos = DetectionComponentUtils::GetProperty<int>(
                    jobProperties, "SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION", -1);
            if (regionBottomRightYPos <= 0) {
                regionBottomRightYPos = frameSize.height;
            }
            cv::Point bottomRight(regionBottomRightXPos, regionBottomRightYPos);

            return cv::Rect(topLeft, bottomRight);
        }


        bool FrameCroppingIsEnabled(const Properties &jobProperties) {
            return DetectionComponentUtils::GetProperty<bool>(
                jobProperties, "SEARCH_REGION_ENABLE_DETECTION", false);
        }


        void AddFlipperIfNeeded(const MPFJob &job, IFrameTransformer::Ptr &currentTransformer) {
            bool shouldFlip;
            if (DetectionComponentUtils::GetProperty<bool>(job.job_properties, "AUTO_FLIP", false)) {
                shouldFlip = DetectionComponentUtils::GetProperty<bool>(job.media_properties, "HORIZONTAL_FLIP",false);
            }
            else {
                shouldFlip = DetectionComponentUtils::GetProperty<bool>(job.job_properties, "HORIZONTAL_FLIP",false);
            }

            if (shouldFlip) {
                currentTransformer.reset(new FrameFlipper(std::move(currentTransformer)));
            }
        }


        void AddRotatorIfNeeded(const MPFJob &job, IFrameTransformer::Ptr &currentTransformer) {
            string rotationKey = "ROTATION";
            int rotation = 0;
            if (DetectionComponentUtils::GetProperty<bool>(job.job_properties, "AUTO_ROTATE",false)) {
                rotation = DetectionComponentUtils::GetProperty<int>(job.media_properties, rotationKey, 0);
            }
            else {
                rotation = DetectionComponentUtils::GetProperty<int>(job.job_properties, rotationKey, 0);
            }

            if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
                throw MPFInvalidPropertyException(rotationKey,
                                                  "Rotation degrees must be 0, 90, 180, or 270.",
                                                  MPF_INVALID_ROTATION);
            }

            if (rotation != 0) {
                currentTransformer.reset(new FrameRotator(std::move(currentTransformer), rotation));
            }
        }


        void AddCropperIfNeeded(const Properties &jobProperties, const cv::Size &inputVideoSize,
                                IFrameTransformer::Ptr &currentTransformer) {

            if (!FrameCroppingIsEnabled(jobProperties)) {
                return;
            }

            cv::Rect regionOfInterest(GetRegionOfInterest(jobProperties, inputVideoSize));
            bool regionOfInterestIsEntireFrame =
                    regionOfInterest.tl() == cv::Point() && regionOfInterest.size() == inputVideoSize;

            if (!regionOfInterestIsEntireFrame) {
                currentTransformer.reset(new FrameCropper(std::move(currentTransformer), regionOfInterest));
            }
        }
    }



    IFrameTransformer::Ptr GetTransformer(const MPFJob &job, const cv::Size &inputVideoSize) {

        IFrameTransformer::Ptr transformer(new NoOpFrameTransformer(inputVideoSize));

        AddRotatorIfNeeded(job, transformer);
        AddFlipperIfNeeded(job, transformer);
        AddCropperIfNeeded(job.job_properties, inputVideoSize, transformer);

        return transformer;

    }
}}}
