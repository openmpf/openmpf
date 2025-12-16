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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Test;


public class TestCharsetDetectingReader {

    @Test
    public void prefersUtf8() throws IOException {
        var inputString = "a1".repeat(80);
        var inputBytes = inputString.getBytes(StandardCharsets.UTF_8);
        var tikaResult = CharsetDetectingReader.detectCharsetTika(inputBytes);
        assertThat(tikaResult)
            .extracting(Charset::name)
            .isEqualTo("ISO-8859-2");

        assertDetectedCharset(inputString, StandardCharsets.UTF_8, false);
    }


    @Test
    public void handlesPartialCharAtEnd() {
        var baseMsg = "a".repeat(11_999);
        var inputString = baseMsg + "好";
        assertDetectedCharset(inputString, StandardCharsets.UTF_8, false);

        var inputBytes = inputString.getBytes(StandardCharsets.UTF_8);
        var decoded = new String(inputBytes, 0, 12_000, StandardCharsets.UTF_8);

        var errorChar = StandardCharsets.UTF_8.newDecoder().replacement();
        var expectedDecodeValue = baseMsg + errorChar;
        assertThat(decoded).isEqualTo(expectedDecodeValue);
    }

    @Test
    public void testShortString() {
        assertDetectedCharset("a", StandardCharsets.UTF_8, false);
    }

    @Test
    public void testUtf16WithOnlyAsciiChars() {
        var inputString = "Hello";
        assertDetectedCharset(inputString, StandardCharsets.UTF_16BE, false);
        assertDetectedCharset(inputString, StandardCharsets.UTF_16LE, false);
        assertDetectedCharset(inputString, StandardCharsets.UTF_16, StandardCharsets.UTF_16BE, true);
    }


    @Test
    public void testIso8859_1() {
        var inputString = "¿Hola, cómo estás?";
        assertDetectedCharset(inputString, StandardCharsets.ISO_8859_1, false);
    }


    @Test
    public void bomIsNotPartOfString() {
        var expectedOutput = "Hello";
        var inputString = CharsetDetectingReader.BYTE_ORDER_MARK + expectedOutput;
        var inputBytes = inputString.getBytes(StandardCharsets.UTF_8);
        var outputString = assertDetectedCharset(inputBytes, StandardCharsets.UTF_8, true);
        assertThat(outputString)
            .isEqualTo(expectedOutput);
    }


    @Test
    public void onlyFirstBomCharRemoved() {
        var expectedOutput = CharsetDetectingReader.BYTE_ORDER_MARK + "Hello";
        var inputString = CharsetDetectingReader.BYTE_ORDER_MARK + expectedOutput;
        var inputBytes = inputString.getBytes(StandardCharsets.UTF_8);
        var outputString = assertDetectedCharset(inputBytes, StandardCharsets.UTF_8, true);
        assertThat(outputString)
            .isEqualTo(expectedOutput);

    }

    @Test
    public void ensureAsciiDetectedAsUtf8() {
        var inputString = "Hello";
        assertDetectedCharset(
                inputString,
                StandardCharsets.US_ASCII,
                StandardCharsets.UTF_8, false);
    }


    private void assertDetectedCharset(
            String inputString, Charset expectedCharset, boolean expectBom) {
        assertDetectedCharset(inputString, expectedCharset, expectedCharset, expectBom);
    }


    private void assertDetectedCharset(
            String inputString,
            Charset inputCharset,
            Charset expectedCharset,
            boolean expectBom) {
        var bytes = inputString.getBytes(inputCharset);
        var outputString = assertDetectedCharset(bytes, expectedCharset, expectBom);
        assertThat(outputString).isEqualTo(inputString);
    }

    private String assertDetectedCharset(
            byte[] inputBytes,
            Charset expectedCharset,
            boolean expectBom) {
        try {
            var reader = CharsetDetectingReader.from(new ByteArrayInputStream(inputBytes));
            assertThat(reader.getCharset())
                    .isEqualTo(expectedCharset);
            assertThat(reader.hasByteOrderMark())
                    .isEqualTo(expectBom);

            var sw = new StringWriter();
            reader.transferTo(sw);
            return sw.toString();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
