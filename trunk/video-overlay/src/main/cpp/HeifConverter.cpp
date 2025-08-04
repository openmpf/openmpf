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
#include "JniHelper.h"
#include <libheif/heifio/encoder.h>
#include <libheif/heifio/encoder_png.h>
#include <libheif/heif_cxx.h>

class LibHeifInitializer {
    public:
    LibHeifInitializer() { heif_init(nullptr); }
    ~LibHeifInitializer() { heif_deinit(); }
};

// This class is here to ensure that the heif_image allocated in
// heif_decode_image() is freed properly.
class HeifImageHolder {
    public:
    HeifImageHolder(heif_image *image) : m_image(image) {}
    ~HeifImageHolder() {heif_image_release(m_image);}
    heif_image* m_image;
};

extern "C" {

JNIEXPORT void JNICALL Java_org_mitre_mpf_heif_HeifConverter_convertNative (
        JNIEnv *env, jclass clz, jstring inputFile, jstring outputFile) {

    JniHelper jni(env);

    try {
        LibHeifInitializer initializer;
        heif::Context ctx;
        ctx.read_from_file(jni.ToStdString(inputFile));
        heif::ImageHandle handle = ctx.get_primary_image_handle();
        HeifImageHolder image(nullptr);
        struct heif_error err = heif_decode_image(handle.get_raw_image_handle(), &(image.m_image),
                                                  heif_colorspace_RGB,
                                                  heif_chroma_interleaved_RGB,
                                                  nullptr);
        if (err.code != 0) {
            throw std::runtime_error(std::string("Could not decode HEIF/AVIF image: ") + std::string(err.message));
        }

        PngEncoder encoder;
        bool success = encoder.Encode(handle.get_raw_image_handle(), image.m_image,
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

