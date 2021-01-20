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

#ifndef _Included_org_mitre_mpf_videooverlay_BoundingBoxWriter
#define _Included_org_mitre_mpf_videooverlay_BoundingBoxWriter

#include <jni.h>
#include <stdlib.h>
#include <cmath>
#include <array>
#include <opencv2/opencv.hpp>
#include <opencv2/core.hpp>
#include <opencv2/videoio.hpp>
#include <opencv2/imgproc.hpp>

#include <MPFRotatedRect.h>

#include "JniHelper.h"
#include "BoundingBoxImageHandle.h"
#include "BoundingBoxVideoHandle.h"

#ifdef __cplusplus
extern "C" {

using namespace cv;

#endif

void drawBoundingBox(int x, int y, int width, int height, double rotation, bool flip, int red, int green, int blue,
                     bool animated, const std::string &label, Mat *image);

void drawLine(Point2d start, Point2d end, Scalar color, int lineThickness, bool animated, Mat *image);

void drawFrameNumber(int frameNumber, Mat *image);

void drawBoundingBoxLabel(Point2d *pt, Scalar color, int labelIndent, int lineThickness, const std::string &label,
                          Mat *image);


void markup(JNIEnv *env, jobject &boundingBoxWriterInstance, BoundingBoxMediaHandle &boundingBoxMediaHandle)
{
    JniHelper jni(env);
    try {
        // Get the bounding box map.
        jclass clzBoundingBoxWriter = jni.GetObjectClass(boundingBoxWriterInstance);
        jmethodID clzBoundingBoxWriter_fnGetBoundingBoxMap =
            jni.GetMethodID(clzBoundingBoxWriter, "getBoundingBoxMap",
            "()Lorg/mitre/mpf/videooverlay/BoundingBoxMap;");
        jobject boundingBoxMap =
            jni.CallObjectMethod(boundingBoxWriterInstance, clzBoundingBoxWriter_fnGetBoundingBoxMap);

        // Get BoundingBoxMap methods.
        jclass clzBoundingBoxMap = jni.GetObjectClass(boundingBoxMap);
        jmethodID clzBoundingBoxMap_fnGet =
            jni.GetMethodID(clzBoundingBoxMap, "get", "(Ljava/lang/Object;)Ljava/lang/Object;"); // May be a list.
        jmethodID clzBoundingBoxMap_fnContainsKey =
            jni.GetMethodID(clzBoundingBoxMap, "containsKey", "(Ljava/lang/Object;)Z");

        // Get List class and methods.
        jclass clzList = jni.FindClass("java/util/List");
        jmethodID clzList_fnGet = jni.GetMethodID(clzList, "get", "(I)Ljava/lang/Object;");
        jmethodID clzList_fnSize = jni.GetMethodID(clzList, "size", "()I");

        // Get Optional class and methods.
        jclass clzOptional = jni.FindClass("java/util/Optional");
        jmethodID clzOptional_fnIsPresent = jni.GetMethodID(clzOptional, "isPresent", "()Z");
        jmethodID clzOptional_fnGet = jni.GetMethodID(clzOptional, "get", "()Ljava/lang/Object;");

        // Get BoundingBox class and methods.
        jclass clzBoundingBox = jni.FindClass("org/mitre/mpf/videooverlay/BoundingBox");
        jmethodID clzBoundingBox_fnGetX = jni.GetMethodID(clzBoundingBox, "getX", "()I");
        jmethodID clzBoundingBox_fnGetY = jni.GetMethodID(clzBoundingBox, "getY", "()I");
        jmethodID clzBoundingBox_fnGetHeight = jni.GetMethodID(clzBoundingBox, "getHeight", "()I");
        jmethodID clzBoundingBox_fnGetWidth = jni.GetMethodID(clzBoundingBox, "getWidth", "()I");
        jmethodID clzBoundingBox_fnGetRed = jni.GetMethodID(clzBoundingBox, "getRed", "()I");
        jmethodID clzBoundingBox_fnGetGreen = jni.GetMethodID(clzBoundingBox, "getGreen", "()I");
        jmethodID clzBoundingBox_fnGetBlue = jni.GetMethodID(clzBoundingBox, "getBlue", "()I");
        jmethodID clzBoundingBox_fnIsAnimated = jni.GetMethodID(clzBoundingBox, "isAnimated", "()Z");
        jmethodID clzBoundingBox_fnIsExemplar = jni.GetMethodID(clzBoundingBox, "isExemplar", "()Z");
        jmethodID clzBoundingBox_fnGetConfidence = jni.GetMethodID(clzBoundingBox, "getConfidence", "()F");
        jmethodID clzBoundingBox_fnGetClassification =
            jni.GetMethodID(clzBoundingBox, "getClassification", "()Ljava/util/Optional;");

        jmethodID clzBoundingBox_fnGetRotationDegrees = jni.GetMethodID(clzBoundingBox, "getRotationDegrees", "()D");
        jmethodID clzBoundingBox_fnGetFlip = jni.GetMethodID(clzBoundingBox, "getFlip", "()Z");

        jclass clzInteger = jni.FindClass("java/lang/Integer");
        jmethodID clzInteger_fnValueOf = jni.GetStaticMethodID(clzInteger, "valueOf", "(I)Ljava/lang/Integer;");

        Size frameSize = boundingBoxMediaHandle.GetFrameSize();
        Mat frame;

        jint currentFrame = -1;
        while (true) {
            currentFrame++;
            jobject currentFrameBoxed = jni.CallStaticObjectMethod(clzInteger, clzInteger_fnValueOf, currentFrame);

            if (!boundingBoxMediaHandle.Read(frame) || frame.empty()) {
                break;
            }

            jboolean foundEntryForCurrentFrame = jni.CallBooleanMethod(boundingBoxMap,
                                                                       clzBoundingBoxMap_fnContainsKey,
                                                                       currentFrameBoxed);

            if (foundEntryForCurrentFrame) {
                jobject currentFrameElements =
                    jni.CallObjectMethod(boundingBoxMap, clzBoundingBoxMap_fnGet, currentFrameBoxed);

                // Iterate through this list, drawing each box on the frame.
                jint numBoxesCurrentFrame = jni.CallIntMethod(currentFrameElements, clzList_fnSize);

                for (jint i = 0; i < numBoxesCurrentFrame; i++) {
                    jobject box = jni.CallObjectMethod(currentFrameElements, clzList_fnGet, i);
                    jint x = jni.CallIntMethod(box, clzBoundingBox_fnGetX);
                    jint y = jni.CallIntMethod(box, clzBoundingBox_fnGetY);

                    jint height = jni.CallIntMethod(box, clzBoundingBox_fnGetHeight);
                    if (height == 0) {
                        height = frameSize.height;
                    }

                    jint width = jni.CallIntMethod(box, clzBoundingBox_fnGetWidth);
                    if (width == 0) {
                        width = frameSize.width;
                    }

                    jint red = jni.CallIntMethod(box, clzBoundingBox_fnGetRed);
                    jint green = jni.CallIntMethod(box, clzBoundingBox_fnGetGreen);
                    jint blue = jni.CallIntMethod(box, clzBoundingBox_fnGetBlue);
                    jboolean animated = jni.CallBooleanMethod(box, clzBoundingBox_fnIsAnimated);
                    jdouble rotation = jni.CallDoubleMethod(box, clzBoundingBox_fnGetRotationDegrees);
                    jboolean flip = jni.CallBooleanMethod(box, clzBoundingBox_fnGetFlip);
                    jfloat confidence = jni.CallFloatMethod(box, clzBoundingBox_fnGetConfidence);

                    std::stringstream ss;

                    jobject classificationObj = jni.CallObjectMethod(box, clzBoundingBox_fnGetClassification);
                    if (jni.CallBooleanMethod(classificationObj, clzOptional_fnIsPresent)) {
                        std::string classification =
                            jni.ToStdString((jstring)jni.CallObjectMethod(classificationObj, clzOptional_fnGet));
                        ss << classification.substr(0, 10); // truncate long strings
                        if (classification.length() > 10) {
                            ss << "...";
                        }
                        ss << ' ';
                    }

                    ss << std::fixed << std::setprecision(3) << confidence;

                    if (boundingBoxMediaHandle.MarkExemplar() &&
                            jni.CallBooleanMethod(box, clzBoundingBox_fnIsExemplar)) {
                        ss << '!';
                    }

                    drawBoundingBox(x, y, width, height, rotation, flip, red, green, blue, animated, ss.str(), &frame);
                }
            }

            if (boundingBoxMediaHandle.ShowFrameNumbers()) {
                drawFrameNumber(currentFrame, &frame);
            }

            boundingBoxMediaHandle.HandleMarkedFrame(frame);
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
 * Method:    markupVideoNative
 */
JNIEXPORT void JNICALL Java_org_mitre_mpf_videooverlay_BoundingBoxWriter_markupVideoNative
  (JNIEnv *env, jobject boundingBoxWriterInstance, jstring sourceVideoPathJString, jstring destinationVideoPathJString)
{
    JniHelper jni(env);
    try {
        std::string sourceVideoPath = jni.ToStdString(sourceVideoPathJString);
        std::string destinationVideoPath = jni.ToStdString(destinationVideoPathJString);

        BoundingBoxVideoHandle boundingBoxVideoHandle(sourceVideoPath, destinationVideoPath);

        markup(env, boundingBoxWriterInstance, boundingBoxVideoHandle);
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
 */
JNIEXPORT void JNICALL Java_org_mitre_mpf_videooverlay_BoundingBoxWriter_markupImageNative
  (JNIEnv *env, jobject boundingBoxWriterInstance, jstring sourceImagePathJString, jstring destinationImagePathJString)
{
    JniHelper jni(env);
    try {
        std::string sourceImagePath = jni.ToStdString(sourceImagePathJString);
        std::string destinationImagePath = jni.ToStdString(destinationImagePathJString);

        BoundingBoxImageHandle boundingBoxImageHandle(sourceImagePath, destinationImagePath);

        markup(env, boundingBoxWriterInstance, boundingBoxImageHandle);
    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (...) {
        jni.ReportCppException();
    }
}

void drawBoundingBox(int x, int y, int width, int height, double rotation, bool flip, int red, int green, int blue,
                     bool animated, const std::string &label, Mat *image) {
    std::array<Point2d, 4> corners = MPF::COMPONENT::MPFRotatedRect(x, y, width, height, rotation, flip).GetCorners();

    Scalar boxColor(blue, green, red);
    int minDim = width < height ? width : height;

    // Because we use LINE_AA below for anti-aliasing, which uses a Gaussian filter, the lack of pixels near the edge
    // of the frame causes a problem when attempting to draw a line along the edge using a thickness of 1.
    // Specifically, no pixels will be drawn near the edge.
    // Refer to: https://stackoverflow.com/questions/42484955/pixels-at-arrow-tip-missing-when-using-antialiasing
    // To address this, we use a minimum thickness of 2.
    int lineThickness = (int) std::max(.0018 * (image->rows < image->cols ? image->cols : image->rows), 2.0);

    int minCircleRadius = 3;
    int circleRadius = lineThickness == 1 ? minCircleRadius : lineThickness + 5;

    double maxCircleCoverage = minDim * 0.25; // circle should not cover more than 25% of the min dim
    if (circleRadius > maxCircleCoverage) {
        circleRadius = std::max((int)maxCircleCoverage, minCircleRadius);
    }

    // Get top-left point.
    auto topLeftPt = std::max_element(corners.begin(), corners.end(), [](Point const& a, Point const& b) {
        if (a.y == b.y) {
            return a.x > b.x; // leftmost
        }
        return a.y > b.y; // topmost
    });

    int labelIndent = circleRadius + 2;
    drawBoundingBoxLabel(topLeftPt, boxColor, labelIndent, lineThickness, label, image);

    drawLine(corners[0], corners[1], boxColor, lineThickness, animated, image);
    drawLine(corners[1], corners[2], boxColor, lineThickness, animated, image);
    drawLine(corners[2], corners[3], boxColor, lineThickness, animated, image);
    drawLine(corners[3], corners[0], boxColor, lineThickness, animated, image);

    circle(*image, Point(x, y), circleRadius, boxColor, cv::LineTypes::FILLED, cv::LineTypes::LINE_AA);
}

void drawLine(Point2d start, Point2d end, Scalar color, int lineThickness, bool animated, Mat *image) {
    if (!animated) {
        line(*image, start, end, color, lineThickness, cv::LineTypes::LINE_AA);
        return;
    }

    // Draw dashed line.
    double lineLen = pow(pow(start.x - end.x, 2) + pow(start.y - end.y, 2), .5);

    int dashLen = 10 + lineThickness;
    double maxDashCoverage = lineLen * 0.5; // dash should not occupy more than 50% of the line length
    if (dashLen > maxDashCoverage) {
        dashLen = (int)maxDashCoverage;
    }

    double step = dashLen / lineLen;
    Point prev = start;
    double percent = 0.0;
    bool draw = true;

    do {
        percent = std::min(percent + step, 1.0);
        int x = (start.x * (1 - percent) + end.x * percent) + 0.5;
        int y = (start.y * (1 - percent) + end.y * percent) + 0.5;
        Point curr(x, y);
        if (draw) {
            line(*image, prev, curr, color, lineThickness);
        }
        prev = curr;
        draw = !draw;
    } while (percent < 1.0);
}

void drawFrameNumber(int frameNumber, Mat *image) {
    std::string label = std::to_string(frameNumber);

    int labelPadding = 8;
    double labelScale = 0.8;
    int labelThickness = 2;
    int labelFont = cv::FONT_HERSHEY_SIMPLEX;

    int baseline = 0;
    Size labelSize = getTextSize(label, labelFont, labelScale, labelThickness, &baseline);

    // position frame number near bottom right of the frame
    int labelRectBottomRightX = image->cols - 10;
    int labelRectBottomRightY = image->rows - 10;

    int labelRectTopLeftX = labelRectBottomRightX - labelSize.width  - (2 * labelPadding);
    int labelRectTopLeftY = labelRectBottomRightY - labelSize.height - (2 * labelPadding);

    rectangle(*image, Point(labelRectTopLeftX, labelRectTopLeftY), Point(labelRectBottomRightX, labelRectBottomRightY),
        Scalar(0, 0, 0), cv::LineTypes::FILLED, cv::LineTypes::LINE_AA);

    int labelBottomLeftX = labelRectTopLeftX + labelPadding;
    int labelBottomLeftY = labelRectTopLeftY + labelSize.height + labelPadding;

    cv::putText(*image, label, Point(labelBottomLeftX, labelBottomLeftY), labelFont, labelScale, Scalar(255,255,255),
        labelThickness, cv::LineTypes::LINE_8);
}

void drawBoundingBoxLabel(Point2d *pt, Scalar color, int labelIndent, int lineThickness, const std::string &label,
                          Mat *image) {
    int labelPadding = 8;
    double labelScale = 0.8;
    int labelThickness = 2;
    int labelFont = cv::FONT_HERSHEY_SIMPLEX;

    int baseline = 0;
    Size labelSize = getTextSize(label, labelFont, labelScale, labelThickness, &baseline);

    int labelRectBottomLeftX = pt->x;
    int labelRectBottomLeftY = pt->y - lineThickness;
    int labelRectTopRightX = pt->x + labelIndent + labelSize.width + labelPadding;
    int labelRectTopRightY = pt->y - labelSize.height - (2 * labelPadding) - lineThickness;

    rectangle(*image, Point(labelRectBottomLeftX, labelRectBottomLeftY), Point(labelRectTopRightX, labelRectTopRightY),
        Scalar(0, 0, 0), cv::LineTypes::FILLED, cv::LineTypes::LINE_AA);

    int labelBottomLeftX = pt->x + labelIndent;
    int labelBottomLeftY = pt->y - labelPadding - (0.5 * lineThickness) - 2;

    cv::putText(*image, label, Point(labelBottomLeftX, labelBottomLeftY), labelFont, labelScale, color,
        labelThickness, cv::LineTypes::LINE_8);
}

#ifdef __cplusplus
}
#endif
#endif

