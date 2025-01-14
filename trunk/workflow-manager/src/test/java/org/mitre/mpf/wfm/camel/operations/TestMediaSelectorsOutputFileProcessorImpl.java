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

package org.mitre.mpf.wfm.camel.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJobImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.persistent.MediaSelector;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.service.JsonPathEvaluator;
import org.mitre.mpf.wfm.service.JsonPathService;
import org.mitre.mpf.wfm.service.StorageException;
import org.mitre.mpf.wfm.service.StorageService;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;


public class TestMediaSelectorsOutputFileProcessorImpl extends MockitoTest.Strict {

    private static final long JOB_ID = 879;

    private static final long MEDIA_ID = 241;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @Mock
    private AggregateJobPropertiesUtil _mockAggJobProps;

    @Mock
    private StorageService _mockStorageService;

    @Mock
    private JsonPathService _mockJsonPathService;

    @InjectMocks
    private MediaSelectorsOutputFileProcessorImpl _mediaSelectorsOutputFileProcessor;

    private Path _mediaPath;

    private TestCaseBuilder _testBuilder = new TestCaseBuilder();


    @Before
    public void init() throws IOException {
        _mediaPath = _tempFolder.newFile().toPath();
    }


    @Test
    public void doesNothingWhenWrongTask() {
        var mediaSelector = new MediaSelector(
                "expr", MediaSelectorType.JSON_PATH, Map.of(), "result");
        when(_mockInProgressJobs.getJob(JOB_ID))
                .thenReturn(createTestJob(List.of(mediaSelector)));

        var exchange = TestUtil.createTestExchange();
        // The job is configured to create the media selectors output file in the second task.
        // Here, we invoke the processor for the first task.
        var trackCache = new TrackCache(JOB_ID, 0, _mockInProgressJobs);
        exchange.getIn().setBody(trackCache);
        exchange.getIn().setHeader(MpfHeaders.JOB_ID, JOB_ID);

        _mediaSelectorsOutputFileProcessor.process(exchange);

        verifyNoMoreInteractions(_mockInProgressJobs);
        verifyNoInteractions(_mockAggJobProps, _mockStorageService, _mockJsonPathService);
    }


    @Test
    public void testOutputUpdatedFileCreation() throws IOException, StorageException {
        var mappers = _testBuilder.addJsonSelector("expr1", "OUTPUT1")
                .addJsonSelector("expr2", "OUTPUT2")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", "OUTPUT_1_1")
                .addTrack(0, "INPUT_1_2", "OUTPUT1", "OUTPUT_1_2")
                .addTrack(1, "INPUT_2_1", "OUTPUT2", "OUTPUT_2_1")
                .getStringMappers();

        assertThat(mappers).hasSize(2);
        var mapper1 = mappers.get(0);
        assertMapped(mapper1, "INPUT_1_1", "OUTPUT_1_1");
        assertMapped(mapper1, "INPUT_1_2", "OUTPUT_1_2");
        assertNotMapped(mapper1, "INPUT_2_1");

        var mapper2 = mappers.get(1);
        assertNotMapped(mapper2, "INPUT_1_1", "INPUT_1_2");
        assertMapped(mapper2, "INPUT_2_1", "OUTPUT_2_1");
    }


    @Test
    public void testCopyWhenNoSelectorMatches() throws IOException, StorageException {
        var job = mock(BatchJob.class);
        when(_mockInProgressJobs.getJob(JOB_ID))
                .thenReturn(job);

        var media = mock(Media.class);
        when(media.getId())
                .thenReturn(MEDIA_ID);
        var mediaSelector = new MediaSelector(
                "expr", MediaSelectorType.JSON_PATH, Map.of(), "result");
        when(media.getMediaSelectors())
                .thenReturn(ImmutableList.of(mediaSelector));
        when(media.getProcessingPath())
                .thenReturn(_mediaPath);

        var fileContent = "<test file content>".getBytes();
        Files.write(_mediaPath, fileContent);

        var outputProcessorCaptor = ArgumentCaptor.forClass(StorageService.OutputProcessor.class);
        var uri = URI.create("file:///path/to/file");
        when(_mockStorageService.storeMediaSelectorsOutput(
                same(job), same(media), eq(MediaSelectorType.JSON_PATH),
                outputProcessorCaptor.capture()))
                .thenReturn(uri) ;

        _mediaSelectorsOutputFileProcessor.createNoMatchOutputDocument(JOB_ID, media);

        verify(_mockInProgressJobs)
                .setMediaSelectorsOutputUri(JOB_ID, MEDIA_ID, uri);

        var outputProcessor = outputProcessorCaptor.getValue();
        try (var out = new ByteArrayOutputStream()) {
            outputProcessor.process(out);
            assertThat(out.toByteArray()).isEqualTo(fileContent);
        }
    }


    private void assertMapped(UnaryOperator<String> mapper, String input, String expectedOutput) {
        assertThat(mapper.apply(input))
                .as("Check mapping of input: %s", input)
                .isEqualTo(expectedOutput);
    }

    private void assertNotMapped(UnaryOperator<String> mapper, String... inputs) {
        assertThat(inputs).isNotEmpty();
        for (var input : inputs) {
            var output = mapper.apply(input);
            assertThat(output)
                .withFailMessage("Expected %s to be unmapped, but it was mapped to %s",
                        input, output)
                .isEqualTo(input);
        }
    }


    @Test
    public void testLongestDuplicatePolicy() throws IOException, StorageException {
        when(_mockAggJobProps.getValue(eq(MpfConstants.MEDIA_SELECTORS_DUPLICATE_POLICY), notNull()))
            .thenReturn("LONGEST");
        var mappers = _testBuilder
                .addJsonSelector("expr1", "OUTPUT1")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", "OUTPUT_1_1")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", "OUTPUT_1_LONGER")
                .getStringMappers();

        var mapper = mappers.get(0);
        assertMapped(mapper, "INPUT_1_1", "OUTPUT_1_LONGER");
    }


    @Test
    public void testJoinDuplicatePolicy() throws IOException, StorageException {
        when(_mockAggJobProps.getValue(eq(MpfConstants.MEDIA_SELECTORS_DUPLICATE_POLICY), notNull()))
            .thenReturn("JOIN");
        var mappers = _testBuilder
                .addJsonSelector("expr1", "OUTPUT1")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", "OUTPUT_1_1")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", "OUTPUT_1_LONGER")
                .getStringMappers();

        assertThat(mappers)
            .singleElement()
            .satisfiesAnyOf(
                m -> assertMapped(m, "INPUT_1_1", "OUTPUT_1_1 | OUTPUT_1_LONGER"),
                m -> assertMapped(m, "INPUT_1_1", "OUTPUT_1_LONGER | OUTPUT_1_1")
            );
    }


    @Test
    public void errorDuplicatePolicyIsNotTriggeredWhenOutputIsSame() throws IOException, StorageException {
        when(_mockAggJobProps.getValue(eq(MpfConstants.MEDIA_SELECTORS_DUPLICATE_POLICY), notNull()))
            .thenReturn("ERROR");
        var mappers = _testBuilder
                .addJsonSelector("expr1", "OUTPUT1")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", "OUTPUT_1_1")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", "OUTPUT_1_1")
                .getStringMappers();
        var mapper = mappers.get(0);
        assertMapped(mapper, "INPUT_1_1", "OUTPUT_1_1");
    }


    @Test
    public void errorDuplicatePolicyIsNotTriggeredWhenOneOutputBlank() throws IOException, StorageException {
        when(_mockAggJobProps.getValue(eq(MpfConstants.MEDIA_SELECTORS_DUPLICATE_POLICY), notNull()))
            .thenReturn("ERROR");
        var mappers = _testBuilder
                .addJsonSelector("expr1", "OUTPUT1")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", "OUTPUT_1_1")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", " ")
                .getStringMappers();
        var mapper = mappers.get(0);
        assertMapped(mapper, "INPUT_1_1", "OUTPUT_1_1");
    }

    @Test
    public void errorDuplicatePolicyThrowsException() throws IOException, StorageException {
        when(_mockAggJobProps.getValue(eq(MpfConstants.MEDIA_SELECTORS_DUPLICATE_POLICY), notNull()))
            .thenReturn("ERROR");
        var mappers = _testBuilder
                .addJsonSelector("expr1", "OUTPUT1")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", "OUTPUT_1_1")
                .addTrack(0, "INPUT_1_1", "OUTPUT1", "OUTPUT_1_2")
                // The operation should fail, so no output should be stored.
                .doNotExpectOutputToBeStored()
                .getStringMappers();
        assertThat(mappers).isEmpty();

        verify(_mockInProgressJobs)
            .addError(eq(JOB_ID), eq(MEDIA_ID), eq(IssueCodes.OTHER), TestUtil.nonBlank());
    }


    @Test
    public void testDelimeter() throws IOException, StorageException {
        when(_mockAggJobProps.getValue(eq(MpfConstants.MEDIA_SELECTORS_DELIMETER), notNull()))
            .thenReturn("| Updated:");

        when(_mockAggJobProps.getValue(eq(MpfConstants.MEDIA_SELECTORS_DUPLICATE_POLICY), notNull()))
            .thenReturn("ERROR");

        var mappers = _testBuilder
                .addJsonSelector("expr1", "OUTPUT1")
                .addTrack(0, "INPUT_1", "OUTPUT1", "OUTPUT_1")
                .disableNoPresentCheck()
                .getStringMappers();

        assertThat(mappers).hasSize(1);
        assertMapped(mappers.get(0), "INPUT_1", "INPUT_1 | Updated: OUTPUT_1");
    }


    private class TestCaseBuilder {

        private List<MediaSelector> _mediaSelectors = new ArrayList<>();

        private List<Track> _tracks = new ArrayList<>();

        private boolean _shouldBeStored = true;

        private boolean _shouldCheckNotPresent = true;

        public TestCaseBuilder addJsonSelector(String expr, String outputProp) {
            var selector = new MediaSelector(
                    expr, MediaSelectorType.JSON_PATH, Map.of(), outputProp);
            _mediaSelectors.add(selector);
            return this;
        }


        public TestCaseBuilder addTrack(
                    int selectorIdx, String input, String outputProp, String outputValue) {
            var track = mock(Track.class);
            when(track.getSelectorId())
                    .thenReturn(Optional.of(_mediaSelectors.get(selectorIdx).id()));
            when(track.getSelectedInput())
                    .thenReturn(Optional.of(input));
            when(track.getTrackProperties())
                    .thenReturn(ImmutableSortedMap.of(outputProp, outputValue));
            _tracks.add(track);
            return this;
        }

        public TestCaseBuilder doNotExpectOutputToBeStored() {
            _shouldBeStored = false;
            return this;
        }

        public TestCaseBuilder disableNoPresentCheck() {
            _shouldCheckNotPresent = false;
            return this;
        }

        @SuppressWarnings("unchecked")
        public List<UnaryOperator<String>> getStringMappers() throws IOException, StorageException {
            var exchange = TestUtil.createTestExchange();
            var trackCache = new TrackCache(JOB_ID, 1, _mockInProgressJobs);
            exchange.getIn().setBody(trackCache);
            exchange.getIn().setHeader(MpfHeaders.JOB_ID, JOB_ID);

            var job = createTestJob(_mediaSelectors);
            when(_mockInProgressJobs.getJob(JOB_ID))
                .thenReturn(job);

            var mockEvaluator = mock(JsonPathEvaluator.class);
            when(_mockJsonPathService.load(_mediaPath))
                .thenReturn(mockEvaluator);

            var sortedTracks = new TreeSet<>(_tracks);
            when(_mockInProgressJobs.getTracks(JOB_ID, MEDIA_ID, 1, 1))
                .thenReturn(sortedTracks);

            _mediaSelectorsOutputFileProcessor.process(exchange);

            var media = job.getMedia().iterator().next();
            if (_shouldBeStored) {
                verify(_mockStorageService)
                    .storeMediaSelectorsOutput(
                                eq(job), eq(media), eq(MediaSelectorType.JSON_PATH), notNull());
            }
            else {
                verifyNoInteractions(_mockStorageService);
                return List.of();
            }

            verify(_mockInProgressJobs, never())
                .addError(any(), any(), any());

            verify(_mockInProgressJobs, never())
                    .getTracks(anyLong(), anyLong(), anyInt(), intThat(i -> i != 1));

            var mappers = new ArrayList<UnaryOperator<String>>();
            var notPresentString = UUID.randomUUID().toString();
            for (var selector : _mediaSelectors) {
                var captor = ArgumentCaptor.forClass(UnaryOperator.class);
                verify(mockEvaluator)
                    .replaceStrings(eq(selector.expression()), captor.capture());

                var mapper = captor.getValue();
                mappers.add(mapper);

                if (_shouldCheckNotPresent) {
                    assertNotMapped(mapper, notPresentString);
                }
            }

            return mappers;
        }
    }

    private static JobPipelineElements createTestPipeline() {
        var algos = new ArrayList<Algorithm>();
        var actions = new ArrayList<Action>();
        for (int i = 1; i <= 3; i++) {
            var algo = new Algorithm("ALGO" + i, null, null, null, null, null, null, false, false);
            algos.add(algo);
            var action = new Action("ACTION" + i, null, algo.name(), null);
            actions.add(action);
        }
        var task1 = new Task("TASK1", null, List.of(actions.get(0).name()));
        var task2 = new Task(
                "TASK2", null,
                List.of(actions.get(1).name(), actions.get(2).name()));
        var pipeline = new Pipeline("PIPELINE", null, List.of(task1.name(), task2.name()));

        return new JobPipelineElements(pipeline, List.of(task1, task2), actions, algos);
    }

    private MediaImpl createTestMedia(Collection<MediaSelector> mediaSelectors) {
        return new MediaImpl(
                MEDIA_ID, "file:///path/to/media", null, _mediaPath, Map.of(),
                Map.of(), List.of(), List.of(), mediaSelectors, "ACTION3", null);
    }


    private BatchJobImpl createTestJob(Collection<MediaSelector> mediaSelectors) {
        var pipelineElements = createTestPipeline();
        return new BatchJobImpl(
                JOB_ID,
                null,
                null,
                pipelineElements, 0, null, null,
                List.of(createTestMedia(mediaSelectors)),
                Map.of(), Map.of(), false);
    }
}
