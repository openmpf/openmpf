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

package org.mitre.mpf.wfm.segmenting;

import static java.util.stream.Collectors.toSet;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class TestTriggerProcessor extends MockitoTest.Strict {

    private static final long JOB_ID = 11;

    private static final long MEDIA_ID = 12;


    @Mock
    private AggregateJobPropertiesUtil _mockAggPropUtil;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @InjectMocks
    private TriggerProcessor _triggerProcessor;


    @Test
    public void testTriggerValidation() {
        TriggerProcessor.validateTrigger(getTriggerProperties(null));
        TriggerProcessor.validateTrigger(getTriggerProperties(""));
        TriggerProcessor.validateTrigger(getTriggerProperties("a=a", "FRAME"));
        TriggerProcessor.validateTrigger(getTriggerProperties("a=", "SUPERSET_REGION"));
        TriggerProcessor.validateTrigger(getTriggerProperties("a=b=", "REGION"));

        assertTriggerInvalid("asdf");
        assertTriggerInvalid("=");
        assertTriggerInvalid("=a");
    }


    @Test
    public void testTriggerRequiresFeedForward() {
        var trigger = "a=a";
        assertTriggerValidationFails(trigger, null);
        assertTriggerValidationFails(trigger, "");
        assertTriggerValidationFails(trigger, "  ");
        assertTriggerValidationFails(trigger, "asdf");
        assertTriggerValidationFails(trigger, "NONE");
        assertTriggerValidationFails(trigger, "none");
    }


    private static UnaryOperator<String> getTriggerProperties(
            String trigger, String feedForwardType) {
        return propName -> {
            if (propName.equals("TRIGGER")) {
                return trigger;
            }
            if (propName.equals("FEED_FORWARD_TYPE")) {
                return feedForwardType;
            }
            throw new IllegalArgumentException();
        };
    }

    private static UnaryOperator<String> getTriggerProperties(String trigger)  {
        return getTriggerProperties(trigger, null);
    }

    private static void assertTriggerInvalid(String trigger) {
        assertTriggerValidationFails(trigger, null);
    }

    private static void assertTriggerValidationFails(String trigger, String feedForwardType) {
        var props = getTriggerProperties(trigger, feedForwardType);
        TestUtil.assertThrows(
                WfmProcessingException.class,
                () -> TriggerProcessor.validateTrigger(props));
    }


    @Test
    public void canHandleNoTracksInPrevTask() {
        var tracks0 = builder()
            .setCurrentTask(0)
            .setLangTrigger("EN", 0)
            .getTriggeredTracks();
        assertThat(tracks0, empty());

        var tracks1 = builder()
            .setCurrentTask(1)
            .setLangTrigger("EN", 1)
            .getTriggeredTracks();
        assertThat(tracks1, empty());
    }


    @Test
    public void canHandleNoTracksMatchingTrigger() {
        var tracks = builder()
            .setCurrentTask(3)
            .setLangTrigger("ZH", 2)
            .addTrack("ES", 2)
            .addTrack("ES", 2)
            .setLangTrigger("EN", 3)
            .getTriggeredTracks();

        assertThat(tracks, empty());
    }


    @Test
    public void onlyReturnsProvidedTracksWhenNoTriggersInJob() {
        var tracks = builder()
            .setCurrentTask(3)
            .addTrack(3, "EN", 2)
            .addTrack(4, "EN", 2)
            .getTriggeredTracks();

        verify(_mockInProgressJobs, never())
                .getTracksStream(anyLong(), anyLong(), anyInt(), anyInt());

        assertContainsTracks(tracks, 3, 4);
    }


    @Test
    public void returnsAllUnTriggeredTracksWhenNoTriggerOnCurrentAction() {
        // 0: speech detection
        // 1: Spanish
        // 2: Russian
        // 3: English
        // 4. other
        var tracks = builder()
                .addTrack("ES", 0)
                .addTrack("ES", 0)
                .addTrack("RU", 0)
                .addTrack("EN", 0)
                .addTrack("EN", 0)
                .addTrack(5, "EO", 0)
                .addTrack(6, "EO", 0)
                .addTrack(7, "ZH", 0)

                .setLangTrigger("ES", 1)
                .addTrack(8, "ES", 1)
                .addTrack(12, "ES", 1)

                .setLangTrigger("RU", 2)
                .addTrack(9, "RU", 2)

                .setLangTrigger("EN", 3)
                .addTrack(10, "EN", 3)
                .addTrack(11, "EN", 3)

                .setCurrentTask(4)
                .getTriggeredTracks();
        assertContainsTracks(tracks, 5, 6, 7, 8, 9, 10, 11, 12);
    }


    @Test
    public void returnsPreviousUnTriggeredTracksThatMatchCurrentTrigger() {
        var tracks = builder()
                .addTrack("ES", 0)
                .addTrack("ES", 0)
                .addTrack("RU", 0)
                .addTrack(8, "EN", 0)
                .addTrack(9, "EN", 0)
                .addTrack("EO", 0)
                .addTrack("EO", 0)
                .addTrack("ZH", 0)

                .setLangTrigger("ES", 1)
                .addTrack("ES", 1)
                .addTrack("ES", 1)
                .addTrack(10, "EN", 1)

                .setLangTrigger("RU", 2)
                .addTrack("RU", 2)

                .setLangTrigger("EN", 3)
                .setCurrentTask(3)
                .getTriggeredTracks();

        assertContainsTracks(tracks, 8, 9, 10);
    }


    @Test
    public void stopsCheckingPrevTasksWhenNoTriggerInMiddle() {
        var tracks = builder()
            .setTrigger("", 2)
            .addTrack(11, "EN", 2)
            .addTrack("ES", 2)

            .setLangTrigger("ES", 3)
            .addTrack(10, "EN", 3)
            .addTrack("ES", 3)

            .setLangTrigger("EN", 4)

            .setCurrentTask(4)
            .getTriggeredTracks();

        verify(_mockInProgressJobs, never())
            .getTracksStream(anyLong(), anyLong(), eq(1), anyInt());

        assertContainsTracks(tracks, 10, 11);
    }


    @Test
    public void testMultipleTriggerValues() {
        var tracks = builder()
            .addTrack(1, "EN", 0)
            .addTrack(2, "RU", 0)
            .addTrack(3, "ES", 0)
            .addTrack("ZH", 0)

            .setCurrentTask(1)
            .setLangTrigger("EN;RU; ES", 1)
            .getTriggeredTracks();

        assertContainsTracks(tracks, 1, 2, 3);
    }


    @Test
    public void testTriggerEscaping() {
        // In the string literals below, a forward slash is used instead of a backslash to avoid
        // having to double escape each backslash. It would be hard to tell which backslashes
        // were used for the Java string literal escaping and which were used for the escaping
        // used by TriggerProcessor.
        var tracks = builder()
            .addTrack(1, reverseSlashes("E/N"), 0)
            .addTrack(2, reverseSlashes(" E/N "), 0)
            .addTrack("EN", 0)

            .addTrack(3, "R;U", 0)
            .addTrack("RU", 0)

            .addTrack(4, "ES", 0)

            .addTrack(5, reverseSlashes("A//B"), 0)
            .addTrack(reverseSlashes("A/B"), 0)
            .addTrack("AB", 0)

            .addTrack("ZH", 0)

            .setCurrentTask(1)
            // After parsing the trigger we get:
            // {"E\N", "R;U", "ES", "A\\B"}
            .setLangTrigger(reverseSlashes("E//N;R/;U;ES ; A////B; "), 1)
            .getTriggeredTracks();

        assertContainsTracks(tracks, 1, 2, 3, 4, 5);
    }


    private static String reverseSlashes(String s) {
        return s.replace('/', '\\');
    }


    @Test
    public void doesNotPassForwardPreviouslyTriggeredTracks() {
        var tracks = initDoesNotPassForwardPreviouslyTriggeredTracks()
            .setCurrentTask(4)
            .getTriggeredTracks();

        assertContainsTracks(tracks, 13, 14);
    }


    @Test
    public void doesNotPassForwardPreviouslyTriggeredTracksWhenNoTrigger() {
        when(_mockInProgressJobs.getTracksStream(JOB_ID, MEDIA_ID, 3, 0))
            .thenReturn(Stream.of());

        var tracks = initDoesNotPassForwardPreviouslyTriggeredTracks()
            .setCurrentTask(5)
            .getTriggeredTracks();

        assertContainsTracks(tracks, 16, 17);
    }


    private TestCaseBuilder initDoesNotPassForwardPreviouslyTriggeredTracks() {
        return builder()
            .addTrack(12, Map.of("LANG", "EN", "OTHER_TRIGGER", "ON"), 2)
            .addTrack(13, Map.of("LANG", "EN", "OTHER_TRIGGER", "OFF"), 2)
            .addTrack(14, "EN", 2)
            .addTrack(15, Map.of("LANG", "ES", "OTHER_TRIGGER", "ON"), 2)
            .addTrack(16, Map.of("LANG", "ES", "OTHER_TRIGGER", "OFF"), 2)
            .addTrack(17, Map.of("OTHER_TRIGGER", "OFF"), 2)
            .addTrack(18, Map.of("OTHER_TRIGGER", "ON"), 2)

            .setTrigger("OTHER_TRIGGER=ON", 3)
            .setLangTrigger("EN", 4);
    }



    private static void assertContainsTracks(Collection<Track> tracks, int... startFrames) {
        assertThat(tracks, hasSize(startFrames.length));
        var actualStartFrames = tracks.stream()
            .map(Track::getStartOffsetFrameInclusive)
            .collect(toSet());

        for (int startFrame : startFrames) {
            assertThat(
                    "Expected there to be a track with a start frame of " + startFrame,
                    startFrame, isIn(actualStartFrames));
        }
    }


    private TestCaseBuilder builder() {
        return new TestCaseBuilder();
    }

    private class TestCaseBuilder {
        // Used to prevent tracks from being equal. Negative numbers are being used in Track
        // fields that are not relevent to this test.
        private static int _trackId = -1;

        private Multimap<Integer, Track> _tracks = HashMultimap.create();

        private int _currentTask = -1;

        private Map<Integer, String> _triggers = new HashMap<>();


        public TestCaseBuilder setCurrentTask(int taskIdx) {
            _currentTask = taskIdx;
            return this;
        }

        public TestCaseBuilder setTrigger(String trigger, int taskIdx) {
            _triggers.put(taskIdx, trigger);
            return this;
        }


        public TestCaseBuilder setLangTrigger(String language, int taskIdx) {
            return setTrigger("LANG=" + language, taskIdx);
        }


        public TestCaseBuilder addTrack(String language, int taskIdx) {
            return addTrack(-20, language, taskIdx);
        }

        public TestCaseBuilder addTrack(int startFrame, String language, int taskIdx) {
            return addTrack(startFrame, Map.of("LANG", language), taskIdx);
        }

        public TestCaseBuilder addTrack(int startFrame, Map<String, String> trackProps, int taskIdx) {
            var track = new Track(
                JOB_ID,
                MEDIA_ID,
                -1,
                -1,
                startFrame,
                startFrame + 10,
                _trackId--,
                -1,
                -1,
                -1,
                List.of(),
                trackProps,
                null);

            _tracks.put(taskIdx, track);
            return this;
        }

        public Collection<Track> getTriggeredTracks() {
            assertThat(_currentTask, greaterThan(-1));

            var media = mock(Media.class);
            lenient().when(media.getId())
                .thenReturn(MEDIA_ID);

            var job = mock(BatchJob.class);
            lenient().when(job.getId())
                .thenReturn(JOB_ID);
            when(_mockInProgressJobs.getJob(JOB_ID))
                .thenReturn(job);

            var pipelineElements = mock(JobPipelineElements.class);
            lenient().when(job.getPipelineElements())
                .thenReturn(pipelineElements);

            for (var triggerEntry : _triggers.entrySet()) {
                if (triggerEntry.getKey() == _currentTask) {
                    // The information about the current task will be provided in the
                    // DetectionContext.
                    continue;
                }
                createMockTask(
                        triggerEntry.getKey(), pipelineElements, triggerEntry.getValue(),
                        job, media);
            }
            for (var trackEntry : _tracks.asMap().entrySet()) {
                int taskIdx = trackEntry.getKey();
                if (taskIdx != _currentTask - 1) {
                    when(_mockInProgressJobs.getTracksStream(JOB_ID, MEDIA_ID, taskIdx, 0))
                        .thenReturn(trackEntry.getValue().stream());
                }
                if (!_triggers.containsKey(taskIdx)) {
                    createMockTask(taskIdx, pipelineElements, null, job, media);
                }
            }

            var detectionContext = new DetectionContext(
                JOB_ID,
                _currentTask,
                "task name",
                0,
                "action name",
                _currentTask == 0,
                createAlgorithmProps(_triggers.get(_currentTask)),
                Set.copyOf(_tracks.get(_currentTask - 1)),
                null, "");
            return _triggerProcessor.getTriggeredTracks(media, detectionContext).toList();
        }

        private void createMockTask(
                int taskIdx,
                JobPipelineElements pipelineElements,
                String trigger,
                BatchJob job,
                Media media) {
            var algo = new Algorithm(
                    "algo-" + taskIdx, null, ActionType.DETECTION, null, null, null,
                    null, false, false);
            lenient().when(pipelineElements.getAlgorithm(algo.name()))
                    .thenReturn(algo);

            var action = new Action("action-" + taskIdx, null, algo.name(), List.of());
            var task = new Task("task-" + taskIdx, null, List.of(action.name()));
            lenient().when(pipelineElements.getTask(taskIdx))
                    .thenReturn(task);
            lenient().when(pipelineElements.getActionsInOrder(task))
                    .thenReturn(List.of(action));
            if (trigger != null) {
                when(_mockAggPropUtil.getValue(MpfConstants.TRIGGER, job, media, action))
                        .thenReturn(trigger);
            }
        }
    }

    private static List<AlgorithmPropertyProtocolBuffer.AlgorithmProperty>
            createAlgorithmProps(String trigger) {

        var prop1 = AlgorithmPropertyProtocolBuffer.AlgorithmProperty.newBuilder()
                .setPropertyName("OTHER_PROP")
                .setPropertyValue("OTHER_PROP_VALUE")
                .build();
        var prop2 = AlgorithmPropertyProtocolBuffer.AlgorithmProperty.newBuilder()
                .setPropertyName("TRIGGER")
                .setPropertyValue(Objects.requireNonNullElse(trigger, ""))
                .build();

        return List.of(prop1, prop2);
    }
}
