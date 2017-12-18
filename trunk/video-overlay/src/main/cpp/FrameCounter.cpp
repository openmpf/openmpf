/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

#include <jni.h>
#include <stdlib.h>
#include <opencv2/core.hpp>
#include <opencv2/videoio.hpp>

#ifndef _Included_org_mitre_mpf_framecounter_FrameCounter
#define _Included_org_mitre_mpf_framecounter_FrameCounter
#ifdef __cplusplus
extern "C" {

using namespace cv;

#endif

/*
 * Class:     org_mitre_mpf_framecounter_FrameCounter
 * Method:    countNative
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT int JNICALL Java_org_mitre_mpf_framecounter_FrameCounter_countNative
  (JNIEnv *env, jobject frameCounterInstance, jstring sourceVideoPath, bool bruteForce)
{
    int count = -8700;

    if (env != NULL) {
        jclass clzFrameCounter = env->GetObjectClass(frameCounterInstance);

        // Set up the video...
        const char *inChars = env->GetStringUTFChars(sourceVideoPath, NULL);
        if (inChars != NULL) {
            try {
                VideoCapture src(inChars);
                if (!src.isOpened())
                {
                    // Cleanup...
                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);

                    return -8701;
                }

                if (bruteForce) {
                    count = 0;
                    while (src.grab()) {
                        count++;
                    }
                } else {
                    count = static_cast<int>(src.get(cv::CAP_PROP_FRAME_COUNT));
                }

                src.release();
                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
            } catch (cv::Exception) {
                return -8702;
            }
        } else {
            return -8703;
        }
    } else {
        return -8704;
    }
    return (jint)count;
}

#ifdef __cplusplus
}
#endif
#endif
