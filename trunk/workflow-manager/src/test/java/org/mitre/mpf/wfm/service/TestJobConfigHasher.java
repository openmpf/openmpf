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

package org.mitre.mpf.wfm.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiFunction;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.AlgorithmProperty;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.rest.api.pipelines.ValueType;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.MediaActionProps;
import org.mitre.mpf.wfm.util.MediaRange;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.Mock;
import org.springframework.core.io.ClassPathResource;
import org.mitre.mpf.test.MockitoTest;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;

public class TestJobConfigHasher extends MockitoTest.Strict {

    @Mock
    private PropertiesUtil _mockPropsUtil;

    @Mock
    private WorkflowPropertyService _mockWfPropSvc;


    private JobConfigHasher _jobConfigHasher;

    @Before
    public void init() throws IOException {
        var objectMapper = ObjectMapperFactory.customObjectMapper();
        when(_mockPropsUtil.getOutputObjectVersion())
            .thenReturn("A.B.C");
        when(_mockPropsUtil.getTiesDbCheckIgnorablePropertiesResource())
            .thenReturn(new ClassPathResource("/test-ignorable-properties.json"));

        when(_mockPropsUtil.getOutputChangedCounter())
            .thenReturn("1");

        _jobConfigHasher = new JobConfigHasher(_mockPropsUtil, _mockWfPropSvc, objectMapper);
    }

    @Test
    public void sameJobGetsSameHash() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .addAction("ALGO2")
            .setProperty("PROP", 0, 0, "VAL1")
            .setProperty("PROP", 0, 1, "VAL2")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .addAction("ALGO2")
            .setProperty("PROP", 0, 0, "VAL1")
            .setProperty("PROP", 0, 1, "VAL2")
            .getHash();

        assertEquals(hash1, hash2);
    }


    @Test
    public void changingAlgorithmChangesHash() {
        var hash1 = builder().addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .getHash();

        var hash2 = builder().addMedia(MediaType.IMAGE)
            .addAction("ALGO2")
            .getHash();

        assertNotEquals(hash1, hash2);
    }

    @Test
    public void changingImportantJobPropertyChangesHash() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .addAction("ALGO2")
            .setProperty("PROP", 0, 0, "VAL1")
            .setProperty("PROP", 0, 1, "VAL2")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .addAction("ALGO2")
            .setProperty("PROP", 0, 0, "VAL1")
            .setProperty("PROP", 0, 1, "VAL3")
            .getHash();

        assertNotEquals(hash1, hash2);
    }

    @Test
    public void addingImportantPropertyChangesHash() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .addAction("ALGO2")
            .setProperty("PROP", 0, 0, "VAL1")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .addAction("ALGO2")
            .setProperty("PROP", 0, 0, "VAL1")
            .setProperty("PROP", 0, 1, "VAL1")
            .getHash();

        assertNotEquals(hash1, hash2);
    }

    @Test
    public void testNullAndEmptyStringDifferent() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("PROP", 0, 0, "")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("PROP", 0, 0, null)
            .getHash();

        assertNotEquals(hash1, hash2);
    }

    @Test
    public void removingImportantPropertyChangesHash() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .addAction("ALGO2")
            .setProperty("PROP", 0, 0, "VAL1")
            .setProperty("PROP", 0, 1, "VAL2")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .addAction("ALGO2")
            .setProperty("PROP", 0, 0, "VAL1")
            .getHash();

        assertNotEquals(hash1, hash2);
    }


    @Test
    public void changingIgnorableJobPropertyDoesNotChangeHash() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("PROP", 0, 0, "VAL1")
            .setProperty("NOT_IMPORTANT", 0, 0, "VAL2")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("PROP", 0, 0, "VAL1")
            .setProperty("NOT_IMPORTANT", 0, 0, "VAL3")
            .getHash();

        assertEquals(hash1, hash2);
    }


    @Test
    public void unionIsUsedForDuplicatedProperties() {
        for (var mediaType : MediaType.values()) {
            var hash1 = builder()
                .addMedia(mediaType)
                .addAction("ALGO")
                .setProperty("TEST_UNION_IS_USED_FOR_DUPLICATES", 0, 0, "VAL1")
                .getHash();

            var hash2 = builder()
                .addMedia(mediaType)
                .addAction("ALGO")
                .setProperty("TEST_UNION_IS_USED_FOR_DUPLICATES", 0, 0, "VAL2")
                .getHash();
            assertEquals(hash1, hash2);
        }

    }


    @Test
    public void addingIgnorablePropertyDoesNotChangeHash() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("PROP", 0, 0, "VAL1")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("PROP", 0, 0, "VAL1")
            .setProperty("NOT_IMPORTANT", 0, 0, "VAL3")
            .getHash();

        assertEquals(hash1, hash2);

    }

    @Test
    public void removingIgnorablePropertyDoesNotChangeHash() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("PROP", 0, 0, "VAL1")
            .setProperty("NOT_IMPORTANT", 0, 0, "VAL3")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("PROP", 0, 0, "VAL1")
            .getHash();

        assertEquals(hash1, hash2);
    }

    @Test
    public void changingMediaHashChangesJobHash() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE, "HASH1")
            .addAction("ALGO")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE, "HASH2")
            .addAction("ALGO")
            .getHash();

        assertNotEquals(hash1, hash2);
    }

    @Test
    public void addingMediaHashChangesJobHash() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE, "HASH1")
            .addAction("ALGO")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE, "HASH1")
            .addMedia(MediaType.IMAGE, "HASH1")
            .addAction("ALGO")
            .getHash();

        assertNotEquals(hash1, hash2);
    }


    @Test
    public void parallelActionsDifferentFromSequential() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .addAction("ALGO2")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .newTask()
            .addAction("ALGO2")
            .getHash();

        assertNotEquals(hash1, hash2);
    }


    @Test
    public void testAlgorithmOutputChangedCounter() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO", OptionalInt.empty())
            .newTask()
            .addAction("ALGO2", OptionalInt.of(2))
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO", OptionalInt.empty())
            .newTask()
            .addAction("ALGO2", OptionalInt.of(2))
            .getHash();

        assertEquals(
                "Both algorithms missing output changed counter should be the same.",
                hash1, hash2);

        var hash3 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO", OptionalInt.of(0))
            .newTask()
            .addAction("ALGO2", OptionalInt.of(2))
            .getHash();

        assertNotEquals(
                "A present output changed counter should not match missing output changed counter.",
                hash2, hash3);

        var hash4 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO", OptionalInt.of(10))
            .newTask()
            .addAction("ALGO2", OptionalInt.of(2))
            .getHash();
        assertNotEquals(
                "Different output changed counters should not match.", hash3, hash4);
    }


    @Test
    public void testMediaOrderDoesNotMatter() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE, "MEDIA_HASH1")
            .addMedia(MediaType.IMAGE, "MEDIA_HASH2")
            .addAction("ALGO")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE, "MEDIA_HASH2")
            .addMedia(MediaType.IMAGE, "MEDIA_HASH1")
            .addAction("ALGO")
            .getHash();

        assertEquals(hash1, hash2);
    }


    @Test
    public void testMediaTypeProperties() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("VIDEO_PROP", 0, 0, "VIDEO1")
            .setProperty("IMAGE_PROP", 0, 0, "IMAGE1")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("VIDEO_PROP", 0, 0, "VIDEO2")
            .setProperty("IMAGE_PROP", 0, 0, "IMAGE1")
            .getHash();

        assertEquals("Job hash shouldn't change when media is an image and property that"
                + " requires video is changed.", hash1, hash2);

        var hash3 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setProperty("VIDEO_PROP", 0, 0, "VIDEO2")
            .setProperty("IMAGE_PROP", 0, 0, "IMAGE2")
            .getHash();

        assertNotEquals("Job hash should change when changing property that requires a"
                + " matching media type.", hash2, hash3);
    }


    @Test
    public void changingPropNotUsedByAlgoDoesNotChangeHash() {
        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setJobProperty("PROP", "VAL1")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setJobProperty("PROP", "VAL2")
            .getHash();
        assertEquals(hash1, hash2);
    }


    @Test
    public void changingWorkflowPropNotUsedByAlgoChangesHash() {
        when(_mockWfPropSvc.getProperty("PROP", MediaType.IMAGE))
            .thenReturn(new WorkflowProperty(null, null, null, null, null, List.of()));

        var hash1 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setJobProperty("PROP", "VAL1")
            .getHash();

        var hash2 = builder()
            .addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .setJobProperty("PROP", "VAL2")
            .getHash();
        assertNotEquals(hash1, hash2);
    }


    @Test
    public void testMediaRanges() {
        var fullVideo = builder()
            .addMedia(Set.of(), Set.of())
            .addAction("ALGO")
            .getHash();

        var videoWithFrameRange = builder()
            .addMedia(
                Set.of(new MediaRange(5, 10)),
                Set.of())
            .addAction("ALGO")
            .getHash();

        assertNotEquals(fullVideo, videoWithFrameRange);

        var videoWithTimeRange = builder()
            .addMedia(
                Set.of(),
                Set.of(new MediaRange(5, 10)))
            .addAction("ALGO")
            .getHash();

        assertNotEquals(fullVideo, videoWithTimeRange);
        assertNotEquals(videoWithFrameRange, videoWithTimeRange);


        var videoWithBothRanges = builder()
            .addMedia(
                Set.of(new MediaRange(5, 10)),
                Set.of(new MediaRange(5, 10)))
            .addAction("ALGO")
            .getHash();

        assertNotEquals(fullVideo, videoWithBothRanges);
        assertNotEquals(videoWithFrameRange, videoWithBothRanges);
        assertNotEquals(videoWithTimeRange, videoWithBothRanges);
    }


    @Test
    public void testOutputChangedCounter() {
        var hash1 = builder().addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .getHash();

        when(_mockPropsUtil.getOutputChangedCounter())
            .thenReturn("2");

        var hash2 = builder().addMedia(MediaType.IMAGE)
            .addAction("ALGO")
            .getHash();

        assertNotEquals(hash1, hash2);
    }


    private JobBuilder builder() {
        return new JobBuilder();
    }

    private class JobBuilder {
        private List<Media> _media = new ArrayList<>();

        private List<List<Action>> _taskContents = new ArrayList<>();

        private Map<String, OptionalInt> _algoNameToOutputCounter = new HashMap<>();

        private Table<Integer, Integer, Map<String, String>> _properties = HashBasedTable.create();

        private Map<String, String> _jobProperties = new HashMap<>();

        @SuppressWarnings("unchecked")
        private BiFunction<Media, Action, Map<String, String>> _mockPropMapGetter
                = mock(BiFunction.class);

        public String getHash() {
            var pipelineElements = mock(JobPipelineElements.class);

            var tasks = new ArrayList<Task>();
            int actionIdx = -1;
            var algoToPropNames = HashMultimap.<String, String>create();
            for (var actionList : _taskContents) {
                var actionNames = actionList.stream()
                        .map(Action::name)
                        .toList();
                var task = new Task(null, null, actionNames);
                when(pipelineElements.getActionsInOrder(task))
                    .thenReturn(actionList);
                tasks.add(task);

                for (var action : actionList) {
                    actionIdx++;
                    for (var mediaIdx = 0; mediaIdx < _media.size(); mediaIdx++) {
                        var map = _properties.get(mediaIdx, actionIdx);
                        if (map == null) {
                            map = _jobProperties;
                        }
                        else {
                            map.putAll(_jobProperties);
                            algoToPropNames.putAll(action.algorithm(), map.keySet());
                        }
                        when(_mockPropMapGetter.apply(_media.get(mediaIdx), action))
                            .thenReturn(map);
                    }
                }
            }
            when(pipelineElements.getTasksInOrder())
                .thenReturn(tasks);

            for (var entry : _algoNameToOutputCounter.entrySet()) {
                var algoProps = algoToPropNames.get(entry.getKey())
                        .stream()
                        .map(pn -> new AlgorithmProperty(pn, "", ValueType.STRING, "", null))
                        .toList();
                var algo = new Algorithm(
                        entry.getKey(), null, ActionType.DETECTION, null, entry.getValue(),
                        null, new Algorithm.Provides(List.of(), algoProps), true, true);
                when(pipelineElements.getAlgorithm(entry.getKey()))
                    .thenReturn(algo);
            }

            return _jobConfigHasher.getJobConfigHash(
                    _media, pipelineElements, new MediaActionProps(_mockPropMapGetter));
        }

        public JobBuilder addMedia(MediaType mediaType) {
            return addMedia(mediaType, mediaType.name() + "HASH_" + _media.size());
        }

        public JobBuilder addMedia(MediaType mediaType, String hash) {
            return addMediaInternal(mediaType, hash, Set.of(), Set.of());
        }

        public JobBuilder addMedia(Set<MediaRange> frameRanges, Set<MediaRange> timeRanges) {
            return addMediaInternal(
                    MediaType.VIDEO, "VIDEO_HASH_" + _media.size(), frameRanges, timeRanges);
        }

        private long _mediaIds = 0;

        private JobBuilder addMediaInternal(
                MediaType mediaType,
                String hash,
                Set<MediaRange> frameRanges,
                Set<MediaRange> timeRanges) {
            var media = mock(Media.class);
            when(media.getId())
                    .thenReturn(++_mediaIds);
            when(media.getLinkedHash())
                .thenReturn(Optional.of(hash));

            when(media.getType())
                .thenReturn(Optional.of(mediaType));

            when(media.getFrameRanges())
                    .thenReturn(ImmutableSet.copyOf(frameRanges));

            when(media.getTimeRanges())
                    .thenReturn(ImmutableSet.copyOf(timeRanges));
            _media.add(media);
            return this;
        }

        public JobBuilder addAction(String algorithm) {
            return addAction(algorithm, OptionalInt.empty());
        }

        private long _actionNames = 0;

        public JobBuilder addAction(String algorithm, OptionalInt outputChangedCounter) {
            if (_taskContents.isEmpty()) {
                newTask();
            }

            var prev = _algoNameToOutputCounter.put(algorithm, outputChangedCounter);
            assertTrue(prev == null || prev.equals(outputChangedCounter));

            var action = new Action(String.valueOf(++_actionNames), null, algorithm, List.of());
            _taskContents.get(_taskContents.size() - 1).add(action);

            return this;
        }

        public JobBuilder newTask() {
            _taskContents.add(new ArrayList<>());
            return this;
        }

        public JobBuilder setProperty(String name, int mediaIdx, int actionIdx, String value) {
            var map = _properties.get(mediaIdx, actionIdx);
            if (map == null) {
                map = new HashMap<String, String>();
                _properties.put(mediaIdx, actionIdx, map);
            }
            map.put(name, value);

            return this;
        }

        public JobBuilder setJobProperty(String name, String value) {
            _jobProperties.put(name, value);
            return this;
        }

        @Override
        public boolean equals(Object other) {
            Assert.fail("Call JobBuilder.getHash()");
            return false;
        }
    }
}
