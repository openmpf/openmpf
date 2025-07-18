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
#include "JniHelper.h"
//#include <libheif/heif.h>
#include <libheif/heifio/encoder.h>
#include <libheif/heifio/encoder_png.h>
#include <libheif/heif_cxx.h>

class LibHeifInitializer {
    public:
    LibHeifInitializer() { heif_init(nullptr); }
    ~LibHeifInitializer() { heif_deinit(); }
};

extern "C" {

JNIEXPORT void JNICALL Java_org_mitre_mpf_heif_HeifConverter_convertNative (
        JNIEnv *env, jclass clz, jstring inputFile, jstring outputFile) {

    JniHelper jni(env);

    try {
        LibHeifInitializer initializer;
        heif::Context ctx;
        struct heif_error err;
        ctx.read_from_file(jni.ToStdString(inputFile));

        std::unique_ptr<PngEncoder> encoder = std::make_unique<PngEncoder>();

        heif::ImageHandle handle = ctx.get_primary_image_handle();
        int has_alpha = heif_image_handle_has_alpha_channel(handle.get_raw_image_handle());
        int bit_depth = heif_image_handle_get_luma_bits_per_pixel(handle.get_raw_image_handle());
        if (bit_depth < 0) {
            throw std::runtime_error("HEIF/AVIF image has undefined bit-depth");
        }

        std::unique_ptr<heif_image*> image = std::make_unique<heif_image*>();
        err = heif_decode_image(handle.get_raw_image_handle(), image.get(),
                                encoder->colorspace(has_alpha),
                                encoder->chroma(has_alpha, bit_depth),
                                nullptr);
        if (err.code != 0) {
            throw std::runtime_error(std::string("Could not decode HEIF/AVIF image: ") + std::string(err.message));
        }
        bool success = encoder->Encode(handle.get_raw_image_handle(), *(image.get()),
                                       jni.ToStdString(outputFile));
        if (!success) {
            throw std::runtime_error("Could not encode HEIF/AVIF image to PNG image");
        }

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

