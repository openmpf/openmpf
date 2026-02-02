/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2026 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2026 The MITRE Corporation                                       *
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

#include <jni.h>

#include "JniHelper.h"
#include "PtsUtil.h"


extern "C" JNIEXPORT jobject JNICALL Java_org_mitre_mpf_pts_PtsExtractor_getPtsNative(
        JNIEnv *env, jclass, jstring videoPath) {
    JniHelper jni(env);
    try {
        auto ptsResult = extractPts(jni.ToStdString(videoPath).c_str());

        auto* ptsArray = jni.ToJLongArray(ptsResult.values.size(), ptsResult.values.data());

        auto* clzPtsResult = jni.FindClass("org/mitre/mpf/pts/PtsResult");
        auto* clzPtsResult_fnConstruct = jni.GetMethodID(clzPtsResult, "<init>", "([JZ)V");
        return jni.CallConstructorMethod(
            clzPtsResult, clzPtsResult_fnConstruct, ptsArray, ptsResult.estimated);
    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
        return nullptr;
    }
    catch (...) {
        jni.ReportCppException();
        return nullptr;
    }
}
