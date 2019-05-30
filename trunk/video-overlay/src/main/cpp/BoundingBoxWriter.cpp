/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

#ifndef _Included_org_mitre_mpf_videooverlay_BoundingBoxWriter
#define _Included_org_mitre_mpf_videooverlay_BoundingBoxWriter

#include <jni.h>
#include <stdlib.h>
#include <cmath>
#include <opencv2/opencv.hpp>
#include <opencv2/core.hpp>
#include <opencv2/videoio.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>

#include "JniHelper.h"



#ifdef __cplusplus
extern "C" {

using namespace cv;

#endif

void drawBoundingBox(int x, int y, int width, int height, double rotation, int red, int green, int blue, Mat *image);

/*
 * Class:     org_mitre_mpf_videooverlay_BoundingBoxWriter
 * Method:    markupVideoNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_mitre_mpf_videooverlay_BoundingBoxWriter_markupVideoNative
  (JNIEnv *env, jobject boundingBoxWriterInstance, jstring sourceVideoPathJString, jstring destinationVideoPathJString)
{
    JniHelper jni(env);
    try {
        // Get the bounding box map.

        jclass clzBoundingBoxWriter = jni.GetObjectClass(boundingBoxWriterInstance);
        jmethodID clzBoundingBoxWriter_fnGetBoundingBoxMap
                = jni.GetMethodID(clzBoundingBoxWriter, "getBoundingBoxMap",
                                  "()Lorg/mitre/mpf/videooverlay/BoundingBoxMap;");
        jobject boundingBoxMap = jni.CallObjectMethod(boundingBoxWriterInstance, clzBoundingBoxWriter_fnGetBoundingBoxMap);

        // Get BoundingBoxMap methods.
        jclass clzBoundingBoxMap = jni.GetObjectClass(boundingBoxMap);
        jmethodID clzBoundingBoxMap_fnGet
                = jni.GetMethodID(clzBoundingBoxMap, "get", "(Ljava/lang/Object;)Ljava/lang/Object;"); // May be a list.
        jmethodID clzBoundingBoxMap_fnContainsKey = jni.GetMethodID(clzBoundingBoxMap, "containsKey",
                                                                    "(Ljava/lang/Object;)Z");

        jint allFramesKey = jni.GetStaticIntField(clzBoundingBoxMap,
                                                  jni.GetStaticFieldID(clzBoundingBoxMap, "ALL_FRAMES", "I"));

        // Get List class and methods.
        jclass clzList = jni.FindClass("java/util/List");
        jmethodID clzList_fnGet = jni.GetMethodID(clzList, "get", "(I)Ljava/lang/Object;");
        jmethodID clzList_fnSize = jni.GetMethodID(clzList, "size", "()I");

        // Get BoundingBox class and methods.
        jclass clzBoundingBox = jni.FindClass("org/mitre/mpf/videooverlay/BoundingBox");
        jmethodID clzBoundingBox_fnGetX = jni.GetMethodID(clzBoundingBox, "getX", "()I");
        jmethodID clzBoundingBox_fnGetY = jni.GetMethodID(clzBoundingBox, "getY", "()I");
        jmethodID clzBoundingBox_fnGetHeight = jni.GetMethodID(clzBoundingBox, "getHeight", "()I");
        jmethodID clzBoundingBox_fnGetWidth = jni.GetMethodID(clzBoundingBox, "getWidth", "()I");
        jmethodID clzBoundingBox_fnGetRed = jni.GetMethodID(clzBoundingBox, "getRed", "()I");
        jmethodID clzBoundingBox_fnGetGreen = jni.GetMethodID(clzBoundingBox, "getGreen", "()I");
        jmethodID clzBoundingBox_fnGetBlue = jni.GetMethodID(clzBoundingBox, "getBlue", "()I");

        jmethodID clzBoundingBox_fnGetRotationDegrees = jni.GetMethodID(clzBoundingBox, "getRotationDegrees", "()D");

        jclass clzInteger = jni.FindClass("java/lang/Integer");
        jmethodID clzInteger_fnValueOf = jni.GetStaticMethodID(clzInteger, "valueOf", "(I)Ljava/lang/Integer;");

        // Set up the videos...
        std::string sourceVideoPath = jni.ToStdString(sourceVideoPathJString);
        VideoCapture src(sourceVideoPath);
        if(!src.isOpened()) {
            throw std::runtime_error("Unable to open source video: " + sourceVideoPath);
        }

        Size cvSize = Size(static_cast<int>(src.get(cv::CAP_PROP_FRAME_WIDTH)),
                           static_cast<int>(src.get(cv::CAP_PROP_FRAME_HEIGHT)));
        double fps = src.get(cv::CAP_PROP_FPS);

        std::string destinationVideoPath = jni.ToStdString(destinationVideoPathJString);
        VideoWriter dest(destinationVideoPath, VideoWriter::fourcc('M','J','P','G'), fps, cvSize, true);
        if (!dest.isOpened()) { // Cleanup...
            throw std::runtime_error("Unable to open destination video: " + sourceVideoPath);
        }

        Mat frame;

        jint currentFrame = -1;
        while (true) {
            currentFrame++;
            jobject currentFrameBoxed = jni.CallStaticObjectMethod(clzInteger, clzInteger_fnValueOf, currentFrame);

            // Get the next frame.
            src >> frame;

            // if that frame is empty, we've reached the end of the video.
            if(frame.empty()) { break; }

            // Otherwise, get the list of boxes that need to be drawn on this frame. This is the union of the boxes
            // found in keys ALL_FRAMES and currentFrame.
            jboolean foundEntryForCurrentFrame = jni.CallBooleanMethod(boundingBoxMap,
                                                                       clzBoundingBoxMap_fnContainsKey,
                                                                       currentFrameBoxed);
            if (foundEntryForCurrentFrame) {
                // Map has elements which are to be drawn on every frame.
                jobject allFramesElements = jni.CallObjectMethod(boundingBoxMap, clzBoundingBoxMap_fnGet,
                                                                 currentFrameBoxed);

                // Iterate through this list, drawing the box on the frame.
                jint numBoxesCurrentFrame = jni.CallIntMethod(allFramesElements, clzList_fnSize);

                for (jint i = 0; i < numBoxesCurrentFrame; i++) {
                    jobject box = jni.CallObjectMethod(allFramesElements, clzList_fnGet, i);
                    jint x = jni.CallIntMethod(box, clzBoundingBox_fnGetX);
                    jint y = jni.CallIntMethod(box, clzBoundingBox_fnGetY);

                    jint height = jni.CallIntMethod(box, clzBoundingBox_fnGetHeight);
                    if (height == 0) {
                        height = cvSize.height;
                    }

                    jint width = jni.CallIntMethod(box, clzBoundingBox_fnGetWidth);
                    if (width == 0) {
                        width = cvSize.width;
                    }

                    jint red = jni.CallIntMethod(box, clzBoundingBox_fnGetRed);
                    jint green = jni.CallIntMethod(box, clzBoundingBox_fnGetGreen);
                    jint blue = jni.CallIntMethod(box, clzBoundingBox_fnGetBlue);
                    jdouble rotation = jni.CallDoubleMethod(box, clzBoundingBox_fnGetRotationDegrees);

                    drawBoundingBox(x, y, width, height, rotation, red, green, blue, &frame);
                }
            }

            jobject boxedAllFramesKey = jni.CallStaticObjectMethod(clzInteger, clzInteger_fnValueOf, allFramesKey);

            jboolean foundAllFramesKey = jni.CallBooleanMethod(boundingBoxMap, clzBoundingBoxMap_fnContainsKey,
                                                               boxedAllFramesKey);
            if (foundAllFramesKey) {
                // Map has elements which are to be drawn on every frame.
                jobject allFramesElements = jni.CallObjectMethod(boundingBoxMap, clzBoundingBoxMap_fnGet,
                                                                 boxedAllFramesKey);

                // Iterate through this list, drawing the box on the frame.
                jint numBoxesAllFrames = jni.CallIntMethod(allFramesElements, clzList_fnSize);

                for (jint i = 0; i < numBoxesAllFrames; i++) {
                    jobject box = jni.CallObjectMethod(allFramesElements, clzList_fnGet, i);
                    jint x = jni.CallIntMethod(box, clzBoundingBox_fnGetX);
                    jint y = jni.CallIntMethod(box, clzBoundingBox_fnGetY);

                    jint height = jni.CallIntMethod(box, clzBoundingBox_fnGetHeight);
                    if (height == 0) {
                        height = cvSize.height;
                    }
                    jint width = jni.CallIntMethod(box, clzBoundingBox_fnGetWidth);
                    if (width == 0) {
                        width = cvSize.width;
                    }

                    jint red = jni.CallIntMethod(box, clzBoundingBox_fnGetRed);
                    jint green = jni.CallIntMethod(box, clzBoundingBox_fnGetGreen);
                    jint blue = jni.CallIntMethod(box, clzBoundingBox_fnGetBlue);
                    jdouble rotation = jni.CallDoubleMethod(box, clzBoundingBox_fnGetRotationDegrees);

                    drawBoundingBox(x, y, width, height, rotation, red, green, blue, &frame);
                }
            }

            dest << frame;
        }

    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (...) {
        jni.ReportCppException();
    }
}

/*
 * Class:     org_mitre_mpf_videooverlay_BoundingBoxWriter
 * Method:    markupImageNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_mitre_mpf_videooverlay_BoundingBoxWriter_markupImageNative
  (JNIEnv *env, jobject boundingBoxWriterInstance, jstring sourceImagePathJString, jstring destinationImagePathJString)
{
    JniHelper jni(env);
    try {
        // Get the bounding box map.
        jclass clzBoundingBoxWriter = jni.GetObjectClass(boundingBoxWriterInstance);
        jmethodID clzBoundingBoxWriter_fnGetBoundingBoxMap
                = jni.GetMethodID(clzBoundingBoxWriter, "getBoundingBoxMap",
                                  "()Lorg/mitre/mpf/videooverlay/BoundingBoxMap;");

        jobject boundingBoxMap = jni.CallObjectMethod(boundingBoxWriterInstance,
                                                      clzBoundingBoxWriter_fnGetBoundingBoxMap);

        // Get BoundingBoxMap methods.
        jclass clzBoundingBoxMap = jni.GetObjectClass(boundingBoxMap);
        jmethodID clzBoundingBoxMap_fnGet = jni.GetMethodID(clzBoundingBoxMap, "get",
                                                            "(Ljava/lang/Object;)Ljava/lang/Object;"); // May be a list.

        jmethodID clzBoundingBoxMap_fnContainsKey = jni.GetMethodID(clzBoundingBoxMap, "containsKey",
                                                                    "(Ljava/lang/Object;)Z");

        // Get List class and methods.
        jclass clzList = jni.FindClass("java/util/List");
        jmethodID clzList_fnGet = jni.GetMethodID(clzList, "get", "(I)Ljava/lang/Object;");
        jmethodID clzList_fnSize = jni.GetMethodID(clzList, "size", "()I");

        // Get BoundingBox class and methods.
        jclass clzBoundingBox = jni.FindClass("org/mitre/mpf/videooverlay/BoundingBox");
        jmethodID clzBoundingBox_fnGetX = jni.GetMethodID(clzBoundingBox, "getX", "()I");
        jmethodID clzBoundingBox_fnGetY = jni.GetMethodID(clzBoundingBox, "getY", "()I");
        jmethodID clzBoundingBox_fnGetHeight = jni.GetMethodID(clzBoundingBox, "getHeight", "()I");
        jmethodID clzBoundingBox_fnGetWidth = jni.GetMethodID(clzBoundingBox, "getWidth", "()I");
        jmethodID clzBoundingBox_fnGetRed = jni.GetMethodID(clzBoundingBox, "getRed", "()I");
        jmethodID clzBoundingBox_fnGetGreen = jni.GetMethodID(clzBoundingBox, "getGreen", "()I");
        jmethodID clzBoundingBox_fnGetBlue = jni.GetMethodID(clzBoundingBox, "getBlue", "()I");

        jmethodID clzBoundingBox_fnGetRotationDegrees = jni.GetMethodID(clzBoundingBox, "getRotationDegrees", "()D");

        jclass clzInteger = jni.FindClass("java/lang/Integer");
        jmethodID clzInteger_fnValueOf = jni.GetStaticMethodID(clzInteger, "valueOf", "(I)Ljava/lang/Integer;");

        // Read in the image...
        std::string sourceImagePath = jni.ToStdString(sourceImagePathJString);
        VideoCapture src(sourceImagePath);
        if (!src.isOpened()) {
            throw std::runtime_error("Unable to open source image: " + sourceImagePath);
        }

        Mat image;
        if (!src.read(image) || image.empty()) {
            throw std::runtime_error("Unable to read source image: " + sourceImagePath);
        }

        // Get the size of the image.
        Size cvSize = Size(image.cols, image.rows);


        // Box 0 into an Integer.
        jobject boxedZero = jni.CallStaticObjectMethod(clzInteger, clzInteger_fnValueOf, 0);

        // Get the elements for Frame 0.
        jboolean foundEntry = jni.CallBooleanMethod(boundingBoxMap, clzBoundingBoxMap_fnContainsKey, boxedZero);
        if (foundEntry) {
            jobject elements = jni.CallObjectMethod(boundingBoxMap, clzBoundingBoxMap_fnGet, boxedZero);

            // Iterate through this list, drawing the box on the frame.
            jint size = jni.CallIntMethod(elements, clzList_fnSize);

            for (jint i = 0; i < size; i++) {
                jobject box = jni.CallObjectMethod(elements, clzList_fnGet, i);
                jint x = jni.CallIntMethod(box, clzBoundingBox_fnGetX);
                jint y = jni.CallIntMethod(box, clzBoundingBox_fnGetY);

                jint height = jni.CallIntMethod(box, clzBoundingBox_fnGetHeight);
                if (height == 0) {
                    height = cvSize.height;
                }
                jint width = jni.CallIntMethod(box, clzBoundingBox_fnGetWidth);
                if (width == 0) {
                    width = cvSize.width;
                }

                jint red = jni.CallIntMethod(box, clzBoundingBox_fnGetRed);
                jint green = jni.CallIntMethod(box, clzBoundingBox_fnGetGreen);
                jint blue = jni.CallIntMethod(box, clzBoundingBox_fnGetBlue);
                jdouble rotation = jni.CallDoubleMethod(box, clzBoundingBox_fnGetRotationDegrees);

                drawBoundingBox(x, y, width, height, rotation, red, green, blue, &image);
            }
        }

        std::string destinationImagePath = jni.ToStdString(destinationImagePathJString);
        if (!imwrite(destinationImagePath, image, { cv::IMWRITE_PNG_COMPRESSION, 9 })) {
            throw std::runtime_error("Failed to write image: " + destinationImagePath);
        }
    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (...) {
        jni.ReportCppException();
    }
}


void getCorners(int x, int y, int width, int height, double rotation, Point2f *corners) {
    corners[0] = cv::Point2f(x, y);

    double radians = rotation * M_PI / 180.0;
    double sinVal = std::sin(radians);
    double cosVal = std::cos(radians);

    double corner2X = x + width * cosVal;
    double corner2Y = y - width * sinVal;
    corners[1] = cv::Point2f(corner2X, corner2Y);

    double corner3X = corner2X + height * sinVal;
    double corner3Y = corner2Y + height * cosVal;
    corners[2] = cv::Point2f(corner3X, corner3Y);

    double corner4X = x + height * sinVal;
    double corner4Y = y + height * cosVal;
    corners[3] = cv::Point2f(corner4X, corner4Y);
}


void drawBoundingBox(int x, int y, int width, int height, double rotation, int red, int green, int blue, Mat *image) {
    Point2f corners[4];
    getCorners(x, y, width, height, rotation, corners);


    Scalar lineColor(blue, green, red);
    int thickness = (int) std::max(.0018 * (image->rows < image->cols ? image->cols : image->rows), 1.0);

    line(*image, corners[0], corners[1], lineColor, thickness, cv::LineTypes::LINE_AA);
    line(*image, corners[1], corners[2], lineColor, thickness, cv::LineTypes::LINE_AA);
    line(*image, corners[2], corners[3], lineColor, thickness, cv::LineTypes::LINE_AA);
    line(*image, corners[3], corners[0], lineColor, thickness, cv::LineTypes::LINE_AA);

    int radius = thickness == 1 ? 3 : thickness + 5;
    int filledInCircleCode = -1;
    circle(*image, Point(x, y), radius, lineColor, filledInCircleCode, cv::LineTypes::LINE_AA);
}


#ifdef __cplusplus
}
#endif
#endif

