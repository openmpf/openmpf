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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaSelector;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.CharsetDetectingReader;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Streams;


@Service
public class CsvColSelectorService {

    private static final String CSV_SELECTORS_ARE_INDICES = "CSV_SELECTORS_ARE_INDICES";

    private static final String CSV_FIRST_ROW_IS_DATA = "CSV_FIRST_ROW_IS_DATA";

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    @Inject
    CsvColSelectorService(AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }


    public record ExtractionResult(String value, MediaSelector selector) {
    }


    public Set<ExtractionResult> extractSelections(BatchJob job, Media media, int taskIdx, int actionIdx) {
        checkSelectorTypes(media);
        var action = job.getPipelineElements().getAction(taskIdx, actionIdx);

        try (var csvParser = openCsvParser(media.getProcessingPath())) {
            var parsedSelectors = parseSelectors(job, media, action, csvParser);

            var firstRow = parsedSelectors.stream()
                    .flatMap(ps -> ps.extractHeaders(csvParser.getHeaderNames()));

            var remainingRows = csvParser.stream()
                    .flatMap(r -> getRowExtractions(r, parsedSelectors));

            return Stream.concat(firstRow, remainingRows)
                    .collect(ImmutableSet.toImmutableSet());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    public void createOutputDocument(
            BatchJob job,
            Media media,
            Action action,
            OutputStream outputStream,
            BiFunction<UUID, String, Optional<String>> updater) {
        try (var reader = CharsetDetectingReader.from(media.getProcessingPath());
                var csvParser = openCsvParser(reader);
                var csvPrinter = openCsvPrinter(outputStream, reader)) {

            var parsedSelectors = createColToSelectorMultimap(job, media, action, csvParser);

            var newHeaders = Streams.mapWithIndex(
                    csvParser.getHeaderNames().stream(),
                    (h, i) -> getUpdatedHeader(h, (int) i, parsedSelectors, updater));
            csvPrinter.printRecord(newHeaders);

            for (var row : csvParser) {
                var updated = Streams.mapWithIndex(
                        row.stream(),
                        (c, i) -> getUpdatedCell(c, parsedSelectors.get((int) i), updater));
                csvPrinter.printRecord(updated);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private static void checkSelectorTypes(Media media) {
        boolean allCorrectType = media.getMediaSelectors()
            .stream()
            .allMatch(ms -> ms.type() == MediaSelectorType.CSV_COLS);
        if (!allCorrectType) {
            throw new IllegalArgumentException(
                    "Only the CSV_COLS media selector type is supported by this class.");
        }
    }

    private static CSVParser openCsvParser(Path path) throws IOException {
        return openCsvParser(CharsetDetectingReader.from(path));
    }


    private static final CSVFormat INPUT_CSV_FORMAT = CSVFormat.DEFAULT
            .builder()
            // Configure CSV parser to consider the first row headers.
            .setHeader()
            .setAllowMissingColumnNames(true)
            .setIgnoreEmptyLines(false)
            .build();

    private static CSVParser openCsvParser(CharsetDetectingReader reader) throws IOException {
        return INPUT_CSV_FORMAT.parse(reader);
    }


    private List<ParsedSelector> parseSelectors(
            BatchJob job,
            Media media,
            Action action,
            CSVParser csvParser) {
        var headerNamesToIndices = getHeadersToIndicesMultimap(csvParser);
        return media.getMediaSelectors()
                .stream()
                .map(ms -> parseSelector(job, media, action, ms, headerNamesToIndices))
                .toList();
    }


    private static SetMultimap<String, Integer> getHeadersToIndicesMultimap(CSVParser csvParser) {
        var resultBuilder = ImmutableSetMultimap.<String, Integer>builder();
        var iter = csvParser.getHeaderNames().listIterator();
        while (iter.hasNext()) {
            int idx = iter.nextIndex();
            var headerName = iter.next().strip();
            resultBuilder.put(headerName, idx);
        }
        return resultBuilder.build();
    }


    private ParsedSelector parseSelector(
            BatchJob job,
            Media media,
            Action action,
            MediaSelector mediaSelector,
            Multimap<String, Integer> headers) {

        ImmutableSet<Integer> colIdxs;
        if (selectorsAreIndices(job, media, action, mediaSelector)) {
            colIdxs = splitSelector(mediaSelector)
                .stream()
                .map(s -> Integer.parseInt(s.strip()))
                .collect(ImmutableSet.toImmutableSet());
        }
        else {
            colIdxs = splitSelector(mediaSelector)
                .stream()
                .flatMap(c -> getHeaderIndices(c, headers).stream())
                .collect(ImmutableSet.toImmutableSet());
        }

        var firstRowIsData = firstRowIsData(job, media, action, mediaSelector);
        return new ParsedSelector(colIdxs, firstRowIsData, mediaSelector);
    }


    private boolean selectorsAreIndices(
            BatchJob job,
            Media media,
            Action action,
            MediaSelector mediaSelector) {
        var strVal = _aggregateJobPropertiesUtil.getValue(
                CSV_SELECTORS_ARE_INDICES, job, media, action, mediaSelector);
        return Boolean.parseBoolean(strVal);
    }


    private static List<String> splitSelector(MediaSelector selector) {
        try (var csv = CSVParser.parse(selector.expression(), INPUT_CSV_FORMAT)) {
            return csv.getHeaderNames();
        }
        catch (IOException e) {
            throw new WfmProcessingException(
                    "Failed to parse selector with expression \"%s\" due to: %s"
                    .formatted(selector.expression(), e), e);
        }
    }

    private static Collection<Integer> getHeaderIndices(
            String headerName,
            Multimap<String, Integer> headers) {
        var result = headers.get(headerName.strip());
        if (result.isEmpty()) {
            throw new WfmProcessingException(
                "The CSV file does not have a header named \"%s\".".formatted(headerName));
        }
        return result;
    }

    private boolean firstRowIsData(
            BatchJob job,
            Media media,
            Action action,
            MediaSelector mediaSelector) {
        var strVal = _aggregateJobPropertiesUtil.getValue(
                CSV_FIRST_ROW_IS_DATA, job, media, action, mediaSelector);
        return Boolean.parseBoolean(strVal);
    }

    private static Stream<ExtractionResult> getRowExtractions(
            CSVRecord row,
            List<ParsedSelector> parsedSelectors) {
        return parsedSelectors.stream().flatMap(ps -> ps.extractRow(row));
    }


    private static CSVPrinter openCsvPrinter(OutputStream outStream, CharsetDetectingReader reader)
            throws IOException {
        var writer = new BufferedWriter(new OutputStreamWriter(outStream, StandardCharsets.UTF_8));
        try {
            if (reader.hasByteOrderMark() && reader.getCharset().equals(StandardCharsets.UTF_8)) {
                writer.write(CharsetDetectingReader.BYTE_ORDER_MARK);
            }
            return CSVFormat.DEFAULT.print(writer);
        }
        catch (Exception e) {
            writer.close();
            throw e;
        }
    }

    private ListMultimap<Integer, ParsedSelector> createColToSelectorMultimap(
            BatchJob job,
            Media media,
            Action action,
            CSVParser csvParser) {
        var resultBuilder = ImmutableListMultimap.<Integer, ParsedSelector>builder();
        var parsedSelectors = parseSelectors(job, media, action, csvParser);
        for (var selector : parsedSelectors) {
            for (var col : selector.cols) {
                resultBuilder.put(col, selector);
            }
        }
        return resultBuilder.build();
    }


    private static String getUpdatedHeader(
            String existingHeader,
            int index,
            Multimap<Integer, ParsedSelector> selectors,
            BiFunction<UUID, String, Optional<String>> updater) {
        if (existingHeader == null || existingHeader.isEmpty()) {
            return "";
        }
        return selectors.get(index)
                .stream()
                .filter(ps -> ps.firstRowIsData)
                .flatMap(ps -> updater.apply(ps.id(), existingHeader).stream())
                .findFirst()
                .orElse(existingHeader);
    }


    private static String getUpdatedCell(
            String existingContent,
            Collection<ParsedSelector> selectors,
            BiFunction<UUID, String, Optional<String>> updater) {
        if (existingContent == null || existingContent.isEmpty()) {
            return "";
        }
        return selectors
                .stream()
                .map(ps -> updater.apply(ps.id(), existingContent))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(existingContent);
    }


    private record ParsedSelector(
        ImmutableSet<Integer> cols,
        boolean firstRowIsData,
        MediaSelector selector) {

        public UUID id() {
            return selector.id();
        }

        public Stream<ExtractionResult> extractHeaders(List<String> headerNames) {
            return firstRowIsData
                    ? extract(headerNames::get, headerNames.size())
                    : Stream.empty();
        }

        public Stream<ExtractionResult> extractRow(CSVRecord row) {
            return extract(row::get, row.size());
        }

        private Stream<ExtractionResult> extract(IntFunction<String> extractor, int size) {
            return cols
                .stream()
                .filter(i -> i < size)
                .map(extractor::apply)
                .filter(s -> s != null && !s.isEmpty())
                .map(s -> new ExtractionResult(s, selector));
        }
    }
}
