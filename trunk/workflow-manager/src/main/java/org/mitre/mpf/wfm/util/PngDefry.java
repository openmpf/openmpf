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


package org.mitre.mpf.wfm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

public class PngDefry {
    private static final Logger LOG = LoggerFactory.getLogger(PngDefry.class);


    // From https://en.wikipedia.org/wiki/Portable_Network_Graphics#File_format
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

    private static final int NUM_BYTES_IN_CHUNK_LENGTH_FIELD = 4;

    private static final byte[] CGBI_CHUNK_TYPE = { 'C', 'g', 'B', 'I'};


    public static boolean isCrushed(Path imgPath) {
        try (var inputStream = Files.newInputStream(imgPath)) {
            var header = inputStream.readNBytes(PNG_HEADER.length);
            if (!Arrays.equals(header, PNG_HEADER)) {
                return false;
            }
            // Discard the field indicating the length of the first chunk.
            inputStream.skip(NUM_BYTES_IN_CHUNK_LENGTH_FIELD);

            // Crushed PNGs have CgBI as the first chunk.
            // Regular PNGs require IHDR to be the first chunk.
            var chunkType = inputStream.readNBytes(CGBI_CHUNK_TYPE.length);
            return Arrays.equals(chunkType, CGBI_CHUNK_TYPE);
        }
        catch (IOException e) {
            LOG.warn(String.format(
                    "Could not determine if %s is an Apple-optimized PNG due to: %s",
                    imgPath, e.getMessage()), e);
            return false;
        }
    }


    /**
     * Converts an Apple-optimized (crushed) PNG to a regular PNG
     * @param inputMedia Path to Apple-optimized (crushed) PNG
     * @param outputDir Directory to put converted file in to.
     * @return Path to the converted image.
     */
    public static Path defry(Path inputMedia, Path outputDir) throws IOException {
        try {
            var suffix = "_defried_" + UUID.randomUUID();
            int exitCode = new ProcessBuilder(
                    "pngdefry", "-o", outputDir.toString(), "-s", suffix, inputMedia.toString())
                        .inheritIO()
                        .start()
                        .waitFor();

            if (exitCode != 0) {
                throw new IllegalStateException("pngdefry failed with exit code: " + exitCode);
            }

            var inputMediaFileName = inputMedia.getFileName().toString();
            String outputFileName;
            if (inputMediaFileName.endsWith(".png")) {
                var inputFileNoExtension = inputMediaFileName.substring(
                        0, inputMediaFileName.length() - 4);
                outputFileName = inputFileNoExtension + suffix + ".png";
            }
            else {
                outputFileName = inputMediaFileName + suffix + ".png";
            }

            Path outputPath = outputDir.resolve(outputFileName);
            // pngdefry returns a success exit code no matter what, so the only way to detect
            // that it failed is to check if the defried image exists.
            if (Files.exists(outputPath)) {
                return outputPath;
            }
            else {
                throw new IllegalStateException("pngdefry failed to defry: " + inputMedia);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupt received while running pngdefry.", e);
        }
    }


    private PngDefry() {
    }
}
