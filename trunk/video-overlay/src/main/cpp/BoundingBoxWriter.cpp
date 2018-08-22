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
#include <opencv2/opencv.hpp>
#include <opencv2/core.hpp>
#include <opencv2/videoio.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>

#ifndef _Included_org_mitre_mpf_videooverlay_BoundingBoxWriter
#define _Included_org_mitre_mpf_videooverlay_BoundingBoxWriter
#ifdef __cplusplus
extern "C" {

using namespace cv;

#endif
/*
 * Class:     org_mitre_mpf_videooverlay_BoundingBoxWriter
 * Method:    markupVideoNative
 * Signature: ()V
 */
JNIEXPORT int JNICALL Java_org_mitre_mpf_videooverlay_BoundingBoxWriter_markupVideoNative
  (JNIEnv *env, jobject boundingBoxWriterInstance, jstring sourceVideoPath, jstring destinationVideoPath)
{
    if (env != NULL) {

        // Get the bounding box map.
        jclass clzBoundingBoxWriter = env->GetObjectClass(boundingBoxWriterInstance);
        jmethodID clzBoundingBoxWriter_fnGetBoundingBoxMap = env->GetMethodID(clzBoundingBoxWriter, "getBoundingBoxMap", "()Lorg/mitre/mpf/videooverlay/BoundingBoxMap;");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jobject boundingBoxMap = env->CallObjectMethod(boundingBoxWriterInstance, clzBoundingBoxWriter_fnGetBoundingBoxMap);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }

        // Get BoundingBoxMap methods.
        jclass clzBoundingBoxMap = env->GetObjectClass(boundingBoxMap);
        jmethodID clzBoundingBoxMap_fnGet = env->GetMethodID(clzBoundingBoxMap, "get", "(Ljava/lang/Object;)Ljava/lang/Object;"); // May be a list.
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBoxMap_fnContainsKey = env->GetMethodID(clzBoundingBoxMap, "containsKey", "(Ljava/lang/Object;)Z");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jint allFramesKey = env->GetStaticIntField(clzBoundingBoxMap, env->GetStaticFieldID(clzBoundingBoxMap, "ALL_FRAMES", "I"));

        // Get List class and methods.
        jclass clzList = env->FindClass("java/util/List");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzList_fnGet = env->GetMethodID(clzList, "get", "(I)Ljava/lang/Object;");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzList_fnSize = env->GetMethodID(clzList, "size", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }

        // Get BoundingBox class and methods.
        jclass clzBoundingBox = env->FindClass("org/mitre/mpf/videooverlay/BoundingBox");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBox_fnGetX = env->GetMethodID(clzBoundingBox, "getX", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBox_fnGetY = env->GetMethodID(clzBoundingBox, "getY", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBox_fnGetHeight = env->GetMethodID(clzBoundingBox, "getHeight", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBox_fnGetWidth = env->GetMethodID(clzBoundingBox, "getWidth", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBox_fnGetColor = env->GetMethodID(clzBoundingBox, "getColor", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }

        jclass clzInteger = env->FindClass("java/lang/Integer");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzInteger_fnValueOf = env->GetStaticMethodID(clzInteger, "valueOf", "(I)Ljava/lang/Integer;");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }

        // Set up the videos...
        const char *inChars = env->GetStringUTFChars(sourceVideoPath, NULL);
        if (inChars != NULL) {

            try {
                VideoCapture src(inChars);
                if(!src.isOpened())
                {
                    // Cleanup...
                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);

                    return 8700;
                }

                Size cvSize = Size(static_cast<int>(src.get(cv::CAP_PROP_FRAME_WIDTH)),
                                   static_cast<int>(src.get(cv::CAP_PROP_FRAME_HEIGHT)));
                double fps = src.get(cv::CAP_PROP_FPS);
                const char *outChars = env->GetStringUTFChars(destinationVideoPath, NULL);
                if (outChars != NULL) {
                    VideoWriter dest(outChars, VideoWriter::fourcc('M','J','P','G'), fps, cvSize, true);
                    if (!dest.isOpened())
                    {
                        // Cleanup...
                        env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                        env->ReleaseStringUTFChars(destinationVideoPath, outChars);

                        return 8710;
                    }

                    Mat frame;
                    int a = 0, r = 0, g = 0, b = 0;

                    jint currentFrame = 0;
                    while(true)
                    {
                        jobject boxed = env->CallStaticObjectMethod(clzInteger, clzInteger_fnValueOf, currentFrame);
                        if (env->ExceptionCheck()) {
                            env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                            env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                            env->ExceptionClear();
                            return 8701;
                        }

                        // Get the next frame.
                        src >> frame;

                        // if that frame is empty, we've reached the end of the video.
                        if(frame.empty()) { break; }

                        int fWidth = frame.cols;
                        int fHeight = frame.rows;

                        // Otherwise, get the list of boxes that need to be drawn on this frame. This is the union of the boxes
                        // found in keys ALL_FRAMES and currentFrame.
                        jboolean success = env->CallBooleanMethod(boundingBoxMap, clzBoundingBoxMap_fnContainsKey, boxed);
                        if (env->ExceptionCheck()) {
                            env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                            env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                            env->ExceptionClear();
                            return 8701;
                        }
                        if(success == JNI_TRUE) {
                            // Map has elements which are to be drawn on every frame.
                            jobject allFramesElements = env->CallObjectMethod(boundingBoxMap, clzBoundingBoxMap_fnGet, boxed);
                            if (env->ExceptionCheck()) {
                                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                env->ExceptionClear();
                                return 8701;
                            }

                            // Iterate through this list, drawing the box on the frame.
                            jint size = env->CallIntMethod(allFramesElements, clzList_fnSize);
                            if (env->ExceptionCheck()) {
                                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                env->ExceptionClear();
                                return 8701;
                            }
                            for(jint i = 0; i < size; i++) {
                                jobject box = env->CallObjectMethod(allFramesElements, clzList_fnGet, i);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                jint x = env->CallIntMethod(box, clzBoundingBox_fnGetX);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                jint y = env->CallIntMethod(box, clzBoundingBox_fnGetY);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                jint height = env->CallIntMethod(box, clzBoundingBox_fnGetHeight);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                if(height == 0) {
                                    height = cvSize.height;
                                }
                                jint width = env->CallIntMethod(box, clzBoundingBox_fnGetWidth);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                if(width == 0) {
                                    width = cvSize.width;
                                }
                                jint color = env->CallIntMethod(box, clzBoundingBox_fnGetColor);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }

                                a = (((int)color) & 0xFF000000) >> 24;
                                b = (((int)color) & 0x00FF0000) >> 16;
                                g = (((int)color) & 0x0000FF00) >> 8;
                                r = (((int)color) & 0x000000FF) >> 0;

                                rectangle(frame, Rect((int)x, (int)y, (int)width, (int)height), Scalar(r, g, b, a),
                                          (int)(std::max(.0018 * (fHeight < fWidth ? fWidth : fHeight), 1.0)));
                            }
                        }

                        boxed = env->CallStaticObjectMethod(clzInteger, clzInteger_fnValueOf, allFramesKey);
                        if (env->ExceptionCheck()) {
                            env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                            env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                            env->ExceptionClear();
                            return 8701;
                        }

                        success = env->CallBooleanMethod(boundingBoxMap, clzBoundingBoxMap_fnContainsKey, boxed);
                        if (env->ExceptionCheck()) {
                            env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                            env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                            env->ExceptionClear();
                            return 8701;
                        }
                        if(success == JNI_TRUE) {
                            // Map has elements which are to be drawn on every frame.
                            jobject allFramesElements = env->CallObjectMethod(boundingBoxMap, clzBoundingBoxMap_fnGet, boxed);
                            if (env->ExceptionCheck()) {
                                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                env->ExceptionClear();
                                return 8701;
                            }

                            // Iterate through this list, drawing the box on the frame.
                            jint size = env->CallIntMethod(allFramesElements, clzList_fnSize);
                            if (env->ExceptionCheck()) {
                                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                env->ExceptionClear();
                                return 8701;
                            }
                            for(jint i = 0; i < size; i++) {
                                jobject box = env->CallObjectMethod(allFramesElements, clzList_fnGet, i);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                jint x = env->CallIntMethod(box, clzBoundingBox_fnGetX);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                jint y = env->CallIntMethod(box, clzBoundingBox_fnGetY);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                jint height = env->CallIntMethod(box, clzBoundingBox_fnGetHeight);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                if(height == 0) {
                                    height = cvSize.height;
                                }
                                jint width = env->CallIntMethod(box, clzBoundingBox_fnGetWidth);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                if(width == 0) {
                                    width = cvSize.width;
                                }
                                jint color = env->CallIntMethod(box, clzBoundingBox_fnGetColor);
                                if (env->ExceptionCheck()) {
                                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                    env->ExceptionClear();
                                    return 8701;
                                }
                                a = (((int)color) & 0xFF000000) >> 24;
                                b = (((int)color) & 0x00FF0000) >> 16;
                                g = (((int)color) & 0x0000FF00) >> 8;
                                r = (((int)color) & 0x000000FF) >> 0;

                                rectangle(frame, Rect((int)x, (int)y, (int)width, (int)height), Scalar(r, g, b, a),
                                          (int)(std::max(.0018 * (fHeight < fWidth ? fWidth : fHeight), 1.0)));
                            }
                        }

                        dest << frame;
                        currentFrame++;
                    }

                    src.release();
                    dest.release();

                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                } else {
                    return 8701;
                }
            } catch (cv::Exception) {
                return 8701;
            }
        } else {
            return 8701;
        }

    } else {
        return 8701;
    }
    return 0;
}

/*
 * Class:     org_mitre_mpf_videooverlay_BoundingBoxWriter
 * Method:    markupImageNative
 * Signature: ()V
 */
JNIEXPORT int JNICALL Java_org_mitre_mpf_videooverlay_BoundingBoxWriter_markupImageNative
  (JNIEnv *env, jobject boundingBoxWriterInstance, jstring sourceVideoPath, jstring destinationVideoPath)
{
    if (env != NULL) {
        // Get the bounding box map.
        jclass clzBoundingBoxWriter = env->GetObjectClass(boundingBoxWriterInstance);
        jmethodID clzBoundingBoxWriter_fnGetBoundingBoxMap = env->GetMethodID(clzBoundingBoxWriter, "getBoundingBoxMap", "()Lorg/mitre/mpf/videooverlay/BoundingBoxMap;");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jobject boundingBoxMap = env->CallObjectMethod(boundingBoxWriterInstance, clzBoundingBoxWriter_fnGetBoundingBoxMap);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }

        // Get BoundingBoxMap methods.
        jclass clzBoundingBoxMap = env->GetObjectClass(boundingBoxMap);
        jmethodID clzBoundingBoxMap_fnGet = env->GetMethodID(clzBoundingBoxMap, "get", "(Ljava/lang/Object;)Ljava/lang/Object;"); // May be a list.
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBoxMap_fnContainsKey = env->GetMethodID(clzBoundingBoxMap, "containsKey", "(Ljava/lang/Object;)Z");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jint allFramesKey = env->GetStaticIntField(clzBoundingBoxMap, env->GetStaticFieldID(clzBoundingBoxMap, "ALL_FRAMES", "I"));

        // Get List class and methods.
        jclass clzList = env->FindClass("java/util/List");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzList_fnGet = env->GetMethodID(clzList, "get", "(I)Ljava/lang/Object;");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzList_fnSize = env->GetMethodID(clzList, "size", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }

        // Get BoundingBox class and methods.
        jclass clzBoundingBox = env->FindClass("org/mitre/mpf/videooverlay/BoundingBox");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBox_fnGetX = env->GetMethodID(clzBoundingBox, "getX", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBox_fnGetY = env->GetMethodID(clzBoundingBox, "getY", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBox_fnGetHeight = env->GetMethodID(clzBoundingBox, "getHeight", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBox_fnGetWidth = env->GetMethodID(clzBoundingBox, "getWidth", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzBoundingBox_fnGetColor = env->GetMethodID(clzBoundingBox, "getColor", "()I");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }

        jclass clzInteger = env->FindClass("java/lang/Integer");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }
        jmethodID clzInteger_fnValueOf = env->GetStaticMethodID(clzInteger, "valueOf", "(I)Ljava/lang/Integer;");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return 8701;
        }

        // Read in the image...
        const char *inChars = env->GetStringUTFChars(sourceVideoPath, NULL);
        if (inChars != NULL) {

            try {
                VideoCapture src(inChars);
                if (!src.isOpened()) {
                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);

                    return 8700;
                }

                Mat image;
                if (!src.read(image) || image.empty()) {
                    // Cleanup...
                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);

                    return 8700;
                }

                // Get the size of the image.
                Size cvSize = Size(image.cols, image.rows);

                // The output image path.
                const char *outChars = env->GetStringUTFChars(destinationVideoPath, NULL);
                if (outChars != NULL) {

                    // Alpha, red, green, and blue value declarations...
                    int a = 0, r = 0, g = 0, b = 0;

                    // Box 0 into an Integer.
                    jobject boxed = env->CallStaticObjectMethod(clzInteger, clzInteger_fnValueOf, 0);
                    if (env->ExceptionCheck()) {
                        env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                        env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                        env->ExceptionClear();
                        return 8701;
                    }

                    // Get the elements for Frame 0.
                    jboolean success = env->CallBooleanMethod(boundingBoxMap, clzBoundingBoxMap_fnContainsKey, boxed);
                    if (env->ExceptionCheck()) {
                        env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                        env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                        env->ExceptionClear();
                        return 8701;
                    }
                    if(success == JNI_TRUE) {
                        jobject elements = env->CallObjectMethod(boundingBoxMap, clzBoundingBoxMap_fnGet, boxed);
                        if (env->ExceptionCheck()) {
                            env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                            env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                            env->ExceptionClear();
                            return 8701;
                        }

                        // Iterate through this list, drawing the box on the frame.
                        jint size = env->CallIntMethod(elements, clzList_fnSize);
                        if (env->ExceptionCheck()) {
                            env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                            env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                            env->ExceptionClear();
                            return 8701;
                        }
                        for(jint i = 0; i < size; i++) {
                            jobject box = env->CallObjectMethod(elements, clzList_fnGet, i);
                            if (env->ExceptionCheck()) {
                                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                env->ExceptionClear();
                                return 8701;
                            }
                            jint x = env->CallIntMethod(box, clzBoundingBox_fnGetX);
                            if (env->ExceptionCheck()) {
                                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                env->ExceptionClear();
                                return 8701;
                            }
                            jint y = env->CallIntMethod(box, clzBoundingBox_fnGetY);
                            if (env->ExceptionCheck()) {
                                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                env->ExceptionClear();
                                return 8701;
                            }
                            jint height = env->CallIntMethod(box, clzBoundingBox_fnGetHeight);
                            if (env->ExceptionCheck()) {
                                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                env->ExceptionClear();
                                return 8701;
                            }
                            if(height == 0) {
                                height = cvSize.height;
                            }
                            jint width = env->CallIntMethod(box, clzBoundingBox_fnGetWidth);
                            if (env->ExceptionCheck()) {
                                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                env->ExceptionClear();
                                return 8701;
                            }
                            if(width == 0) {
                                width = cvSize.width;
                            }
                            jint color = env->CallIntMethod(box, clzBoundingBox_fnGetColor);
                            if (env->ExceptionCheck()) {
                                env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                                env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                                env->ExceptionClear();
                                return 8701;
                            }

                            a = (((int)color) & 0xFF000000) >> 24;
                            b = (((int)color) & 0x00FF0000) >> 16;
                            g = (((int)color) & 0x0000FF00) >> 8;
                            r = (((int)color) & 0x000000FF) >> 0;

                            rectangle(image, Rect((int)x, (int)y, (int)width, (int)height), Scalar(r, g, b, a),
                                      (int)(std::max(.0018 * (image.rows < image.cols ? image.cols : image.rows), 1.0)));
                        }
                    }

                    std::vector<int> compression_params;
                    compression_params.push_back(cv::IMWRITE_PNG_COMPRESSION);
                    compression_params.push_back(9);

                    if(!imwrite(outChars, image, compression_params)) {
                        env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                        env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                        image.release();
                        return 8710;
                    }

                    image.release();

                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                    env->ReleaseStringUTFChars(destinationVideoPath, outChars);
                } else {
                    env->ReleaseStringUTFChars(sourceVideoPath, inChars);
                    return 8701;
                }
            } catch (cv::Exception) {
                return 8701;
            }
        } else {
            return 8701;
        }
    } else {
          return 8701;
    }
    return 0;
}

#ifdef __cplusplus
}
#endif
#endif
