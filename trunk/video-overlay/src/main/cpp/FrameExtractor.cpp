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
#include <stdlib.h>
#include <string>
#include <exception>
#include <opencv2/core.hpp>
#include <opencv2/videoio.hpp>
#include <opencv2/imgcodecs.hpp>
#include <MPFDetectionObjects.h>
#include <MPFVideoCapture.h>
#include <frame_transformers/AffineFrameTransformer.h>
#include <frame_transformers/IFrameTransformer.h>
#include <frame_transformers/NoOpFrameTransformer.h>
#include <frame_transformers/SearchRegion.h>
#include "JniHelper.h"

/* Header for class org_mitre_mpf_frameextractor_FrameExtractor */

#ifndef _Included_org_mitre_mpf_frameextractor_FrameExtractor
#define _Included_org_mitre_mpf_frameextractor_FrameExtractor
#ifdef __cplusplus
extern "C" {
using namespace cv;
using namespace MPF;
using namespace COMPONENT;

#endif


/*
 * Class:     org_mitre_mpf_frameextractor_FrameExtractor
 * Method:    executeNative
 * Signature: (java/lang/String;java/lang/String;Z;java/util/List;)I
 */
JNIEXPORT int JNICALL Java_org_mitre_mpf_frameextractor_FrameExtractor_executeNative
(JNIEnv *env, jobject frameExtractorInstance, jstring video, jstring destinationPath, jboolean croppingFlag, jobject paths)
{
    JniHelper jni(env);

    try {
        // Methods to get frame numbers, track ids, and detections.
        jclass clzFrameExtractor = jni.GetObjectClass(frameExtractorInstance);
        jmethodID clzFrameExtractor_fnGetFrames = jni.GetMethodID(clzFrameExtractor,
                                                           "getFrameNumbers", "()Ljava/util/Set;");
        jmethodID clzFrameExtractor_fnGetTrackIndices = jni.GetMethodID(clzFrameExtractor,
                                                           "getTrackIndices", "(Ljava/lang/Integer;)Ljava/util/Set;");
        jmethodID clzFrameExtractor_fnGetDetection = jni.GetMethodID(clzFrameExtractor, "getDetection",
                                                           "(Ljava/lang/Integer;Ljava/lang/Integer;)Lorg/mitre/mpf/interop/JsonDetectionOutputObject;");
        jmethodID clzFrameExtractor_fnMakeFilename = jni.GetMethodID(clzFrameExtractor,
                "makeFilename", "(Ljava/lang/String;II)Ljava/lang/String;");

        // Get the List class and method to add to the paths output list.
        jclass clzList = jni.FindClass("java/util/List");
        jmethodID clzList_fnAdd = jni.GetMethodID(clzList, "add", "(Ljava/lang/Object;)Z");

        // Get the iterator class and methods.
        jclass clzIterator = jni.FindClass("java/util/Iterator");
        jmethodID clzIterator_fnNext = jni.GetMethodID(clzIterator, "next", "()Ljava/lang/Object;");
        jmethodID clzIterator_fnHasNext = jni.GetMethodID(clzIterator, "hasNext", "()Z");

        // Get Set class and methods, for accessing the frame and track keySets.
        jclass clzSet = jni.FindClass("java/util/TreeSet");
        jmethodID clzSet_fnIterator = jni.GetMethodID(clzSet, "iterator", "()Ljava/util/Iterator;");

        // Get the Map class and method to get the rotation property from the detection properties map.
        jclass clzMap = jni.FindClass("java/util/TreeMap");
        jmethodID clzMap_fnGet = jni.GetMethodID(clzMap, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");

        // Get the FrameExtractionResult class and its constructor.
        jclass clzExtractionResult = jni.FindClass("org/mitre/mpf/frameextractor/FrameExtractionResult");
        jmethodID clzExtractionResult_fnConstruct = jni.GetMethodID(clzExtractionResult, "<init>", "(IILjava/lang/String;)V");

        // Get the Integer class and method to convert to jint.
        jclass clzInteger = jni.FindClass("java/lang/Integer");
        jmethodID clzInteger_fnIntValue = jni.GetMethodID(clzInteger, "intValue", "()I");

        // Get the JsonDetectionOutputObject methods.
        jclass clzJsonDetectionOutputObject = jni.FindClass("org/mitre/mpf/interop/JsonDetectionOutputObject");
        jmethodID clzJsonDetectionOutputObject_fnGetOffsetFrame = jni.GetMethodID(clzJsonDetectionOutputObject,
                "getOffsetFrame", "()I");
        jmethodID clzJsonDetectionOutputObject_fnGetX = jni.GetMethodID(clzJsonDetectionOutputObject, "getX", "()I");
        jmethodID clzJsonDetectionOutputObject_fnGetY = jni.GetMethodID(clzJsonDetectionOutputObject, "getY", "()I");
        jmethodID clzJsonDetectionOutputObject_fnGetHeight = jni.GetMethodID(clzJsonDetectionOutputObject, "getHeight", "()I");
        jmethodID clzJsonDetectionOutputObject_fnGetWidth = jni.GetMethodID(clzJsonDetectionOutputObject, "getWidth", "()I");
        jmethodID clzJsonDetectionOutputObject_fnGetProperties = jni.GetMethodID(clzJsonDetectionOutputObject,
                "getDetectionProperties", "()Ljava/util/SortedMap;");

        // Begin processing: open the media, and then process one frame at a time,
        // completing all extractions before going to the next frame.
        std::string mediaPath = jni.ToStdString(video);

        MPFVideoCapture src(mediaPath);
        if (!src.IsOpened()) {
            throw std::runtime_error("Unable to open input media file: " + mediaPath);
        }

        // Get the set of frames to be extracted and an iterator for it.
        jobject frameNumberSet = jni.CallObjectMethod(frameExtractorInstance, clzFrameExtractor_fnGetFrames);
        jobject frameIterator = jni.CallObjectMethod(frameNumberSet, clzSet_fnIterator);

        // Iterate over the frames in the set
        while (jni.CallBooleanMethod(frameIterator, clzIterator_fnHasNext) == JNI_TRUE) {
            // Get the frame number and read it.
            jobject thisFrameNumObj = jni.CallObjectMethod(frameIterator, clzIterator_fnNext);
            jint thisFrameNum = jni.CallIntMethod(thisFrameNumObj, clzInteger_fnIntValue);

            // Tell OpenCV to go to this frame next...
            src.SetFramePosition((int)thisFrameNum);

            // Get the frame...
            Mat frame;
            src >> frame;

            // If that frame is empty, we've reached the end of the video.
            if (frame.empty()) { break; }

            jint thisTrack = 0;

            if (!croppingFlag) {
                // No cropping, so simply write the frame to the file and continue.
                jstring filename = (jstring) jni.CallObjectMethod(frameExtractorInstance,
                        clzFrameExtractor_fnMakeFilename,
                        destinationPath,
                        thisTrack,
                        thisFrameNum);

                if (filename != nullptr) {
                    std::string destFile = jni.ToStdString(filename);
                    imwrite(destFile, frame);
                    jobject result = jni.CallConstructorMethod(clzExtractionResult, clzExtractionResult_fnConstruct,
                                                               thisFrameNum, thisTrack, filename);
                    jni.CallObjectMethod(paths, clzList_fnAdd, result);
                }
            }
            else {
                jobject trackIndexSet = jni.CallObjectMethod(frameExtractorInstance, clzFrameExtractor_fnGetTrackIndices, thisFrameNumObj);
                jobject trackIterator = jni.CallObjectMethod(trackIndexSet, clzSet_fnIterator);

                // For each track, perform the extraction for the associated detection object.
                while (jni.CallBooleanMethod(trackIterator, clzIterator_fnHasNext) == JNI_TRUE) {
                    // Get the detection associated with this track
                    jobject thisTrackObj = jni.CallObjectMethod(trackIterator, clzIterator_fnNext);
                    jint thisTrack = jni.CallIntMethod(thisTrackObj, clzInteger_fnIntValue);
                    jobject detection = jni.CallObjectMethod(frameExtractorInstance, clzFrameExtractor_fnGetDetection,
                                                             thisFrameNumObj, thisTrackObj);

                    // Create the bounding box.
                    jint x = jni.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetX);
                    jint y = jni.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetY);
                    jint width = jni.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetWidth);
                    jint height = jni.CallIntMethod(detection, clzJsonDetectionOutputObject_fnGetHeight);
                    Rect detectionBox(x, y, width, height);

                    // Get the rotation property.
                    jobject properties = jni.CallObjectMethod(detection, clzJsonDetectionOutputObject_fnGetProperties);
                    std::string rotationPropName("ROTATION");
                    jstring jPropName = jni.ToJString(rotationPropName);
                    jstring jPropValue = (jstring)jni.CallObjectMethod(properties, clzMap_fnGet, jPropName);
                    double rotation = 0.0;
                    if (jPropValue != nullptr) {
                        std::string rotationPropValue = jni.ToStdString(jPropValue);
                        rotation = std::stod(rotationPropValue);
                    }

                    Mat transformFrame = frame.clone();

                    // Create the transformation for this frame and apply it.
                    FeedForwardExactRegionAffineTransformer transformer(
                        { std::make_tuple(detectionBox, rotation, false) },
                        IFrameTransformer::Ptr(new NoOpFrameTransformer(transformFrame.size())));

                    transformer.TransformFrame(transformFrame, 0);

                    jstring filename = (jstring) jni.CallObjectMethod(frameExtractorInstance,
                                                                      clzFrameExtractor_fnMakeFilename,
                                                                      destinationPath,
                                                                      thisTrack,
                                                                      thisFrameNum);

                    if (filename != nullptr) {
                        std::string destFile = jni.ToStdString(filename);
                        imwrite(destFile, transformFrame);
                        jobject result = jni.CallConstructorMethod(clzExtractionResult, clzExtractionResult_fnConstruct,
                                                                   thisFrameNum, thisTrack, filename);
                        jni.CallObjectMethod(paths, clzList_fnAdd, result);
                    }
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
