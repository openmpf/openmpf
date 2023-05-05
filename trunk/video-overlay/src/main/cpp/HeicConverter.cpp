/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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
#include <libheif/heif_cxx.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>

#include "JniHelper.h"

extern "C" {

JNIEXPORT void JNICALL Java_org_mitre_mpf_heic_HeicConverter_convertNative (
        JNIEnv *env, jclass clz, jstring inputFile, jstring outputFile) {

    JniHelper jni(env);
    try {
        heif::Context ctx;
        ctx.read_from_file(jni.ToStdString(inputFile));

        heif::ImageHandle handle = ctx.get_primary_image_handle();
        heif::Image img = handle.decode_image(heif_colorspace_RGB,
                                              heif_chroma_interleaved_RGB);

        int stride = cv::Mat::AUTO_STEP;
        uint8_t* data = img.get_plane(heif_channel_interleaved, &stride);
        int width = handle.get_width();
        int height = handle.get_height();

        cv::Mat cv_img(height, width, CV_8UC3, data, stride);
        cv::cvtColor(cv_img, cv_img, cv::COLOR_RGB2BGR);
        cv::imwrite(jni.ToStdString(outputFile), cv_img);
    }
    catch (const std::exception &e) {
        jni.ReportCppException(e.what());
    }
    catch (const heif::Error &e) {
        jni.ReportCppException("An error occurred in libheif: " + e.get_message());
    }
    catch (...) {
        jni.ReportCppException();
    }
}

}  // extern "C"

