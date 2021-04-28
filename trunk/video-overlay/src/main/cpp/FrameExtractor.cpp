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
#include <string>
#include <exception>
#include <opencv2/core.hpp>
#include <opencv2/imgcodecs.hpp>

#include <MPFVideoCapture.h>
#include <frame_transformers/AffineFrameTransformer.h>
#include <frame_transformers/IFrameTransformer.h>
#include <frame_transformers/NoOpFrameTransformer.h>
#include <detectionComponentUtils.h>

#include "JniHelper.h"


using namespace MPF;
using namespace COMPONENT;

namespace {
    class Extractor {
    public:
        Extractor(JNIEnv *env,
                  jobject frameExtractorInstance,
                  jstring media,
                  jobject mediaMetadata,
                  jstring destinationPath,
                  jboolean croppingFlag,
                  jboolean rotationFillIsBlack,
                  jobject paths);

        void Extract();

    private:
        template<bool crop>
        void DoExtraction();

        template<bool crop>
        void ProcessFrame(cv::Mat& frame, int frameNumber);

        void StoreFrame(const cv::Mat &frame, int trackNumber, int frameNumber);

        JNIEnv * env_;
        JniHelper jni_;
        MPFVideoCapture videoCap_;
        jobject frameExtractorInstance_;
        bool croppingFlag_;
        jobject mediaMetadata_;
        jstring destinationPath_;
        jobject paths_;
        std::unique_ptr<jstring, JStringDeleter> rotationJStringPtr_;
        std::unique_ptr<jstring, JStringDeleter> horizontalFlipJStringPtr_;
        cv::Scalar fillColor_;

        jmethodID clzFrameExtractor_fnGetTrackIndices_;
        jmethodID clzFrameExtractor_fnGetDetection_;
        jmethodID clzFrameExtractor_fnMakeFilename_;

        jmethodID clzList_fnAdd_;

        jmethodID clzIterator_fnNext_;
        jmethodID clzIterator_fnHasNext_;

        jmethodID clzSet_fnIterator_;

        jmethodID clzMap_fnGet_;

        jclass clzExtractionResult_;
        jmethodID clzExtractionResult_fnConstruct_;

        jmethodID clzInteger_fnIntValue_;

        jmethodID clzJsonDetectionOutputObject_fnGetX_;
        jmethodID clzJsonDetectionOutputObject_fnGetY_;
        jmethodID clzJsonDetectionOutputObject_fnGetWidth_;
        jmethodID clzJsonDetectionOutputObject_fnGetHeight_;
        jmethodID clzJsonDetectionOutputObject_fnGetProperties_;

        jobject frameIterator_;
    };
}


extern "C" JNIEXPORT int JNICALL Java_org_mitre_mpf_frameextractor_FrameExtractor_executeNative (
        JNIEnv *env,
        jobject frameExtractorInstance,
        jstring media,
        jobject mediaMetadata,
        jstring destinationPath,
        jboolean croppingFlag,
        jboolean rotationFillIsBlack,
        jobject paths) {
    JniHelper jni(env);
    try {
        Extractor extractor(env, frameExtractorInstance, media, mediaMetadata, destinationPath,
                            croppingFlag, rotationFillIsBlack, paths);
        extractor.Extract();
        return 0;
    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (...) {
        jni.ReportCppException();
    }
}


namespace {
    cv::Mat cropFrame(const cv::Mat &original, int x, int y, int width, int height, double rotation,
                      bool flip, const cv::Scalar& fillColor) {
        if (width == 0 || height == 0) {
            return cv::Mat(1, 1, original.type(), fillColor);
        }

        bool hasRotation = !DetectionComponentUtils::RotationAnglesEqual(rotation, 0);
        if (hasRotation || flip) {
            cv::Mat transformFrame = original.clone();
            FeedForwardExactRegionAffineTransformer transformer(
                    { MPFRotatedRect(x, y, width, height, rotation, flip) },
                    fillColor,
                    IFrameTransformer::Ptr(new NoOpFrameTransformer(transformFrame.size())));
            transformer.TransformFrame(transformFrame, 0);
            return transformFrame;
        }
        else {
            cv::Rect frameRect(0, 0, original.cols, original.rows);
            cv::Rect detectionRect(x, y, width, height);
            auto extractionRegion = frameRect & detectionRect;
            if (extractionRegion.empty()) {
                return cv::Mat(height, width, original.type(), fillColor);
            }
            else {
                return original(extractionRegion);
            }
        }
    }


    Extractor::Extractor(JNIEnv* env,
                         jobject frameExtractorInstance,
                         jstring media,
                         jobject mediaMetadata,
                         jstring destinationPath,
                         jboolean croppingFlag,
                         jboolean rotationFillIsBlack,
                         jobject paths)
            : env_(env)
            , jni_(env_)
            , videoCap_(jni_.ToStdString(media))
            , frameExtractorInstance_(frameExtractorInstance)
            , croppingFlag_(croppingFlag)
            , mediaMetadata_(mediaMetadata)
            , destinationPath_(destinationPath)
            , paths_(paths)
            , rotationJStringPtr_(jni_.ToJString("ROTATION"))
            , horizontalFlipJStringPtr_(jni_.ToJString("HORIZONTAL_FLIP"))
            , fillColor_(cv::Scalar::all(rotationFillIsBlack ? 0 : 255))
    {
        jclass clzFrameExtractor = jni_.GetObjectClass(frameExtractorInstance);
        jmethodID clzFrameExtractor_fnGetFrames = jni_.GetMethodID(
                clzFrameExtractor,
                "getFrameNumbers",
                "()Ljava/util/Set;");
        clzFrameExtractor_fnGetTrackIndices_ = jni_.GetMethodID(
                clzFrameExtractor,
                "getTrackIndices",
                "(I)Ljava/util/Set;");
        clzFrameExtractor_fnGetDetection_ = jni_.GetMethodID(
                clzFrameExtractor,
                "getDetection",
                "(II)Lorg/mitre/mpf/interop/JsonDetectionOutputObject;");
        clzFrameExtractor_fnMakeFilename_ = jni_.GetMethodID(
                clzFrameExtractor,
                "makeFilename",
                "(Ljava/lang/String;II)Ljava/lang/String;");

        // Get the List class and method to add to the paths output list.
        jclass clzList = jni_.FindClass("java/util/List");
        clzList_fnAdd_ = jni_.GetMethodID(clzList, "add", "(Ljava/lang/Object;)Z");

        // Get the iterator class and methods.
        jclass clzIterator = jni_.FindClass("java/util/Iterator");
        clzIterator_fnNext_ = jni_.GetMethodID(
                clzIterator,
                "next",
                "()Ljava/lang/Object;");
        clzIterator_fnHasNext_ = jni_.GetMethodID(clzIterator, "hasNext", "()Z");

        // Get Set class and methods, for accessing the frame and track keySets.
        jclass clzSet = jni_.FindClass("java/util/TreeSet");
        clzSet_fnIterator_ = jni_.GetMethodID(
                clzSet,
                "iterator",
                "()Ljava/util/Iterator;");

        // Get the Map class and method to get the rotation property from the detection properties map.
        jclass clzMap = jni_.FindClass("java/util/Map");
        clzMap_fnGet_ = jni_.GetMethodID(
                clzMap,
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;");

        // Get the FrameExtractionResult class and its constructor.
        clzExtractionResult_ = jni_.FindClass("org/mitre/mpf/frameextractor/FrameExtractionResult");
        clzExtractionResult_fnConstruct_ = jni_.GetMethodID(
                clzExtractionResult_,
                "<init>",
                "(IILjava/lang/String;)V");

        // Get the Integer class and method to convert to jint.
        jclass clzInteger = jni_.FindClass("java/lang/Integer");
        clzInteger_fnIntValue_ = jni_.GetMethodID(clzInteger, "intValue", "()I");


        // Get the JsonDetectionOutputObject methods.
        jclass clzJsonDetectionOutputObject = jni_.FindClass("org/mitre/mpf/interop/JsonDetectionOutputObject");
        clzJsonDetectionOutputObject_fnGetX_ = jni_.GetMethodID(
                clzJsonDetectionOutputObject,
                "getX",
                "()I");
        clzJsonDetectionOutputObject_fnGetY_ = jni_.GetMethodID(
                clzJsonDetectionOutputObject,
                "getY",
                "()I");
        clzJsonDetectionOutputObject_fnGetHeight_ = jni_.GetMethodID(
                clzJsonDetectionOutputObject,
                "getHeight",
                "()I");
        clzJsonDetectionOutputObject_fnGetWidth_ = jni_.GetMethodID(
                clzJsonDetectionOutputObject,
                "getWidth",
                "()I");
        clzJsonDetectionOutputObject_fnGetProperties_ = jni_.GetMethodID(
                clzJsonDetectionOutputObject,
                "getDetectionProperties",
                "()Ljava/util/SortedMap;");

        // Get the set of frames to be extracted and an iterator for it.
        jobject frameNumberSet = jni_.CallObjectMethod(
                frameExtractorInstance,
                clzFrameExtractor_fnGetFrames);
        frameIterator_ = jni_.CallObjectMethod(frameNumberSet, clzSet_fnIterator_);
    }

    void Extractor::Extract() {
        if (croppingFlag_) {
            DoExtraction<true>();
        }
        else {
            DoExtraction<false>();
        }
    }

    template <bool crop>
    void Extractor::DoExtraction() {
        // Iterate over the frames in the set
        while (jni_.CallBooleanMethod(frameIterator_, clzIterator_fnHasNext_)) {
            LocalJniFrame perVideoFrameJniFrame(env_, 32);
            // Get the frame number and read it.
            jobject thisFrameNumObj = jni_.CallObjectMethod(frameIterator_, clzIterator_fnNext_);
            jint thisFrameNum = jni_.CallIntMethod(thisFrameNumObj, clzInteger_fnIntValue_);

            // Tell OpenCV to go to this frame next...
            videoCap_.SetFramePosition((int)thisFrameNum);

            // Get the frame...
            cv::Mat frame;
            videoCap_ >> frame;

            // If that frame is empty, we've reached the end of the video.
            if (frame.empty()) { break; }

            ProcessFrame<crop>(frame, thisFrameNum);
        }
    }

    template<>
    void Extractor::ProcessFrame<true>(cv::Mat& frame, int frameNumber) {
        jobject trackIndexSet = jni_.CallObjectMethod(
                frameExtractorInstance_,
                clzFrameExtractor_fnGetTrackIndices_,
                frameNumber);
        jobject trackIterator = jni_.CallObjectMethod(trackIndexSet, clzSet_fnIterator_);

        // For each track, perform the extraction for the associated detection object.
        while (jni_.CallBooleanMethod(trackIterator, clzIterator_fnHasNext_)) {
            LocalJniFrame perTrackLocalFrame(env_, 32);

            // Get the detection associated with this track
            jobject thisTrackObj = jni_.CallObjectMethod(trackIterator, clzIterator_fnNext_);
            jint thisTrack = jni_.CallIntMethod(thisTrackObj, clzInteger_fnIntValue_);
            jobject detection = jni_.CallObjectMethod(
                    frameExtractorInstance_,
                    clzFrameExtractor_fnGetDetection_,
                    frameNumber,
                    thisTrack);

            // Create the bounding box.
            jint x = jni_.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetX_);
            jint y = jni_.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetY_);
            jint width = jni_.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetWidth_);
            jint height = jni_.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetHeight_);
            cv::Rect detectionBox(x, y, width, height);

            // Get the rotation property.
            jobject properties = jni_.CallObjectMethod(
                    detection,
                    clzJsonDetectionOutputObject_fnGetProperties_);
            jstring jPropValue = (jstring) jni_.CallObjectMethod(
                    properties,
                    clzMap_fnGet_,
                    *rotationJStringPtr_);
            double rotation = 0.0;
            if (jPropValue != nullptr) {
                std::string rotationPropValue = jni_.ToStdString(jPropValue);
                rotation = std::stod(rotationPropValue);
            }

            // Get the horizontal flip property.
            jPropValue = (jstring) jni_.CallObjectMethod(
                    mediaMetadata_,
                    clzMap_fnGet_,
                    *horizontalFlipJStringPtr_);
            bool horizontalFlip = false;
            if (jPropValue != nullptr) {
                horizontalFlip = jni_.ToBool(jPropValue);
            }
            cv:: Mat transformFrame = cropFrame(frame, x, y, width, height, rotation,
                                                horizontalFlip, fillColor_);
            StoreFrame(transformFrame, thisTrack, frameNumber);
        }
    }

    template<>
    void Extractor::ProcessFrame<false>(cv::Mat& frame, int frameNumber) {
        // No cropping, but we still need to account for media metadata (e.g. EXIF).

        // Get the media metadata rotation property.
        auto jPropValue = (jstring) jni_.CallObjectMethod(
                mediaMetadata_, clzMap_fnGet_, *rotationJStringPtr_);
        double rotation = 0;
        if (jPropValue != nullptr) {
            std::string rotationPropValue = jni_.ToStdString(jPropValue);
            rotation = std::stod(rotationPropValue);
        }

        // Get the media metadata horizontal flip property.
        jPropValue = (jstring) jni_.CallObjectMethod(mediaMetadata_, clzMap_fnGet_,
                                                     *horizontalFlipJStringPtr_);
        bool horizontalFlip = false;
        if (jPropValue != nullptr) {
            horizontalFlip = jni_.ToBool(jPropValue);
        }

        bool hasRotation = !DetectionComponentUtils::RotationAnglesEqual(rotation, 0);
        if (hasRotation || horizontalFlip) {
            AffineFrameTransformer transformer(
                    rotation, horizontalFlip, fillColor_,
                    IFrameTransformer::Ptr(new NoOpFrameTransformer(frame.size())));

            transformer.TransformFrame(frame, 0);
        }
        StoreFrame(frame, 0, frameNumber);
    }


    void Extractor::StoreFrame(const cv::Mat &frame, int trackNumber, int frameNumber) {
        jstring filename = (jstring) jni_.CallObjectMethod(
                frameExtractorInstance_,
                clzFrameExtractor_fnMakeFilename_,
                destinationPath_,
                trackNumber,
                frameNumber);

        if (filename != nullptr) {
            std::string destFile = jni_.ToStdString(filename);
            cv::imwrite(destFile, frame);
            jobject result = jni_.CallConstructorMethod(
                    clzExtractionResult_,
                    clzExtractionResult_fnConstruct_,
                    frameNumber,
                    trackNumber,
                    filename);
            jni_.CallObjectMethod(paths_, clzList_fnAdd_, result);
        }
    }
}
