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

#include <jni.h>
#include <stdlib.h>
#include <cmath>
#include <array>
#include <opencv2/opencv.hpp>
#include <opencv2/core.hpp>
#include <opencv2/videoio.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp> // DEBUG

#include <MPFRotatedRect.h>
#include <frame_transformers/NoOpFrameTransformer.h>
#include <frame_transformers/IFrameTransformer.h>
#include <frame_transformers/AffineFrameTransformer.h>

#include "detectionComponentUtils.h"
#include "JniHelper.h"
#include "BoundingBoxImageHandle.h"
#include "BoundingBoxVideoHandle.h"


using namespace cv;
using namespace MPF;
using namespace COMPONENT;

template<typename TMediaHandle>
void markup(JNIEnv *env, jobject &boundingBoxWriterInstance, jobject mediaMetadata, jobject requestProperties,
            TMediaHandle &boundingBoxMediaHandle);

bool jniGetBoolProperty(JniHelper jni, std::string key, jobject map, jmethodID methodId);

void drawBoundingBox(int x, int y, int width, int height, double boxRotation, bool boxFlip,
                     double mediaRotation, bool mediaFlip, int red, int green, int blue, bool animated,
                     const std::string &label, bool labelChooseSide, Mat *image);

void drawLine(Point2d start, Point2d end, Scalar color, int lineThickness, bool animated, Mat *image);

void drawFrameNumber(int frameNumber, Mat *image);

void drawBoundingBoxLabel(Point2d pt, double rotation, bool flip, Scalar color, int labelIndent, bool labelOnLeft,
                          int lineThickness, const std::string &label, Mat *image);


extern "C" {

JNIEXPORT void JNICALL Java_org_mitre_mpf_videooverlay_BoundingBoxWriter_markupVideoNative
  (JNIEnv *env, jobject boundingBoxWriterInstance, jstring sourceVideoPathJString, jobject mediaMetadata,
   jstring destinationVideoPathJString, jobject requestProperties)
{
    JniHelper jni(env);
    try {
        std::string sourceVideoPath = jni.ToStdString(sourceVideoPathJString);
        std::string destinationVideoPath = jni.ToStdString(destinationVideoPathJString);

        BoundingBoxVideoHandle boundingBoxVideoHandle(sourceVideoPath, destinationVideoPath);

        markup(env, boundingBoxWriterInstance, mediaMetadata, requestProperties, boundingBoxVideoHandle);
    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (...) {
        jni.ReportCppException();
    }
}

JNIEXPORT void JNICALL Java_org_mitre_mpf_videooverlay_BoundingBoxWriter_markupImageNative
  (JNIEnv *env, jobject boundingBoxWriterInstance, jstring sourceImagePathJString, jobject mediaMetadata,
   jstring destinationImagePathJString, jobject requestProperties)
{
    JniHelper jni(env);
    try {
        std::string sourceImagePath = jni.ToStdString(sourceImagePathJString);
        std::string destinationImagePath = jni.ToStdString(destinationImagePathJString);

        BoundingBoxImageHandle boundingBoxImageHandle(sourceImagePath, destinationImagePath);

        markup(env, boundingBoxWriterInstance, mediaMetadata, requestProperties, boundingBoxImageHandle);
    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (...) {
        jni.ReportCppException();
    }
}

} // extern "C"


template<typename TMediaHandle>
void markup(JNIEnv *env, jobject &boundingBoxWriterInstance, jobject mediaMetadata, jobject requestProperties,
            TMediaHandle &boundingBoxMediaHandle)
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

        // Get the Integer class and methods.
        jclass clzInteger = jni.FindClass("java/lang/Integer");
        jmethodID clzInteger_fnValueOf = jni.GetStaticMethodID(clzInteger, "valueOf", "(I)Ljava/lang/Integer;");

        // Get List class and methods.
        jclass clzList = jni.FindClass("java/util/List");
        jmethodID clzList_fnGet = jni.GetMethodID(clzList, "get", "(I)Ljava/lang/Object;");
        jmethodID clzList_fnSize = jni.GetMethodID(clzList, "size", "()I");

        // Get the Map class and methods.
        jclass clzMap = jni.FindClass("java/util/Map");
        jmethodID clzMap_fnGet = jni.GetMethodID(clzMap, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");

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

        // Get the media metadata rotation property.
        auto jPropKey = jni.ToJString("ROTATION");
        jstring jPropValue = (jstring) jni.CallObjectMethod(mediaMetadata, clzMap_fnGet, *jPropKey);
        double mediaRotation = 0.0;
        if (jPropValue != nullptr) {
            std::string rotationPropValue = jni.ToStdString(jPropValue);
            mediaRotation = std::stod(rotationPropValue);
        }

        // Get the media metadata horizontal flip property.
        bool mediaFlip = jniGetBoolProperty(jni, "HORIZONTAL_FLIP", mediaMetadata, clzMap_fnGet);

        // Get request properties.
        bool labelsEnabled = jniGetBoolProperty(jni, "MARKUP_LABELS_ENABLED", requestProperties, clzMap_fnGet);
        bool labelsChooseSideEnabled = jniGetBoolProperty(jni, "MARKUP_LABELS_CHOOSE_SIDE_ENABLED",
                                                          requestProperties, clzMap_fnGet);
        bool borderEnabled = jniGetBoolProperty(jni, "MARKUP_BORDER_ENABLED", requestProperties, clzMap_fnGet);
        bool exemplarEnabledEnabled = jniGetBoolProperty(jni, "MARKUP_VIDEO_EXEMPLAR_ENABLED",
                                                         requestProperties, clzMap_fnGet);
        bool frameNumberEnabled = jniGetBoolProperty(jni, "MARKUP_VIDEO_FRAME_NUMBER_ENABLED",
                                                     requestProperties, clzMap_fnGet);

        Size origFrameSize = boundingBoxMediaHandle.GetFrameSize();
        Mat frame;

        // Provide enough room for long labels with wide characters to extend off the edges of the image.
        int framePadding = 400;

        jint currentFrame = -1;
        while (true) {
            currentFrame++;
            jobject currentFrameBoxed = jni.CallStaticObjectMethod(clzInteger, clzInteger_fnValueOf, currentFrame);

            if (!boundingBoxMediaHandle.Read(frame) || frame.empty()) {
                break;
            }

            // Add a black border to allow boxes and labels to extend off the edges of the image.  
            cv::copyMakeBorder(frame, frame, framePadding, framePadding, framePadding, framePadding,
                               cv::BORDER_CONSTANT, Scalar(0, 0, 0));

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
                        height = origFrameSize.height;
                    }

                    jint width = jni.CallIntMethod(box, clzBoundingBox_fnGetWidth);
                    if (width == 0) {
                        width = origFrameSize.width;
                    }

                    jint red = jni.CallIntMethod(box, clzBoundingBox_fnGetRed);
                    jint green = jni.CallIntMethod(box, clzBoundingBox_fnGetGreen);
                    jint blue = jni.CallIntMethod(box, clzBoundingBox_fnGetBlue);
                    jboolean animated = jni.CallBooleanMethod(box, clzBoundingBox_fnIsAnimated);
                    jfloat confidence = jni.CallFloatMethod(box, clzBoundingBox_fnGetConfidence);

                    double boxRotation = (double)jni.CallDoubleMethod(box, clzBoundingBox_fnGetRotationDegrees);
                    bool boxFlip = (bool)jni.CallBooleanMethod(box, clzBoundingBox_fnGetFlip);

                    std::stringstream ss;

                    if (labelsEnabled) {
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

                        if (exemplarEnabledEnabled && boundingBoxMediaHandle.MarkExemplar() &&
                                jni.CallBooleanMethod(box, clzBoundingBox_fnIsExemplar)) {
                            ss << '!';
                        }
                    }

                    drawBoundingBox(x + framePadding, y + framePadding, width, height, boxRotation, boxFlip,
                                    mediaRotation, mediaFlip, red, green, blue, animated, ss.str(),
                                    labelsChooseSideEnabled, &frame);
                }
            }

            // Generate the final frame by flipping and/or rotating the raw frame to account for media metadata.
            AffineFrameTransformer frameTransformer(
                    mediaRotation, mediaFlip, Scalar(0, 0, 0),
                    IFrameTransformer::Ptr(new NoOpFrameTransformer(frame.size())));

            Mat transformFrame = frame.clone();
            frameTransformer.TransformFrame(transformFrame, 0);

            // Crop the padding off if we're not keeping the border.
            if (!borderEnabled) {
                transformFrame = transformFrame(cv::Rect(framePadding, framePadding,
                                                         origFrameSize.width, origFrameSize.height));
            }

            if (frameNumberEnabled && boundingBoxMediaHandle.ShowFrameNumbers()) {
                drawFrameNumber(currentFrame, &transformFrame);
            }

            boundingBoxMediaHandle.HandleMarkedFrame(transformFrame);
        }

    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (...) {
        jni.ReportCppException();
    }
}


bool jniGetBoolProperty(JniHelper jni, std::string key, jobject map, jmethodID methodId) {
    auto jPropKey = jni.ToJString(key);
    auto jPropValue = (jstring) jni.CallObjectMethod(map, methodId, *jPropKey);
    bool value = false;
    if (jPropValue != nullptr) {
        value = jni.ToBool(jPropValue);
    }
    return value;
}


void drawBoundingBox(int x, int y, int width, int height, double boxRotation, bool boxFlip,
                     double mediaRotation, bool mediaFlip, int red, int green, int blue, bool animated,
                     const std::string &label, bool labelChooseSide, Mat *image)
{
    // Calculate the box coordinates relative to the raw frame.
    // The frame is "raw" in the sense that it's not flipped and/or rotated to account for media metadata.
    std::array<Point2d, 4> corners = MPFRotatedRect(x, y, width, height, boxRotation, boxFlip).GetCorners();
    auto detectionTopLeftPt = corners[0];

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

    double maxCircleCoverage = minDim * 0.25; // circle should not cover more than 25% of the minimum dimension
    if (circleRadius > maxCircleCoverage) {
        circleRadius = std::max((int)maxCircleCoverage, minCircleRadius);
    }

    if (!label.empty()) {
        // Calculate the adjusted box coordinates relative to the final frame.
        // The frame is "final" in the sense that it's flipped and/or rotated to account for media metadata.
        MPFRotatedRect adjRotatedRect(x, y, width, height,
                                      boxFlip ? boxRotation + mediaRotation : boxRotation - mediaRotation,
                                      boxFlip ^ mediaFlip);
        std::array<Point2d, 4> adjCorners = adjRotatedRect.GetCorners();

        // Get the top point of box in final frame. The lower-left corner of the black label rectangle will later be
        // positioned here (see drawBoundingBoxLabel()), ensuring that the label will never appear within the detection box.
        auto adjTopPtIter = std::min_element(adjCorners.begin(), adjCorners.end(), [](Point const& a, Point const& b) {
            return std::tie(a.y, a.x) < std::tie(b.y, b.x); // left takes precedence over right
        });
        int adjTopPtIndex = std::distance(adjCorners.begin(), adjTopPtIter);
        Point2d adjTopPt = adjCorners[adjTopPtIndex];

        // Get point of box in raw frame that corresponds to the top point in the box in the final frame.
        Point2d rawTopPt = corners[adjTopPtIndex];

        bool labelOnLeft = false;
        if (labelChooseSide) {
            // Determine if the label should be on the left or right side of the top point.
            // Our goal is to prevent the label from extending past the leftmost or rightmost point, if possible.
            Rect2d adjRect = adjRotatedRect.GetBoundingRect();
            Point2d adjRectCenter = (adjRect.br() + adjRect.tl()) * 0.5;
            labelOnLeft = (adjTopPt.x > adjRectCenter.x);
        }

        int labelIndent = circleRadius + 2;
        drawBoundingBoxLabel(rawTopPt, mediaRotation, mediaFlip, boxColor, labelIndent, labelOnLeft, lineThickness,
                             label, image);
    }

    drawLine(corners[0], corners[1], boxColor, lineThickness, animated, image);
    drawLine(corners[1], corners[2], boxColor, lineThickness, animated, image);
    drawLine(corners[2], corners[3], boxColor, lineThickness, animated, image);
    drawLine(corners[3], corners[0], boxColor, lineThickness, animated, image);

    circle(*image, detectionTopLeftPt, circleRadius, boxColor, cv::LineTypes::FILLED, cv::LineTypes::LINE_AA);
}

void drawLine(Point2d start, Point2d end, Scalar color, int lineThickness, bool animated, Mat *image)
{
    if (!animated) {
        line(*image, start, end, color, lineThickness, cv::LineTypes::LINE_AA);
        return;
    }

    // Draw dashed line.
    double lineLen = pow(pow(start.x - end.x, 2) + pow(start.y - end.y, 2), .5);

    int dashLen = 10 + lineThickness;
    double maxDashCoverage = lineLen * 0.5; // dash segment should not occupy more than 50% of the total line length
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

void drawFrameNumber(int frameNumber, Mat *image)
{
    std::string label = std::to_string(frameNumber);

    int labelPadding = 8;
    double labelScale = 0.8;
    int labelThickness = 2;
    int labelFont = cv::FONT_HERSHEY_SIMPLEX;

    int baseline = 0;
    Size labelSize = getTextSize(label, labelFont, labelScale, labelThickness, &baseline);

    // Position frame number near bottom right of the frame.
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

void drawBoundingBoxLabel(Point2d pt, double rotation, bool flip, Scalar color, int labelIndent, bool labelOnLeft,
                          int lineThickness, const std::string &label, Mat *image)
{
    int labelPadding = 8;
    double labelScale = 0.8;
    int labelThickness = 2;
    int labelFont = cv::FONT_HERSHEY_SIMPLEX;

    int baseline = 0;
    Size labelSize = getTextSize(label, labelFont, labelScale, labelThickness, &baseline);

    int labelRectBottomLeftX = pt.x;
    int labelRectBottomLeftY = pt.y;
    int labelRectTopRightX = pt.x + labelIndent + labelSize.width + labelPadding;
    int labelRectTopRightY = pt.y - labelSize.height - (2 * labelPadding);

    int labelRectWidth = labelRectTopRightX - labelRectBottomLeftX;
    int labelRectHeight = labelRectBottomLeftY - labelRectTopRightY;

    // Create the black rectangle in which to put the label text.
    Mat labelMat = Mat::zeros(labelRectHeight, labelRectWidth, image->type());

    int labelBottomLeftX = labelIndent;
    int labelBottomLeftY = labelSize.height + labelPadding;

    cv::putText(labelMat, label, Point(labelBottomLeftX, labelBottomLeftY),
        labelFont, labelScale, color, labelThickness, cv::LineTypes::LINE_8);

    if (flip) {
        cv::flip(labelMat, labelMat, 1); // flip around y-axis so the text appears left-to-right in the final frame
    }

    // Next we will place the black label rectangle (labelMat) with text in a white square (paddedLabelMat).
    // The lower-left corner of the rectangle is positioned in the center of the square, shown with an X below:
    //
    //    +--------------------+
    //    |                    |
    //    |        +---------+ |
    //    |        |         | |
    //    |        X---------+ |
    //    |                    |
    //    |                    |
    //    |                    |
    //    +--------------------+

    // Calculate the diagonal distance from the lower-left corner of the rectangle to the upper-right corner.
    // This distance is half the length of a side of the square, enough for the rectangle to be rotated a full 360
    // degrees within the square.
    int labelRectMaxDim = ceil(sqrt(pow(labelRectWidth, 2) + pow(labelRectHeight, 2)));

    Mat paddedLabelMat(labelRectMaxDim * 2, labelRectMaxDim * 2, image->type(), Scalar(255,255,255));

    // Place the label rectangle within the square on the right side of the point, as shown in the above diagram,
    // unless labelOnLeft is true, in which case place it on the left side of the point.
    // If the label is flipped, move the rectangle to the other side of the point to account for how it will be flipped
    // again when generating the final frame.
    cv::Rect labelMatInsertRect(flip ^ labelOnLeft ? labelRectMaxDim - labelRectWidth : labelRectMaxDim,
                                labelRectMaxDim - labelRectHeight, labelMat.cols, labelMat.rows);
    labelMat.copyTo(paddedLabelMat(labelMatInsertRect));

    // Rotate the white box such that the label rectangle with text will be orientated horizontally in the final frame.
    bool hasRotation = !DetectionComponentUtils::RotationAnglesEqual(rotation, 0);
    if (hasRotation) {
        Point2d center(labelRectMaxDim, labelRectMaxDim);
        Mat r = cv::getRotationMatrix2D(center, rotation, 1.0);
        cv::warpAffine(paddedLabelMat, paddedLabelMat, r, paddedLabelMat.size(),
                       cv::InterpolationFlags::INTER_CUBIC, cv::BORDER_CONSTANT, cv::Scalar(255, 255, 255));
    }

    // Generate a black and white mask that only captures the label rectangle within the white box.
    // Black pixels in the mask represent the parts to mask out.
    Mat paddedLabelMask = Mat::zeros(paddedLabelMat.rows, paddedLabelMat.cols, CV_8U);
    cv::inRange(paddedLabelMat, Scalar(255, 255, 255), Scalar(255, 255, 255), paddedLabelMask);
    paddedLabelMask = ~paddedLabelMask;

    try {
        // Place the white box on the image and apply the mask. Align the center of the box (which corresponds to the
        // lower-left corner of the label rectangle) with the desired location (pt).
        cv::Rect paddedLabelMatInsertRect(labelRectBottomLeftX - labelRectMaxDim,
                                          labelRectBottomLeftY - labelRectMaxDim,
                                          paddedLabelMat.cols, paddedLabelMat.rows);
        paddedLabelMat.copyTo((*image)(paddedLabelMatInsertRect), paddedLabelMask);
    } catch (std::exception& e) {
        // Depending on the position of the detection relative to the frame boundary, sometimes the label cannot be
        // drawn within the viewable region. This is fine. Log and continue.
        std::cerr << "Warning: Label outside of viewable region." << std::endl;
    }
}
