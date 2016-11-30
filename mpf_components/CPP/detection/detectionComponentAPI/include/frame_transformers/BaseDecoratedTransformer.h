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



#ifndef MPF_CPP_COMPONENTS_BASEDECORATOR_H
#define MPF_CPP_COMPONENTS_BASEDECORATOR_H

#include <opencv2/core/core.hpp>

#include "detection_base.h"
#include "IFrameTransformer.h"


namespace MPF { namespace COMPONENT {

    /**
     * This class implements both a decorator pattern and a template method pattern.
     * The decorator pattern is used to make it possible to combine any number of frame transformers.
     * The template method pattern is used encapsulate the process of calling the inner transform in the right
     * order.
     * When doing the forward transform, the inner transform gets called first. The reverseTransform
     * occurs in the opposite order, so the subclass's reverseTransform is called first, then the inner
     * reverseTransform occurs.
     */
    class BaseDecoratedTransformer : public IFrameTransformer {

    public:

        /**
         * Calls in the inner tranform before calling the subclass's doFrameTransform method.
         * @param frame[in,out] Frame to transform.
         */
        void TransformFrame(cv::Mat &frame) override;


        /**
         * Calls the subclass's doReverseTransform before calling the inner transformer's reverseTransform.
         * @param imageLocation[in,out]  The image location to do the reverse transform on.
         */
        void ReverseTransform(MPFImageLocation &imageLocation) override;


    protected:
        explicit BaseDecoratedTransformer(IFrameTransformer::Ptr frameTransformer);

        /**
         * Subclasses override this method to implement the frame transformation
         * @param frame[in,out] Frame to transform.
         */
        virtual void DoFrameTransform(cv::Mat &frame) = 0;


        /**
         * Subclasses override this method to implement the reverse transform
         * @param imageLocation[in,out]  The image location to do the reverse transform on.
         */
        virtual void DoReverseTransform(MPFImageLocation &imageLocation) = 0;

        virtual void AddDetectionProperties(MPFImageLocation &imageLocation) = 0;

        cv::Size GetInnerFrameSize() const;

    private:
        const IFrameTransformer::Ptr innerTransform_;
    };

}}


#endif //MPF_CPP_COMPONENTS_BASEDECORATOR_H
