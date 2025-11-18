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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.persistent.MediaSelector;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.CharsetDetectingReader;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class TestCsvColSelectorService extends MockitoTest.Strict {

    @Mock
    private AggregateJobPropertiesUtil _mockAggJobProps;

    @InjectMocks
    private CsvColSelectorService _csvService;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private static final long TEST_MEDIA_ID = 438;

    private final BatchJob _mockJob = mock(BatchJob.class);

    private final Action _testAction = new Action(null, null, null, null);

    @Before
    public void init() {
        var pipelineElements = mock(JobPipelineElements.class);
        when(pipelineElements.getAction(1, 0))
            .thenReturn(_testAction);
        when(_mockJob.getPipelineElements())
                .thenReturn(pipelineElements);
    }

    @Test
    public void testExcelCsvWithIndicesSelector() {
        var mediaSelector = createMediaSelector("2,1", true, true);
        runExcelCsvTest(mediaSelector);
    }

    @Test
    public void testExcelCsvWithNameSelector() {
        var mediaSelector = createMediaSelector("你好吗？,\"¿Hola, cómo estás?\"", false, true);
        runExcelCsvTest(mediaSelector);
    }


    private void runExcelCsvTest(MediaSelector mediaSelector) {
        var path = TestUtil.findFilePath("/excel-test.csv");
        assertThat(path)
            .content(StandardCharsets.UTF_8)
            .startsWith(CharsetDetectingReader.BYTE_ORDER_MARK);

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

        var mediaSelector = createMediaSelector("0,1", true, true);
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
        var mediaSelector = createMediaSelector("a,b", false, false);
        var media = createTestMedia(csvContent, mediaSelector);

        var updatedStrings = Map.of(
            "1", "I",
            "2", "II",
            "5", "V");
        assertExtractionsEqualTo(media, updatedStrings.keySet());

        var resultBytes = createOutputDocument(media, updatedStrings);

        String[][] expectedOutput = {
            {"a", "b", "c", "c", "b"},
            {"I", "II", "3", "4", "V"}
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

        var mediaSelector = createMediaSelector("b,c", false, false);
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
    public void handlesWhitespaceInHeader() {
        var csvContent = """
            a, a , a,a ,b,c
            1,2,3,4,5,6""";

        var selector = createMediaSelector("a, c  ", false, false);
        var media = createTestMedia(csvContent, selector);
        var updatedStrings = Map.of(
            "1", "I",
            "2", "II",
            "3", "III",
            "4", "IV",
            "6", "VI"
        );
        assertExtractionsEqualTo(media, updatedStrings.keySet());

        var resultBytes = createOutputDocument(media, updatedStrings);

        String[][] expectedOutput = {
            {"a", " a ", " a", "a ", "b", "c"},
            {"I", "II", "III", "IV", "5", "VI"}
        };
        assertOutputMatches(resultBytes, expectedOutput);
    }


    @Test
    public void handlesDuplicateContent() {
        var csvContent = """
            a,b,c,c,b
            1,1,1,1,1
            1,1,1,1,1
            """;
        var mediaSelector = createMediaSelector("a,b", false, false);
        var media = createTestMedia(csvContent, mediaSelector);

        var updatedStrings = Map.of(
            "1", "I");
        assertExtractionsEqualTo(media, updatedStrings.keySet());

        var resultBytes = createOutputDocument(media, updatedStrings);

        String[][] expectedOutput = {
            {"a", "b", "c", "c", "b"},
            {"I", "I", "1", "1", "I"},
            {"I", "I", "1", "1", "I"}
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
        var nameSelector = createMediaSelector("a,c", false, false);
        var indexSelector = createMediaSelector("0,1", true, true);

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

        var extractions = _csvService.extractSelections(_mockJob, media, 1, 0);
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
            a,b,c,1,2
            3,4,5,6,7
            """;

    @Test
    public void testNamedWithHeader() {
        assertExtractionsEqualTo(
            HEADER_TEST_CSV,
            createMediaSelector("a,2", false, false),
            "3", "7");
    }

    @Test
    public void testNamedWithFirstRowData() {
        assertExtractionsEqualTo(
            HEADER_TEST_CSV,
            createMediaSelector("a,2", false, true),
            "a", "2", "3", "7");
    }

    @Test
    public void testIndicesWithHeader() {
        assertExtractionsEqualTo(
            HEADER_TEST_CSV,
            createMediaSelector("2,3", true, false),
            "5", "6");
    }

    @Test
    public void testIndicesWithFirstRowData() {
        assertExtractionsEqualTo(
            HEADER_TEST_CSV,
            createMediaSelector("2,3", true, true),
            "c", "1", "5", "6");
    }

    @Test
    public void testNoHeaderMatch() {
        var media = createTestMedia(
            HEADER_TEST_CSV,
            createMediaSelector("c,d", false, false));

        assertThatExceptionOfType(WfmProcessingException.class)
            .isThrownBy(() -> _csvService.extractSelections(_mockJob, media, 1, 0))
            .withMessage("The CSV file does not have a header named \"d\".");
    }


    @Test
    public void handlesDuplicateSelectors() {
        assertExtractionsEqualTo(
            HEADER_TEST_CSV,
            createMediaSelector("2,3,2", true, false),
            "5", "6");

        assertExtractionsEqualTo(
            HEADER_TEST_CSV,
            createMediaSelector("2,c,2", false, false),
            "5", "7");
    }

    @Test
    public void handlesWhitespaceInSelector() {
        assertExtractionsEqualTo(
            HEADER_TEST_CSV,
            createMediaSelector("2,3 ,2", true, false),
            "5", "6");

        assertExtractionsEqualTo(
            HEADER_TEST_CSV,
            createMediaSelector("2 ,c,2", false, false),
            "5", "7");
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
            MediaSelector selector,
            String... expectedExtractions) {
        var media = createTestMedia(csvContent, selector);
        assertExtractionsEqualTo(media, Arrays.asList(expectedExtractions));
    }


    private void assertExtractionsEqualTo(
            Media media,
            Iterable<String> expectedExtractions) {
        var selections = _csvService.extractSelections(_mockJob, media, 1, 0);
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
        _csvService.createOutputDocument(_mockJob, media, _testAction, os, updater);
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
            TEST_MEDIA_ID,
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


    private MediaSelector createMediaSelector(
            String expression,
            boolean selectorsAreIndices,
            boolean firstRowIsData) {
        var selector = new MediaSelector(
                expression,
                MediaSelectorType.CSV_COLS,
                Map.of(),
                "resultProp");

        if (selectorsAreIndices) {
            lenient().when(_mockAggJobProps.getValue(
                    eq("CSV_SELECTORS_ARE_INDICES"),
                    eq(_mockJob),
                    argThat(m -> m.getId() == TEST_MEDIA_ID),
                    eq(_testAction),
                    eq(selector)))
                .thenReturn("true");
        }
        if (firstRowIsData) {
            lenient().when(_mockAggJobProps.getValue(
                    eq("CSV_FIRST_ROW_IS_DATA"),
                    eq(_mockJob),
                    argThat(m -> m.getId() == TEST_MEDIA_ID),
                    eq(_testAction),
                    eq(selector)))
                .thenReturn("true");
        }
        return selector;
    }
}
