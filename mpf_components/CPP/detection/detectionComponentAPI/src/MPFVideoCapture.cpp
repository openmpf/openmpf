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
#include "MPFVideoCapture.h"


namespace MPF { namespace COMPONENT {


    MPFVideoCapture::MPFVideoCapture(const MPFVideoJob &videoJob)
            : cvVideoCapture_(videoJob.data_uri)
            , frameTransformer_(GetFrameTransformer(videoJob)) {
    }

    IFrameTransformer::Ptr MPFVideoCapture::GetFrameTransformer(const MPFJob &job) {
        return FrameTransformerFactory::GetTransformer(job, GetOriginalFrameSize());
    }


    bool MPFVideoCapture::IsOpened() const {
        return cvVideoCapture_.isOpened();
    }

    int MPFVideoCapture::GetFrameCount() {
        return (int) GetPropertyInternal(CV_CAP_PROP_FRAME_COUNT);
    }


    void MPFVideoCapture::SetFramePosition(int frameIdx) {
        SetProperty(CV_CAP_PROP_POS_FRAMES, frameIdx);
    }


    int MPFVideoCapture::GetCurrentFramePosition() {
        return (int) GetPropertyInternal(CV_CAP_PROP_POS_FRAMES);
    }


    bool MPFVideoCapture::Read(cv::Mat &frame) {
        bool wasRead = cvVideoCapture_.read(frame);
        if (wasRead) {
            frameTransformer_->TransformFrame(frame);
        }
        return wasRead;
    }

    MPFVideoCapture &MPFVideoCapture::operator>>(cv::Mat &frame) {
        Read(frame);
        return *this;
    }

    void MPFVideoCapture::Release() {
        cvVideoCapture_.release();
    }

    double MPFVideoCapture::GetFrameRate() {
        return GetPropertyInternal(CV_CAP_PROP_FPS);
    }


    cv::Size MPFVideoCapture::GetFrameSize() {
        return frameTransformer_->GetFrameSize();
    }

    cv::Size MPFVideoCapture::GetOriginalFrameSize() {
        int width = (int) GetPropertyInternal(CV_CAP_PROP_FRAME_WIDTH);
        int height = (int) GetPropertyInternal(CV_CAP_PROP_FRAME_HEIGHT);
        return cv::Size(width, height);
    }


    double MPFVideoCapture::GetProperty(int propId) {
        switch (propId) {
            case CV_CAP_PROP_FRAME_WIDTH:
                return GetFrameSize().width;
            case CV_CAP_PROP_FRAME_HEIGHT:
                return GetFrameSize().height;
            default:
                return GetPropertyInternal(propId);
        }
    }

    double MPFVideoCapture::GetPropertyInternal(int propId) {
        return cvVideoCapture_.get(propId);
    }

    bool MPFVideoCapture::SetProperty(int propId, double value) {
        return cvVideoCapture_.set(propId, value);
    }


    int MPFVideoCapture::GetFourCharCodecCode() {
        return (int) GetPropertyInternal(CV_CAP_PROP_FOURCC);
    }

    void MPFVideoCapture::ReverseTransform(MPFVideoTrack &videoTrack) {
        for (auto &frameLocationPair : videoTrack.frame_locations) {
            ReverseTransform(frameLocationPair.second);
        }

    }

    void MPFVideoCapture::ReverseTransform(MPFImageLocation &imageLocation) {
        frameTransformer_->ReverseTransform(imageLocation);
    }

}}












