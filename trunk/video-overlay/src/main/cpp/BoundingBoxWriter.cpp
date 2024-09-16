/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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
#include <cstdlib>
#include <cmath>
#include <array>
#include <opencv2/opencv.hpp>
#include <opencv2/core.hpp>
#include <opencv2/videoio.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/freetype.hpp>

#include <MPFRotatedRect.h>
#include <frame_transformers/NoOpFrameTransformer.h>
#include <frame_transformers/IFrameTransformer.h>
#include <frame_transformers/AffineFrameTransformer.h>

#include "detectionComponentUtils.h"
#include "JniHelper.h"
#include "BoundingBoxImageHandle.h"
#include "BoundingBoxVideoHandle.h"
#include "ResolutionConfig.h"
#include "markup.pb.h"

using namespace cv;
using namespace MPF;
using namespace COMPONENT;
namespace mpf_buffers = org::mitre::mpf::wfm::buffers;

using pFreeType2 = cv::Ptr<cv::freetype::FreeType2>;

using pb_props_t = google::protobuf::Map<std::string, std::string>;


// https://unicode.org/emoji/charts/full-emoji-list.html
constexpr const char *notoEmojiRegularPath = "/usr/share/fonts/google-noto-emoji/NotoEmoji-Regular.ttf";
constexpr const char *fastForwardEmoji = "\U000023E9";
constexpr const char *anchorEmoji      = "\U00002693";
constexpr const char *magGlassEmoji    = "\U0001F50D";
constexpr const char *paperClipEmoji   = "\U0001F4CE";
constexpr const char *movieCameraEmoji = "\U0001F3A5";
constexpr const char *starEmoji        = "\U00002B50";

void markupVideo(const mpf_buffers::MarkupRequest& markupRequest);
void markupImage(const mpf_buffers::MarkupRequest& markupRequest);

template<typename TMediaHandle>
void markup(
        const mpf_buffers::MarkupRequest& markup_request,
        pFreeType2 freeType2,
        const ResolutionConfig& resCfg,
        TMediaHandle& mediaHandle);

std::size_t getMaxLabelLength(const mpf_buffers::MarkupRequest& markupRequest);

ResolutionConfig getResolutionConfig(pFreeType2 freeType2, const cv::Size &frameSize,
                                     std::size_t maxLabelLength);

pFreeType2 initFreeType2();

bool getBoolProperty(
        const std::string& key,
        bool defaultValue,
        const pb_props_t& properties);

double getDoubleProperty(
        const std::string& key,
        double defaultValue,
        const pb_props_t& properties);

int getIntProperty(
        const std::string& key,
        int defaultValue,
        const pb_props_t& properties);

std::string getStringProperty(
        const std::string& key,
        const std::string& defaultValue,
        const pb_props_t& properties);

void drawBoundingBox(int x, int y, int width, int height, double boxRotation, bool boxFlip, double mediaRotation,
                     bool mediaFlip, int red, int green, int blue, double alpha, const std::string &emojiLabel,
                     const std::string &textLabel, bool labelChooseSide, pFreeType2 freeType2,
                     const ResolutionConfig &resCfg, Mat &image);

void drawFrameNumber(int frameNumber, double alpha, const ResolutionConfig &resCfg, Mat &image);

void drawBoundingBoxLabel(const Point2d &pt, double rotation, bool flip, const Scalar &color, double alpha,
                          bool labelOnLeft, const std::string &emojiLabel, const std::string &textLabel,
                          pFreeType2 freeType2, const ResolutionConfig &resCfg, Mat &image);


mpf_buffers::MarkupRequest parseProtobuf(JniHelper& jniHelper, jbyteArray java_array);

std::string getEmojiLabel(
        const mpf_buffers::BoundingBox& box,
        bool markMovingEnabled,
        bool markExemplarsEnabled,
        bool markBoxSourceEnabled);


extern "C" JNIEXPORT void JNICALL Java_org_mitre_mpf_videooverlay_BoundingBoxWriter_markupNative
  (JNIEnv* env, jclass cls, jbyteArray protobytes)
{
    JniHelper jni(env);
    try {
        auto markupRequest = parseProtobuf(jni, protobytes);
        if (markupRequest.media_type() == mpf_buffers::MediaType::IMAGE) {
            markupImage(markupRequest);
        }
        else if (markupRequest.media_type() == mpf_buffers::MediaType::VIDEO) {
            markupVideo(markupRequest);
        }
        else {
            jni.ReportCppException("Received markup request that was not for video or image.");
        }
    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (...) {
        jni.ReportCppException();
    }
}


void markupVideo(const mpf_buffers::MarkupRequest& markupRequest) {
    auto encoder = getStringProperty(
        "MARKUP_VIDEO_ENCODER", "mjpeg", markupRequest.markup_properties());
    std::transform(encoder.begin(), encoder.end(), encoder.begin(), ::tolower);
    int vp9Crf = getIntProperty("MARKUP_VIDEO_VP9_CRF", 31, markupRequest.markup_properties());

    pFreeType2 freeType2 = initFreeType2();
    MPF::COMPONENT::MPFVideoCapture videoCapture(markupRequest.source_path());
    ResolutionConfig resCfg = getResolutionConfig(
            freeType2,
            videoCapture.GetFrameSize(),
            getMaxLabelLength(markupRequest));

    BoundingBoxVideoHandle boundingBoxVideoHandle(
            markupRequest.destination_path(), encoder, vp9Crf, std::move(videoCapture));

    markup(markupRequest, freeType2, resCfg, boundingBoxVideoHandle);

    boundingBoxVideoHandle.Close();
}


void markupImage(const mpf_buffers::MarkupRequest& markupRequest) {
    BoundingBoxImageHandle boundingBoxImageHandle(
            markupRequest.source_path(),
            markupRequest.destination_path());

    pFreeType2 freeType2 = initFreeType2();
    ResolutionConfig resCfg = getResolutionConfig(
            freeType2,
            boundingBoxImageHandle.GetFrameSize(),
            getMaxLabelLength(markupRequest));

    markup(markupRequest, freeType2, resCfg, boundingBoxImageHandle);
}

template<typename TMediaHandle>
void markup(
        const mpf_buffers::MarkupRequest& markupRequest,
        pFreeType2 freeType2,
        const ResolutionConfig& resCfg,
        TMediaHandle& mediaHandle) {

    bool labelsEnabled = getBoolProperty(
        "MARKUP_LABELS_ENABLED", true, markupRequest.markup_properties());

    double labelsAlpha = getDoubleProperty(
        "MARKUP_LABELS_ALPHA", 0.5, markupRequest.markup_properties());

    bool borderEnabled = getBoolProperty(
        "MARKUP_BORDER_ENABLED", false, markupRequest.markup_properties());

    bool labelsChooseSideEnabled = getBoolProperty(
        "MARKUP_LABELS_CHOOSE_SIDE_ENABLED", true, markupRequest.markup_properties());

    bool markMovingEnabled = getBoolProperty(
        "MARKUP_VIDEO_MOVING_OBJECT_ICONS_ENABLED", false, markupRequest.markup_properties());
    bool markExemplarsEnabled = getBoolProperty(
        "MARKUP_VIDEO_EXEMPLAR_ICONS_ENABLED", true, markupRequest.markup_properties());
    bool markBoxSourceEnabled = getBoolProperty(
        "MARKUP_VIDEO_BOX_SOURCE_ICONS_ENABLED", false, markupRequest.markup_properties());

    bool frameNumbersEnabled = getBoolProperty(
        "MARKUP_VIDEO_FRAME_NUMBERS_ENABLED", true, markupRequest.markup_properties());

    double mediaRotation = getDoubleProperty("ROTATION", 0, markupRequest.media_metadata());
    bool mediaFlip = getBoolProperty("HORIZONTAL_FLIP", false, markupRequest.media_metadata());

    int currentFrameNum = -1;
    while (true) {
        currentFrameNum++;
        cv::Mat frame;
        if (!mediaHandle.Read(frame) || frame.empty()) {
            break;
        }
        // Add a black border to allow boxes and labels to extend off the edges of the image.
        cv::copyMakeBorder(
            frame, frame, resCfg.framePadding, resCfg.framePadding, resCfg.framePadding,
            resCfg.framePadding, cv::BORDER_CONSTANT, Scalar(0, 0, 0));

        auto currentFrameEntryIter = markupRequest.bounding_boxes().find(currentFrameNum);
        if (currentFrameEntryIter != markupRequest.bounding_boxes().end()) {
            for (const auto& box : currentFrameEntryIter->second.bounding_boxes()) {
                std::string emojiLabel;
                std::string textLabel;

                if (labelsEnabled) {
                    if (mediaHandle.useIcons) {
                        emojiLabel = getEmojiLabel(
                            box, markMovingEnabled, markExemplarsEnabled, markBoxSourceEnabled);
                    }

                    if (!box.label().empty()) {
                        if (!emojiLabel.empty()) {
                            textLabel += ' ';
                        }
                        textLabel += box.label();
                    }
                }
                drawBoundingBox(
                    box.x() + resCfg.framePadding,
                    box.y() + resCfg.framePadding,
                    box.width() > 0 ? box.width() : mediaHandle.GetFrameSize().width,
                    box.height() > 0 ? box.height() : mediaHandle.GetFrameSize().height,
                    box.rotation_degrees(),
                    box.flip(),
                    mediaRotation,
                    mediaFlip,
                    box.red(),
                    box.green(),
                    box.blue(),
                    labelsAlpha,
                    emojiLabel,
                    textLabel,
                    labelsChooseSideEnabled,
                    freeType2,
                    resCfg,
                    frame);
            }
        }

        // Crop the padding off.
        if (!borderEnabled) {
            frame = frame(cv::Rect(
                cv::Point(resCfg.framePadding, resCfg.framePadding),
                mediaHandle.GetFrameSize()));
        }
        else {
            // Reduce the border padding to minimize screen real estate.
            frame = frame(cv::Rect(
                resCfg.framePadding / 2,
                resCfg.framePadding / 2,
                mediaHandle.GetFrameSize().width + resCfg.framePadding,
                mediaHandle.GetFrameSize().height + resCfg.framePadding));
        }
        // Generate the final frame by flipping and/or rotating the raw frame to account for media metadata.
        AffineFrameTransformer(
            mediaRotation, mediaFlip, cv::Scalar(0, 0, 0),
            std::make_unique<NoOpFrameTransformer>(frame.size())
        ).TransformFrame(frame, 0);

        if (frameNumbersEnabled && mediaHandle.showFrameNumbers) {
            drawFrameNumber(currentFrameNum, labelsAlpha, resCfg, frame);
        }
        mediaHandle.HandleMarkedFrame(frame);
    }
}


std::size_t getMaxLabelLength(const mpf_buffers::MarkupRequest& markupRequest) {
    std::size_t maxLabelLength = 0;
    for (const auto& [frame, boxesWrapper] : markupRequest.bounding_boxes()) {
        for (const auto& box : boxesWrapper.bounding_boxes()) {
            maxLabelLength = std::max(box.label().size(), maxLabelLength);
        }
    }
    return maxLabelLength;
}


ResolutionConfig getResolutionConfig(pFreeType2 freeType2, const cv::Size &frameSize,
                                     std::size_t maxLabelLength) {
    int minDim = std::min(frameSize.height, frameSize.width);

    int textLabelFont = cv::FONT_HERSHEY_SIMPLEX;
    int fontBaseHeight = 21; // px

    double textScaleFactor = 1 / (100 * (double)fontBaseHeight); // stack 100 labels in an image, vertically

    double textLabelScale = std::max(textScaleFactor * minDim, 0.40);
    int textLabelThickness = std::ceil(textLabelScale + 0.25);
    int labelPadding = 10 * textLabelScale;


    // Because we use LINE_AA below for anti-aliasing, which uses a Gaussian filter, the lack of pixels near the edge
    // of the frame causes a problem when attempting to draw a line along the edge using a thickness of 1.
    // Specifically, no pixels will be drawn near the edge.
    // Refer to: https://stackoverflow.com/questions/42484955/pixels-at-arrow-tip-missing-when-using-antialiasing
    // To address this, we use a minimum thickness of 2.
    int lineThickness = std::max(textLabelThickness, 2);

    int minCircleRadius = 3;
    int circleRadius = lineThickness == 1 ? minCircleRadius : lineThickness + 5;

    double maxCircleCoverage = minDim * 0.25; // circle should not cover more than 25% of the minimum dimension
    if (circleRadius > maxCircleCoverage) {
        circleRadius = std::max((int)maxCircleCoverage, minCircleRadius);
    }

    int labelIndent = circleRadius + 2;


    // Calculate frame padding for worst-case scenario.
    int baseline = 0;
    std::string maxSizeLabel(std::max(maxLabelLength, 1UL), 'm'); // "m" is the widest character
    Size textLabelSize = getTextSize(maxSizeLabel, textLabelFont, textLabelScale,
                                     textLabelThickness, &baseline);

    int emojiHeight = textLabelSize.height;
    Size emojiLabelSize = freeType2->getTextSize(std::string(magGlassEmoji) + magGlassEmoji, // magnifying glass is the widest emoji
                                                 emojiHeight, cv::FILLED, &baseline);

    int framePadding = labelIndent + textLabelSize.width + emojiLabelSize.width + labelPadding;

    return { lineThickness, circleRadius, textLabelFont, textLabelScale, textLabelThickness, labelIndent, labelPadding,
             framePadding };
}

pFreeType2 initFreeType2() {
    pFreeType2 freeType2 = cv::freetype::createFreeType2();
    try {
        freeType2->loadFontData(notoEmojiRegularPath, 0);
    } catch (cv::Exception& e) {
        throw std::runtime_error(std::string("An error occurred in freeType2. Check that \"") +
                                 notoEmojiRegularPath + "\" exists: " + e.what());
    }
    return freeType2;
}


bool getBoolProperty(
        const std::string& key,
        bool defaultValue,
        const pb_props_t& properties) {
    auto iter = properties.find(key);
    if (iter == properties.end()) {
        return defaultValue;
    }
    static const std::string trueString = "TRUE";
    return std::equal(
            trueString.begin(), trueString.end(),
            iter->second.begin(), iter->second.end(),
            [](char trueChar, char actualChar) { return trueChar == std::toupper(actualChar); });
}


double getDoubleProperty(
        const std::string& key,
        double defaultValue,
        const pb_props_t& properties) {
    auto iter = properties.find(key);
    if (iter == properties.end()) {
        return defaultValue;
    }
    else {
        return std::stod(iter->second);
    }
}

int getIntProperty(
        const std::string& key,
        int defaultValue,
        const pb_props_t& properties) {
    auto iter = properties.find(key);
    if (iter == properties.end()) {
        return defaultValue;
    }
    else {
        return std::stoi(iter->second);
    }
}

std::string getStringProperty(
        const std::string& key,
        const std::string& defaultValue,
        const pb_props_t& properties) {
    auto iter = properties.find(key);
    if (iter == properties.end()) {
        return defaultValue;
    }
    else {
        return iter->second;
    }
}


void drawBoundingBox(int x, int y, int width, int height, double boxRotation, bool boxFlip, double mediaRotation,
                     bool mediaFlip, int red, int green, int blue, double alpha, const std::string &emojiLabel,
                     const std::string &textLabel, bool labelChooseSide, pFreeType2 freeType2,
                     const ResolutionConfig &resCfg, Mat &image)
{
    // Calculate the box coordinates relative to the raw frame.
    // The frame is "raw" in the sense that it's not flipped and/or rotated to account for media metadata.
    std::array<Point2d, 4> corners = MPFRotatedRect(x, y, width, height, boxRotation, boxFlip).GetCorners();
    auto detectionTopLeftPt = corners[0];

    Scalar boxColor(blue, green, red);

    if (!textLabel.empty() || !emojiLabel.empty()) {
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
        auto adjTopPtIndex = std::distance(adjCorners.begin(), adjTopPtIter);
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

        drawBoundingBoxLabel(rawTopPt, mediaRotation, mediaFlip, boxColor, alpha, labelOnLeft, emojiLabel, textLabel,
                             freeType2, resCfg, image);
    }

    line(image, corners[0], corners[1], boxColor, resCfg.lineThickness, cv::LineTypes::LINE_AA);
    line(image, corners[1], corners[2], boxColor, resCfg.lineThickness, cv::LineTypes::LINE_AA);
    line(image, corners[2], corners[3], boxColor, resCfg.lineThickness, cv::LineTypes::LINE_AA);
    line(image, corners[3], corners[0], boxColor, resCfg.lineThickness, cv::LineTypes::LINE_AA);

    circle(image, detectionTopLeftPt, resCfg.circleRadius, boxColor, cv::LineTypes::FILLED, cv::LineTypes::LINE_AA);
}

/* This code might be useful in the future.
void drawDashedLine(const Point2d &start, const Point2d &end, const Scalar &color, const ResolutionConfig &resCfg,
                    Mat &image) {
    double lineLen = pow(pow(start.x - end.x, 2) + pow(start.y - end.y, 2), .5);

    int dashLen = 10 + resCfg.lineThickness;
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
            line(image, prev, curr, color, resCfg.lineThickness);
        }
        prev = curr;
        draw = !draw;
    } while (percent < 1.0);
}
*/

void drawFrameNumber(int frameNumber, double alpha, const ResolutionConfig &resCfg, Mat &image)
{
    std::string label = std::to_string(frameNumber);

    int baseline = 0;
    Size labelSize =
        getTextSize(label, resCfg.textLabelFont, resCfg.textLabelScale, resCfg.textLabelThickness, &baseline);

    int labelRectWidth = labelSize.width + (2 * resCfg.labelPadding);
    int labelRectHeight = labelSize.height + (2 * resCfg.labelPadding);

    // Position frame number near top right of the frame.
    int labelRectTopLeftX = image.cols - 10 - labelRectWidth;
    int labelRectTopLeftY = 10;

    // Create the black rectangle in which to put the label text.
    Mat labelMat = Mat::zeros(labelRectHeight, labelRectWidth, image.type());

    int labelBottomLeftX = resCfg.labelPadding;
    int labelBottomLeftY = labelSize.height + resCfg.labelPadding;

    cv::putText(labelMat, label, Point(labelBottomLeftX, labelBottomLeftY), resCfg.textLabelFont, resCfg.textLabelScale,
                Scalar(255, 255, 255), resCfg.textLabelThickness, cv::LineTypes::LINE_AA);

    // Place the label on the image.
    cv::Rect labelMatInsertRect(labelRectTopLeftX, labelRectTopLeftY, labelMat.cols, labelMat.rows);
    auto insertionRegion = image(labelMatInsertRect);
    cv::addWeighted(insertionRegion, 1 - alpha, labelMat, alpha, 0, insertionRegion);
}

void drawBoundingBoxLabel(const Point2d &pt, double rotation, bool flip, const Scalar &color, double alpha,
                          bool labelOnLeft, const std::string &emojiLabel, const std::string &textLabel,
                          pFreeType2 freeType2, const ResolutionConfig &resCfg, Mat &image)
{
    int baseline = 0;
    Size textLabelSize =
        getTextSize(textLabel, resCfg.textLabelFont, resCfg.textLabelScale, resCfg.textLabelThickness, &baseline);

    int emojiHeight = textLabelSize.height;
    Size emojiLabelSize = freeType2->getTextSize(emojiLabel, emojiHeight, cv::FILLED, &baseline);

    int labelRectBottomLeftX = pt.x;
    int labelRectBottomLeftY = pt.y;

    int labelRectWidth = resCfg.labelIndent + emojiLabelSize.width + textLabelSize.width + resCfg.labelPadding;
    int labelRectHeight = textLabelSize.height + (2 * resCfg.labelPadding);

    // Create the black rectangle in which to put the label.
    Mat labelMat = Mat::zeros(labelRectHeight, labelRectWidth, image.type());

    int labelBottomLeftX = resCfg.labelIndent;
    int labelBottomLeftY = textLabelSize.height + resCfg.labelPadding;

    freeType2->putText(labelMat, emojiLabel, Point(labelBottomLeftX, resCfg.labelPadding),
                       emojiHeight, color, cv::FILLED, cv::LineTypes::LINE_AA, false);

    cv::putText(labelMat, textLabel, Point(labelBottomLeftX + emojiLabelSize.width, labelBottomLeftY),
                resCfg.textLabelFont, resCfg.textLabelScale, color, resCfg.textLabelThickness, cv::LineTypes::LINE_AA);

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

    Mat paddedLabelMat(labelRectMaxDim * 2, labelRectMaxDim * 2, image.type(), Scalar(255,255,255));

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

    // Place the white box on the image. Align the center of the box (which corresponds to the lower-left corner of
    // the label rectangle) with the desired location (pt). Shift the label up slightly to account for line thickness.
    cv::Rect paddedLabelMatInsertRect(labelRectBottomLeftX - labelRectMaxDim,
                                      labelRectBottomLeftY - labelRectMaxDim - (resCfg.lineThickness/2),
                                      paddedLabelMat.cols, paddedLabelMat.rows);

    cv::Rect intersection(cv::Rect(cv::Point(0, 0), image.size()) & paddedLabelMatInsertRect);

    auto insertionRegion = image(intersection);
    if (!insertionRegion.empty()) {
        auto paddedLabelMatRegion = paddedLabelMat(cv::Rect(intersection.x - paddedLabelMatInsertRect.x,
                                                            intersection.y - paddedLabelMatInsertRect.y,
                                                            intersection.width, intersection.height));
        insertionRegion.forEach<cv::Vec3b>([&](cv::Vec3b &pixel, const int position[]) {
            if (paddedLabelMatRegion.at<cv::Vec3b>(position) != cv::Vec3b{255, 255, 255}) {
                pixel = (1 - alpha) * pixel + alpha * paddedLabelMatRegion.at<cv::Vec3b>(position);
            }
        });
    }
}

mpf_buffers::MarkupRequest parseProtobuf(JniHelper& jniHelper, jbyteArray java_array) {
    auto byte_array = jniHelper.GetByteArray(java_array);
    mpf_buffers::MarkupRequest markup_request;
    markup_request.ParseFromArray(byte_array.GetBytes(), byte_array.GetLength());
    return markup_request;
}

std::string getEmojiLabel(
        const mpf_buffers::BoundingBox& box,
        bool markMovingEnabled,
        bool markExemplarsEnabled,
        bool markBoxSourceEnabled) {
    std::string emojiLabel;
    if (markMovingEnabled) {
        if (box.moving()) {
            emojiLabel += fastForwardEmoji;
        }
        else {
            emojiLabel += anchorEmoji;
        }
    }
    if (markExemplarsEnabled && box.exemplar()) {
        emojiLabel += starEmoji;
        return emojiLabel;
    }
    if (markBoxSourceEnabled) {
        if (box.source() == mpf_buffers::BoundingBoxSource::DETECTION_ALGORITHM) {
            emojiLabel += magGlassEmoji;
        }
        else if (box.source() == mpf_buffers::BoundingBoxSource::TRACKING_FILLED_GAP) {
            emojiLabel += paperClipEmoji;
        }
        else {
            // ANIMATION
            emojiLabel += movieCameraEmoji;
        }
    }
    return emojiLabel;
}
