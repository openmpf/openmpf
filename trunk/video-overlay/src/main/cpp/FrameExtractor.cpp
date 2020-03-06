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

#include <jni.h>
#include <iostream>
#include <stdlib.h>
#include <string>
#include <exception>
#include <opencv2/core.hpp>
#include <opencv2/videoio.hpp>
#include <opencv2/imgcodecs.hpp>
#include <MPFDetectionObjects.h>
#include <MPFVideoCapture.h>
#include <frame_transformers/AffineFrameTransformer.h>
#include "JniHelper.h"

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
 * Signature: (java/lang/String;java/lang/String;java/util/List;)I
 */
JNIEXPORT int JNICALL Java_org_mitre_mpf_frameextractor_FrameExtractor_executeNative
(JNIEnv *env, jobject frameExtractorInstance, jstring video, jstring destinationPath, jobject paths)
{
    JniHelper jni(env);

    try {
        // Get the list of track objects to extract from.
        jclass clzFrameExtractor = jni.GetObjectClass(frameExtractorInstance);
        jmethodID clzFrameExtractor_fnGetTracks = jni.GetMethodID(clzFrameExtractor,
                "getTracksToExtract", "()Ljava/util/List;");
        jobject trackList = jni.CallObjectMethod(frameExtractorInstance, clzFrameExtractor_fnGetTracks);

        jmethodID clzFrameExtractor_fnMakeFilename = jni.GetMethodID(clzFrameExtractor,
                "makeFilename", "(Ljava/lang/String;II)Ljava/lang/String;");

        // Get the List class and methods to access elements of the track list
        jclass clzList = jni.FindClass("java/util/List");
        jmethodID clzList_fnIterator = jni.GetMethodID(clzList, "iterator", "()Ljava/util/Iterator;");
        // Get the List add method to put results into the output list
        jmethodID clzList_fnAdd = jni.GetMethodID(clzList, "add", "(Ljava/lang/Object;)Z");

        // Get the iterator class and methods
        jclass clzIterator = jni.FindClass("java/util/Iterator");
        jmethodID clzIterator_fnNext = jni.GetMethodID(clzIterator, "next", "()Ljava/lang/Object;");
        jmethodID clzIterator_fnHasNext = jni.GetMethodID(clzIterator, "hasNext", "()Z");

        // Get Set class and methods, for accessing the detections in the JsonTrackOutputObject
        jclass clzSet = jni.FindClass("java/util/Set");
        jmethodID clzSet_fnIterator = jni.GetMethodID(clzSet, "iterator", "()Ljava/util/Iterator;");

        // Get the Map class and method to get the rotation property from the detection properties map
        jclass clzMap = jni.FindClass("java/util/Map");
        jmethodID clzMap_fnGet = jni.GetMethodID(clzMap, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");

        // Get the FrameExtractionResult class and its constructor
        jclass clzExtractionResult = jni.FindClass("org/mitre/mpf/frameextractor/FrameExtractionResult");
        jmethodID clzExtractionResult_fnConstruct = jni.GetMethodID(clzExtractionResult, "<init>", "(IILjava/lang/String;)V");

        // Get the Integer class
        jclass clzInteger = jni.FindClass("java/lang/Integer");
        jmethodID clzInteger_fnParseInt = jni.GetStaticMethodID(clzInteger, "parseInt", "(Ljava/lang/String;)I");

        // Get the JsonTrackOutputObject methods
        jclass clzJsonTrackOutputObject = jni.FindClass("org/mitre/mpf/interop/JsonTrackOutputObject");
        jmethodID clzJsonTrackOutputObject_fnGetId = jni.GetMethodID(clzJsonTrackOutputObject, "getId", "()Ljava/lang/String;");

        jmethodID clzJsonTrackOutputObject_fnGetDetections = jni.GetMethodID(clzJsonTrackOutputObject,
                "getDetections", "()Ljava/util/SortedSet;");

        // Get the JsonDetectionOutputObject methods
        jclass clzJsonDetectionOutputObject = jni.FindClass("org/mitre/mpf/interop/JsonDetectionOutputObject");
        jmethodID clzJsonDetectionOutputObject_fnGetOffsetFrame = jni.GetMethodID(clzJsonDetectionOutputObject,
                "getOffsetFrame", "()I");
        jmethodID clzJsonDetectionOutputObject_fnGetX = jni.GetMethodID(clzJsonDetectionOutputObject, "getX", "()I");
        jmethodID clzJsonDetectionOutputObject_fnGetY = jni.GetMethodID(clzJsonDetectionOutputObject, "getY", "()I");
        jmethodID clzJsonDetectionOutputObject_fnGetHeight = jni.GetMethodID(clzJsonDetectionOutputObject, "getHeight", "()I");
        jmethodID clzJsonDetectionOutputObject_fnGetWidth = jni.GetMethodID(clzJsonDetectionOutputObject, "getWidth", "()I");
        jmethodID clzJsonDetectionOutputObject_fnGetProperties = jni.GetMethodID(clzJsonDetectionOutputObject,
                "getDetectionProperties", "()Ljava/util/SortedMap;");
        // For each detection to be extracted create an AffineFrameTransformer, apply it, and then write out the result.

        std::string videoPath = jni.ToStdString(video);

        MPF::COMPONENT::MPFVideoCapture src(videoPath);
        if (!src.IsOpened()) {
            throw std::runtime_error("Unable to open input video file: " + videoPath);
        }

        Mat frame;

        jobject trackIterator = jni.CallObjectMethod(trackList, clzList_fnIterator);
        // While there are more tracks in the list
        while (jni.CallBooleanMethod(trackIterator, clzIterator_fnHasNext) == JNI_TRUE) {
            // Get the next track
            jobject track = jni.CallObjectMethod(trackIterator, clzIterator_fnNext);
            // Get the set of detections
            jobject detections = jni.CallObjectMethod(track, clzJsonTrackOutputObject_fnGetDetections);
            jobject detectionIterator = jni.CallObjectMethod(detections, clzSet_fnIterator);
            jstring trackIdString = (jstring)jni.CallObjectMethod(track, clzJsonTrackOutputObject_fnGetId);
            jint trackIdInt = 0;
            if (trackIdString != nullptr) {
                trackIdInt = jni.CallIntMethod(clzInteger, clzInteger_fnParseInt, trackIdString);
            }
            // While there are detections in the set
            while (jni.CallBooleanMethod(detectionIterator, clzIterator_fnHasNext) == JNI_TRUE) {
                jobject detection = jni.CallObjectMethod(detectionIterator, clzIterator_fnNext);
                // Create the bounding box
                jint X = jni.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetX);
                jint Y = jni.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetY);
                jint width = jni.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetWidth);
                jint height = jni.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetHeight);
                cv::Rect boundingBox(X, Y, width, height);
                // Get the rotation property
                jobject properties = jni.CallObjectMethod(detection, clzJsonDetectionOutputObject_fnGetProperties);
                std::string rotationPropName("ROTATION");
                jstring jPropName = jni.ToJString(rotationPropName);
                jstring jPropValue = (jstring)jni.CallObjectMethod(properties, clzMap_fnGet, jPropName);
                double rotation = 0.0;
                if (jPropValue != nullptr) {
                    std::string rotationPropValue = jni.ToStdString(jPropValue);
                    rotation = atof(rotationPropValue.c_str());
                }
                // Create the AffineTransformation
                std::vector<std::tuple<cv::Rect, double, bool>> regions(1);
                std::tuple<cv::Rect, double, bool> transform(boundingBox, rotation, false);
                regions.push_back(transform);
                MPF::COMPONENT::AffineTransformation affineTransform(regions, rotation, false);

                // Get the frame number from the detection
                jint offsetFrame = jni.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetOffsetFrame);

                // Tell OpenCV to go to that frame next...
                src.SetFramePosition((int)offsetFrame);

                // Get the frame...
                src >> frame;

                // If that frame is empty, we've reached the end of the video.
                if (frame.empty()) { break; }

                //Otherwise, apply the affine transform
                affineTransform.Apply(frame);
                jstring filename = (jstring) jni.CallObjectMethod(frameExtractorInstance,
                        clzFrameExtractor_fnMakeFilename,
                        destinationPath,
                        trackIdInt,
                        offsetFrame);

                if (filename != nullptr) {
                    std::string destFile = jni.ToStdString(filename);
                    imwrite(destFile, frame);
                    jobject result = jni.CallConstructorMethod(clzExtractionResult, clzExtractionResult_fnConstruct,
                            offsetFrame, trackIdInt, filename);
                    jni.CallObjectMethod(paths, clzList_fnAdd, result);
                }
            }
        }
        return 0;
    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (...) {
        jni.ReportCppException();
    }
}

#ifdef __cplusplus
}
#endif
#endif
