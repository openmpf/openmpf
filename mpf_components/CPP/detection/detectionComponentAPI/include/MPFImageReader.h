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

#ifndef MPF_CPP_COMPONENTS_IMAGEREADER_H
#define MPF_CPP_COMPONENTS_IMAGEREADER_H


#include <opencv2/core/core.hpp>

#include "detection_base.h"
#include "frame_transformers/IFrameTransformer.h"



namespace MPF { namespace COMPONENT {

    class MPFImageReader {

    public:
        explicit MPFImageReader(const MPFImageJob &job);

        cv::Mat GetImage() const;

        void ReverseTransform(MPFImageLocation &imageLocation) const;
    private:
        cv::Mat image_;
        IFrameTransformer::Ptr frameTransformer_;

        static IFrameTransformer::Ptr GetFrameTransformer(const MPFJob &job, const cv::Mat &image);
    };

}}


#endif //MPF_CPP_COMPONENTS_IMAGEREADER_H
