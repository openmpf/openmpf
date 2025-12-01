/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2025 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2025 The MITRE Corporation                                       *
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tika.parser.txt.CharsetDetector;

import com.google.common.collect.ImmutableMap;


public class CharsetDetectingReader extends InputStreamReader {

    // The String form of the BOM is the same for all UTF variants.
    public static final String BYTE_ORDER_MARK = "\uFEFF";

    private static final Map<Charset, byte[]> CHARSET_BOM_BYTES = createBomMap();

    private final Charset _charset;

    private final boolean _hasBom;

    private CharsetDetectingReader(InputStream inputStream, Charset charset, boolean hasBom) {
        super(inputStream, charset);
        _charset = charset;
        _hasBom = hasBom;
    }


    public static CharsetDetectingReader from(Path path) throws IOException {
        var inputStream = Files.newInputStream(path);
        try {
            return from(inputStream);
        }
        catch (Exception e) {
            inputStream.close();
            throw e;
        }
    }

    private static final int TIKA_DEFAULT_MARK_LENGTH = 12_000;

    public static CharsetDetectingReader from(InputStream inputStream) throws IOException {
        var markableStream = inputStream.markSupported()
                ? inputStream
                : new BufferedInputStream(inputStream);

        markableStream.mark(TIKA_DEFAULT_MARK_LENGTH);
        byte[] leadingBytes = markableStream.readNBytes(TIKA_DEFAULT_MARK_LENGTH);
        markableStream.reset();

        var charset = detectCharset(leadingBytes);
        var hasBom = removeBomIfPresent(leadingBytes, markableStream, charset);
        return new CharsetDetectingReader(markableStream, charset, hasBom);
    }


    public Charset getCharset() {
        return _charset;
    }

    public boolean hasByteOrderMark() {
        return _hasBom;
    }

    private static Charset detectCharset(byte[] bytes) throws IOException {
        if (isUtf8(bytes)) {
            return StandardCharsets.UTF_8;
        }
        else {
            return detectCharsetTika(bytes);
        }
    }

    private static boolean isUtf8(byte[] bytes) {
        var buf = ByteBuffer.wrap(bytes);
        while (buf.hasRemaining()) {
            byte b = buf.get();
            if (b == 0) {
                return false;
            }
            else if (Utf8ByteTypes.SINGLE_BYTE_CHAR.matches(b)) {
                continue;
            }

            int numTrailingBytes;
            if (Utf8ByteTypes.BEGIN_2_BYTE_CHAR.matches(b)) {
                numTrailingBytes = 1;
            }
            else if (Utf8ByteTypes.BEGIN_3_BYTE_CHAR.matches(b)) {
                numTrailingBytes = 2;
            }
            else if (Utf8ByteTypes.BEGIN_4_BYTE_CHAR.matches(b)) {
                numTrailingBytes = 3;
            }
            else {
                return false;
            }

            for (int i = 0; i < numTrailingBytes && buf.hasRemaining(); i++) {
                if (!Utf8ByteTypes.CONTINUATION.matches(buf.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    private enum Utf8ByteTypes {
        SINGLE_BYTE_CHAR(0),
        CONTINUATION(1),
        BEGIN_2_BYTE_CHAR(2),
        BEGIN_3_BYTE_CHAR(3),
        BEGIN_4_BYTE_CHAR(4);

        private final byte _mask;

        private final byte _cmpVal;

        Utf8ByteTypes(int numLeadingOnes) {
            _mask = (byte) (~0 << (7 - numLeadingOnes));
            _cmpVal = (byte) (_mask << 1);
        }

        public boolean matches(byte b) {
            return (b & _mask) == _cmpVal;
        }
    }


    public static Charset detectCharsetTika(byte[] bytes) throws IOException {
        var matches = new CharsetDetector()
                // Prefer UTF-8 when input is compatible. This was necessary because for small
                // sequences of ASCII characters, it would sometimes report IBM500.
                .setDeclaredEncoding(StandardCharsets.UTF_8.name())
                .setText(bytes)
                .detectAll();

        for (var match : matches) {
            try {
                return Charset.forName(match.getNormalizedName());
            }
            catch (UnsupportedCharsetException ignored) {
                // Try a lower confidence Charset
            }
        }
        throw new IOException("Could not determine file Charset.");
    }



    private static boolean removeBomIfPresent(
            byte[] leadingBytes,
            InputStream markableStream,
            Charset charset) throws IOException {
        var bomBytes = CHARSET_BOM_BYTES.get(charset);
        if (bomBytes == null) {
            // This Charset does not use a BOM.
            return false;
        }
        if (leadingBytes.length < bomBytes.length) {
            return false;
        }

        var hasBom = Arrays.equals(
                bomBytes, 0, bomBytes.length,
                leadingBytes, 0, bomBytes.length);
        if (hasBom) {
            markableStream.skipNBytes(bomBytes.length);
        }
        return hasBom;
    }

    private static Map<Charset, byte[]> createBomMap() {
        var standardCharsets = Stream.of(
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_16,
                StandardCharsets.UTF_16BE,
                StandardCharsets.UTF_16LE);

        var nonStandardCharsets = Stream.of("UTF-32", "UTF-32BE", "UTF-32LE")
                .map(Charset.availableCharsets()::get)
                .filter(Objects::nonNull);

        return Stream.concat(standardCharsets, nonStandardCharsets)
            .collect(ImmutableMap.toImmutableMap(Function.identity(), BYTE_ORDER_MARK::getBytes));
    }
}
