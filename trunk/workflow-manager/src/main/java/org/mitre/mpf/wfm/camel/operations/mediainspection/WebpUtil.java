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

package org.mitre.mpf.wfm.camel.operations.mediainspection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebpUtil {
    private static final Logger LOG = LoggerFactory.getLogger(WebpUtil.class);

    private WebpUtil() {
    }

    // When WEBP files have extra data at the end of the file, libavcodec will cause the calling
    // program to crash. To avoid this issue we remove those extra bytes.
    // WEBP files are RIFF files with specific chunks. RIFF files include the length of the file.
    // We use that length to determine how long the file should be. Then, we create a copy of
    // the file with the extra data removed.
    public static Path fixLengthIfNeeded(Path imgPath, Path tempDir) throws IOException {
        try (var inChannel = FileChannel.open(imgPath, StandardOpenOption.READ)) {
            // Bytes 0 - 4 should be the ASCII values for RIFF
            var chunkIdBuf = readNBytes(inChannel, 4);
            validateRiffChunkId(chunkIdBuf);

            // Bytes 4-8 contain the length of the RIFF chunk.
            var chunkLengthBuf = readNBytes(inChannel, 4);
            long expectedFullFileSize = getFileSizeSpecifiedInRiffChunk(chunkLengthBuf);

            if (inChannel.size() == expectedFullFileSize) {
                return imgPath;
            }
            if (inChannel.size() < expectedFullFileSize) {
                throw new WfmProcessingException("""
                        The RIFF chunk length bytes said the file should contain %s bytes, \
                        but the file only contains %s bytes.""".formatted(
                        expectedFullFileSize, inChannel.size()));
            }

            var pathWithCorrectSize = tempDir.resolve(UUID.randomUUID() + ".webp");
            LOG.warn("""
                    The RIFF chunk length indicated the length of "{}" should be {} bytes, but \
                    it actually contains {} bytes. Copying the first {} bytes to "{}".""",
                    imgPath, expectedFullFileSize, inChannel.size(), expectedFullFileSize,
                    pathWithCorrectSize);
            copyFirstNBytes(inChannel, pathWithCorrectSize, expectedFullFileSize);
            return pathWithCorrectSize;
        }
    }

    private static void validateRiffChunkId(ByteBuffer chunkIdBuf) {
        if (chunkIdBuf.get(0) != 'R'
                || chunkIdBuf.get(1) != 'I'
                || chunkIdBuf.get(2) != 'F'
                || chunkIdBuf.get(3) != 'F') {
            throw new WfmProcessingException("""
                    Media is not a valid WEBP file because it does not start with the \
                    ASCII values for "RIFF".""");
        }
    }

    private static long getFileSizeSpecifiedInRiffChunk(ByteBuffer buffer) {
        // The chunk legnth is little endian and unsigned.
        int signedValue = buffer.order(ByteOrder.LITTLE_ENDIAN).getInt(0);
        long chunkLength = Integer.toUnsignedLong(signedValue);
        // RIFF requires overall file to have an even length. If the amount of actual data
        // in the file is odd, a padding byte must be added.
        long paddingLength = chunkLength % 2;
        // The chunk length does not include the 4 byte chunk identifier and the length field itself.
        return chunkLength + paddingLength + 4 + 4;
    }


    private static ByteBuffer readNBytes(FileChannel channel, int numBytesToRead)
            throws IOException {
        var buffer = ByteBuffer.allocate(numBytesToRead);
        while (buffer.hasRemaining() && channel.read(buffer) > 0);
        return buffer.flip();
    }


    private static void copyFirstNBytes(
            FileChannel srcChannel, Path destPath, long numBytesToWrite) throws IOException {
        try (var destChannel = FileChannel.open(
                destPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

            long numCopied = 0;
            while (numCopied < numBytesToWrite) {
                numCopied += srcChannel.transferTo(
                        numCopied, numBytesToWrite - numCopied, destChannel);
            }
        }
    }

}
