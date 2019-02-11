/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
#include <string>
#include <exception>
#include <opencv2/core.hpp>
#include <opencv2/videoio.hpp>
#include <opencv2/imgcodecs.hpp>
#include <MPFVideoCapture.h>

/* Header for class org_mitre_mpf_frameextractor_FrameExtractor */

#ifndef _Included_org_mitre_mpf_frameextractor_FrameExtractor
#define _Included_org_mitre_mpf_frameextractor_FrameExtractor
#ifdef __cplusplus
extern "C" {

using namespace cv;

#endif
/*
 * Class:     org_mitre_mpf_frameextractor_FrameExtractor
 * Method:    executeNative
 * Signature: (java/lang/String;java/lang/String;java/util/Map;)I
 */
JNIEXPORT int JNICALL Java_org_mitre_mpf_frameextractor_FrameExtractor_executeNative
  (JNIEnv *env, jobject frameExtractorInstance, jstring video, jstring destinationPath, jobject map)
{
    if (env == nullptr) {
        return 8700;
    }

    // Get the bounding box map.
    jclass clzFrameExtractor = env->GetObjectClass(frameExtractorInstance);
    jmethodID clzFrameExtractor_fnGetFrames = env->GetMethodID(clzFrameExtractor, "getFrames", "()Ljava/util/Set;");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8701;
    }
    jmethodID clzFrameExtractor_fnMakeFilename = env->GetMethodID(clzFrameExtractor, "makeFilename",
                                                                  "(Ljava/lang/String;I)Ljava/lang/String;");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8702;
    }
    jobject framesSet = env->CallObjectMethod(frameExtractorInstance, clzFrameExtractor_fnGetFrames);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8703;
    }

    // Get the iterator class and methods.
    jclass clzIterator = env->FindClass("java/util/Iterator");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8704;
    }
    jmethodID clzIterator_fnNext = env->GetMethodID(clzIterator, "next", "()Ljava/lang/Object;");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8705;
    }
    jmethodID clzIterator_fnHasNext = env->GetMethodID(clzIterator, "hasNext", "()Z");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8706;
    }

    // Get Set class and methods.
    jclass clzSet = env->FindClass("java/util/TreeSet");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8707;
    }
    jmethodID clzSet_fnIterator = env->GetMethodID(clzSet, "iterator", "()Ljava/util/Iterator;");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8708;
    }

    // Get the Map class and methods.
    jclass clzMap = env->FindClass("java/util/Map");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8709;
    }
    jmethodID clzMap_fnPut = env->GetMethodID(clzMap, "put",
                                              "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8710;
    }

    jclass clzInteger = env->FindClass("java/lang/Integer");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8711;
    }
    jmethodID clzInteger_fnIntValue = env->GetMethodID(clzInteger, "intValue", "()I");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 8712;
    }

    std::string videoPath;
    {
        const char *inChars = env->GetStringUTFChars(video, nullptr);
        if (inChars == nullptr) {
            return 8713;
        }
        videoPath = inChars;
        env->ReleaseStringUTFChars(video, inChars);
    }
    if (videoPath.empty()) {
        return 8714;
    }

    try {
        MPF::COMPONENT::MPFVideoCapture src(videoPath);

        Mat frame;

        jobject iterator = env->CallObjectMethod(framesSet, clzSet_fnIterator);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8715;
        }

        // While there are more frames in the set...
        while (env->CallBooleanMethod(iterator, clzIterator_fnHasNext) == JNI_TRUE) {
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return 8716;
            }
            // Get the next frame index from the set...
            jobject boxed = env->CallObjectMethod(iterator, clzIterator_fnNext);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return 8717;
            }

            // Unbox it because Java...
            jint unboxed = env->CallIntMethod(boxed, clzInteger_fnIntValue);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return 8718;
            }

            // Cast it to something OpenCV can use...
            int nextFrameIndex = (int) unboxed;

            // Tell OpenCV to go to that frame next...
            src.SetFramePosition(nextFrameIndex);

            // Get the frame...
            src >> frame;

            // If that frame is empty, we've reached the end of the video.
            if (frame.empty()) { break; }

            // Otherwise, extract that frame.
            jstring filename = (jstring) env->CallObjectMethod(frameExtractorInstance,
                                                               clzFrameExtractor_fnMakeFilename,
                                                               destinationPath,
                                                               unboxed);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return 8719;
            }
            env->CallObjectMethod(map, clzMap_fnPut, boxed, filename);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return 8720;
            }

            if (filename != nullptr) {
                const char *destChars = env->GetStringUTFChars(filename, nullptr);
                if (destChars == nullptr) {
                    return 8721;
                }
                imwrite(destChars, frame);
                env->CallObjectMethod(map, clzMap_fnPut, boxed, filename);
                if (env->ExceptionCheck()) {
                    env->ReleaseStringUTFChars(filename, destChars);
                    env->ExceptionClear();
                    return 8722;
                }
                env->ReleaseStringUTFChars(filename, destChars);
            }
        }
        return 0;
    }
    catch (std::exception &) {
        return 8723;
    }
}

#ifdef __cplusplus
}
#endif
#endif
