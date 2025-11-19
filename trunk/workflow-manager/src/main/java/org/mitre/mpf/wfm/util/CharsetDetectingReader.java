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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

    public static CharsetDetectingReader from(InputStream inputStream) throws IOException {
        var markableStream = inputStream.markSupported()
                ? inputStream
                : new BufferedInputStream(inputStream);
        var charset = detectCharset(markableStream);
        var hasBom = removeBomIfPresent(markableStream, charset);
        return new CharsetDetectingReader(markableStream, charset, hasBom);
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

    public Charset getCharset() {
        return _charset;
    }

    public boolean hasByteOrderMark() {
        return _hasBom;
    }


    private static Charset detectCharset(InputStream markableStream) throws IOException {
        var matches = new CharsetDetector()
                // Prefer UTF-8 when input is compatible. This was necessary because for small
                // sequences of ASCII characters, it would sometimes report IBM500.
                .setDeclaredEncoding(StandardCharsets.UTF_8.name())
                .setText(markableStream)
                .detectAll();
        return Stream.of(matches)
                .map(cm -> Charset.availableCharsets().get(cm.getNormalizedName()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IOException("Could not determine charset."));
    }


    private static boolean removeBomIfPresent(
            InputStream markableStream,
            Charset charset) throws IOException {
        var bomBytes = CHARSET_BOM_BYTES.get(charset);
        if (bomBytes == null) {
            // This Charset does not use a BOM.
            return false;
        }

        // Mark the stream since we might not read a BOM.
        markableStream.mark(bomBytes.length);
        var fileBegin = markableStream.readNBytes(bomBytes.length);
        var hasBom = Arrays.equals(bomBytes, fileBegin);
        if (!hasBom) {
            // Since there was not a BOM, regular characters were removed from stream.
            // Those characters need to be put back in the stream.
            markableStream.reset();
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
