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

package org.mitre.mpf.wfm.service;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.persistent.MediaSelector;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.CharsetDetectingReader;

public class TestCsvColSelectorService {

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private final CsvColSelectorService _csvService = new CsvColSelectorService();


    @Test
    public void testExcelCsv() {
        var path = TestUtil.findFilePath("/excel-test.csv");
        assertThat(path)
            .content(StandardCharsets.UTF_8)
            .startsWith(CharsetDetectingReader.BYTE_ORDER_MARK);

        var mediaSelector = createMediaSelector("2,1");
        var media = createTestMedia(path, mediaSelector);

        var updatedStrings = Map.of(
            "你好吗？", "zh - Hello, how are you?",
            "你叫什么名字？", "zh - What is your name?",
            "你今年多大？", "zh - How old are you?",

            "¿Hola, cómo estás?", "spa - Hello, how are you?",
            "¿Cómo te llamas?", "spa - What is your name?",
            "¿Cuántos años tiene?", "spa - How old are you?"
        );
        assertExtractionsEqualTo(media, updatedStrings.keySet());

        var resultBytes = createOutputDocument(media, updatedStrings);
        assertThat(resultBytes)
            .asString(StandardCharsets.UTF_8)
            .startsWith(CharsetDetectingReader.BYTE_ORDER_MARK);

        var bomLen = CharsetDetectingReader.BYTE_ORDER_MARK.getBytes(StandardCharsets.UTF_8).length;
        var resultNoBom = Arrays.copyOfRange(resultBytes, bomLen, resultBytes.length);

        String[][] expectedOutput = {
            {"Hello, how are you?", "zh - Hello, how are you?", "spa - Hello, how are you?"},
            {"What is your name?", "zh - What is your name?", "spa - What is your name?"},
            {"How old are you?", "zh - How old are you?", "spa - How old are you?"}
        };
        assertOutputMatches(resultNoBom, expectedOutput);
    }


    @Test
    public void canHandleEmptyLines() {
        var csvContent = """
            1,2,3

            4,5,6
            """;

        var mediaSelector = createMediaSelector("0,1");
        var media = createTestMedia(csvContent, mediaSelector);

        var updatedStrings = Map.of(
            "1", "I",
            "2", "II",
            "4", "IV",
            "5", "V");
        assertExtractionsEqualTo(media, updatedStrings.keySet());

        var resultBytes = createOutputDocument(media, updatedStrings);
        assertThat(resultBytes)
            .asString(StandardCharsets.UTF_8)
            .doesNotStartWith(CharsetDetectingReader.BYTE_ORDER_MARK);

        String[][] expectedOutput = {
            {"I", "II", "3"},
            {""},
            {"IV", "V", "6"}
        };
        assertOutputMatches(resultBytes, expectedOutput);
    }


    @Test
    public void handlesDuplicateHeaders() {
        var csvContent = """
            a,b,c,c,b
            1,2,3,4,5
            """;
        var mediaSelector = createMediaSelector("a,b,1,2");
        var media = createTestMedia(csvContent, mediaSelector);

        var updatedStrings = Map.of(
            "1", "I",
            "2", "II",
            "3", "III",
            "5", "V");
        assertExtractionsEqualTo(media, updatedStrings.keySet());

        var resultBytes = createOutputDocument(media, updatedStrings);

        String[][] expectedOutput = {
            {"a", "b", "c", "c", "b"},
            {"I", "II", "III", "4", "V"}
        };
        assertOutputMatches(resultBytes, expectedOutput);
    }


    @Test
    public void handlesRowsWithDifferentLengths() {
        var csvContent = """
            a,b,c
            1
            2,3
            4,5,6
            7,8,9,10""";

        var mediaSelector = createMediaSelector("b,c");
        var media = createTestMedia(csvContent, mediaSelector);
        var updatedStrings = Map.of(
            "3", "III",
            "5", "V",
            "6", "VI",
            "8", "VIII",
            "9", "IV");
        assertExtractionsEqualTo(media, updatedStrings.keySet());

        var resultBytes = createOutputDocument(media, updatedStrings);

        String[][] expectedOutput = {
            {"a", "b", "c"},
            {"1"},
            {"2", "III"},
            {"4", "V", "VI"},
            {"7", "VIII", "IV", "10"}
        };

        assertOutputMatches(resultBytes, expectedOutput);
    }

    @Test
    public void testMultipleSelectors() {
        var csvContent = """
            a,b,c,d
            1,2,3,4
            5,6,7,8
            """;
        var nameSelector = createMediaSelector("a,c");
        var indexSelector = createMediaSelector("0,1");

        var media = createTestMedia(csvContent, nameSelector, indexSelector);

        var nameSelectorUpdates = Map.of(
            "1", "I",
            "5", "V",

            "3", "III",
            "7", "VII"
        );

        var indexSelectorUpdates = Map.of(
            "a", "new a",
            "1", "I",
            "5", "V",

            "b", "new b",
            "2", "II",
            "6", "VI"
        );

        var extractions = _csvService.extractSelections(media);
        assertThat(extractions)
                .hasSize(nameSelectorUpdates.size() + indexSelectorUpdates.size());
        for (var extraction : extractions) {
            assertThat(extraction.selector().id())
                .isIn(nameSelector.id(), indexSelector.id());

            if (extraction.selector().id().equals(nameSelector.id())) {
                assertThat(extraction.value())
                    .isIn(nameSelectorUpdates.keySet());
            }
            else {
                assertThat(extraction.value())
                    .isIn(indexSelectorUpdates.keySet());
            }
        }

        BiFunction<UUID, String, Optional<String>> updater = (id, input) -> {
            assertThat(id)
                .isIn(nameSelector.id(), indexSelector.id());
            var updates = id.equals(nameSelector.id())
                ? nameSelectorUpdates
                : indexSelectorUpdates;
            assertThat(input)
                .isIn(updates.keySet());

            return Optional.of(updates.get(input));
        };

        var resultBytes = createOutputDocument(media, updater);

        String[][] expectedOutput = {
            {"new a", "new b", "c", "d"},
            {"I", "II", "III", "4"},
            {"V", "VI", "VII", "8"}
        };
        assertOutputMatches(resultBytes, expectedOutput);
    }


    private static final String HEADER_TEST_CSV = """
            a,b,c
            1,2,3
            """;

    @Test
    public void numericSelectorsAssumesNoHeaders() {
        assertExtractionsEqualTo(
                HEADER_TEST_CSV, "0,1",
                "a", "b", "1", "2");
    }

    @Test
    public void nonNumericSelectorsAssumesHeadersPresent() {
        assertExtractionsEqualTo(
                HEADER_TEST_CSV, "a,b",
                "1", "2");
    }

    @Test
    public void mixedSelectorsAssumesHeadersPresent() {
        assertExtractionsEqualTo(
                HEADER_TEST_CSV, "a,1",
                "1", "2");
    }

    @Test
    public void handlesDuplicateSelectors() {
        assertExtractionsEqualTo(
                HEADER_TEST_CSV, "a,0,a,0",
                "1");
    }


    private void assertOutputMatches(byte[] outputCsvBytes, String[][] expectedOutput) {
        var csvRecords = parseCsv(outputCsvBytes).getRecords();
        assertThat(csvRecords)
            .hasSameSizeAs(expectedOutput);

        var csvIter = csvRecords.listIterator();
        while (csvIter.hasNext()) {
            var idx = csvIter.nextIndex();
            var csvRecord = csvIter.next();
            assertThat(csvRecord.values())
                .isEqualTo(expectedOutput[idx]);
        }
    }

    private void assertExtractionsEqualTo(
            String csvContent,
            String selectorExpr,
            String... expectedExtractions) {
        var mediaSelector = createMediaSelector(selectorExpr);
        var media = createTestMedia(csvContent, mediaSelector);
        assertExtractionsEqualTo(media, Arrays.asList(expectedExtractions));
    }


    private void assertExtractionsEqualTo(
            Media media,
            Iterable<String> expectedExtractions) {
        var selections = _csvService.extractSelections(media);
        assertThat(selections)
            .extracting(er -> er.value())
            .containsExactlyInAnyOrderElementsOf(expectedExtractions);

        var selectorIds = media.getMediaSelectors()
            .stream()
            .map(MediaSelector::id)
            .collect(toSet());

        assertThat(selections)
                .extracting(er -> er.selector().id())
                .allMatch(selectorIds::contains);
    }


    private byte[] createOutputDocument(
            Media media, BiFunction<UUID, String, Optional<String>> updater) {
        var os = new ByteArrayOutputStream();
        _csvService.createOutputDocument(media, os, updater);
        return os.toByteArray();
    }

    private byte[] createOutputDocument(Media media, Map<String, String> updatedStrings) {
        assertThat(media.getMediaSelectors()).hasSize(1);
        var selector = media.getMediaSelectors().get(0);
        var updater = createUpdater(updatedStrings, selector);
        return createOutputDocument(media, updater);
    }

    private static MediaImpl createTestMedia(Path path, MediaSelector... selectors) {
        return new MediaImpl(
            1,
            MediaUri.create("file:///fake"),
            UriScheme.FILE,
            path,
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            Arrays.asList(selectors),
            "mediaSelectorsOutputAction",
            null);
    }

    private MediaImpl createTestMedia(String mediaContent, MediaSelector... selectors) {
        try {
            var path = _tempFolder.newFile().toPath();
            Files.writeString(path, mediaContent, StandardCharsets.UTF_8);
            return createTestMedia(path, selectors);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BiFunction<UUID, String, Optional<String>> createUpdater(
            Map<String, String> updatedStrings,
            MediaSelector selector) {
        return (id, inputString) -> {
            assertThat(id).isEqualTo(selector.id());
            assertThat(updatedStrings).containsKey(inputString);
            return Optional.of(updatedStrings.get(inputString));
        };
    }


    private static CSVParser parseCsv(byte[] csvBytes) {
        try {
            var is = new ByteArrayInputStream(csvBytes);
            return CSVParser.parse(is, StandardCharsets.UTF_8, CSVFormat.DEFAULT);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MediaSelector createMediaSelector(String expression) {
        return new MediaSelector(
                expression,
                MediaSelectorType.CSV_COLS,
                Map.of(),
                "resultProp");

    }
}
