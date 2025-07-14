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
#include <libheif/heif_cxx.h>
#include <libheif/heifio/encoder_png.h>

#include "JniHelper.h"

extern "C" {

JNIEXPORT void JNICALL Java_org_mitre_mpf_heif_HeifConverter_convertNative (
        JNIEnv *env, jclass clz, jstring inputFile, jstring outputFile) {

    JniHelper jni(env);

    class LibHeifInitializer {
        public:
        LibHeifInitializer() { heif_init(nullptr); }
        ~LibHeifInitializer() { heif_deinit(); }
    };

    try {
        LibHeifInitializer initializer;
        heif::Context ctx;
        ctx.read_from_file(jni.ToStdString(inputFile));

        std::unique_ptr<PngEncoder> encoder = std::make_unique<PngEncoder>();

        heif::ImageHandle handle = ctx.get_primary_image_handle();
        heif::ImageHandle png_img = ctx.encode_image(handle, encoder);
        ctx.write_to_file(jni.ToStdString(outputFile));
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

