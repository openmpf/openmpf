/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

#include <exception>
#include <stdexcept>
#include <string>

#include <jni.h>

#include <opencv2/core.hpp>
#include <opencv2/videoio.hpp>

#include "JniHelper.h"

extern "C" {

using namespace cv;

JNIEXPORT int JNICALL Java_org_mitre_mpf_framecounter_FrameCounter_countNative
  (JNIEnv *env, jobject frameCounterInstance, jstring sourceVideoPath, bool bruteForce)
{
    JniHelper jni(env);
    try {
        std::string videoPath = jni.ToStdString(sourceVideoPath);
        VideoCapture src(videoPath);
        if (!src.isOpened()) {
            throw std::runtime_error("Unable to open source video: " + videoPath);
        }

        cv::Mat placeHolder;
        if (!src.read(placeHolder)) {
            jclass exceptionClz = jni.FindClass(
                    "org/mitre/mpf/framecounter/NotReadableByOpenCvException");
            std::string errorMsg = "OpenCV could not read first frame of " + videoPath
                        + ". Video format is not supported by OpenCV or the video is corrupt.";
            jni.ThrowNew(exceptionClz, errorMsg.c_str());
            return -1;
        }

        if (bruteForce) {
            // The first frame was already processed.
            int count = 1;
            while (src.grab()) {
                count++;
            }
            return count;
        }
        else {
            return static_cast<int>(src.get(cv::CAP_PROP_FRAME_COUNT));
        }
    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (...) {
        jni.ReportCppException();
    }
    return -1;
}

} // extern "C"
