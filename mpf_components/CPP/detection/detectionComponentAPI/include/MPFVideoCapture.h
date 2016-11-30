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

#ifndef DETECTIONUTILS_VIDEOCAPTURE_H
#define DETECTIONUTILS_VIDEOCAPTURE_H

#include <map>
#include <memory>
#include <string>

#include <highgui.h>
#include <opencv2/core/core.hpp>

#include "detection_base.h"
#include "frame_transformers/IFrameTransformer.h"

namespace MPF { namespace COMPONENT {

class MPFVideoCapture {

  public:
    /**
     * Initializes a new MPFVideoCapture instance, using the frame transformers specified in jobProperties.
     * @param videoJob
     * @throws std::invalid_argument videoJob contains invalid property
     */
    explicit MPFVideoCapture(const MPFVideoJob &videoJob);

    bool Read(cv::Mat &frame);

    MPFVideoCapture &operator>>(cv::Mat &frame);

    bool IsOpened() const;

    int GetFrameCount();

    void SetFramePosition(int frameIdx);

    int GetCurrentFramePosition();

    void Release();

    double GetFrameRate();

    cv::Size GetFrameSize();

    cv::Size GetOriginalFrameSize();

    double GetProperty(int propId);

    bool SetProperty(int propId, double value);

    int GetFourCharCodecCode();

    void ReverseTransform(MPFVideoTrack &videoTrack);

    void ReverseTransform(MPFImageLocation &imageLocation);

    private:
        cv::VideoCapture cvVideoCapture_;
        const IFrameTransformer::Ptr frameTransformer_;

    double GetPropertyInternal(int propId);

        IFrameTransformer::Ptr GetFrameTransformer(const MPFJob &job);
    };

}}

#endif //DETECTIONUTILS_VIDEOCAPTURE_H
