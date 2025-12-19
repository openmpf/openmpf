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

package org.mitre.mpf.wfm.camel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.Test;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.mitre.mpf.rest.api.pipelines.Task;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.camel.TrackOutputHelper.Annotator;
import org.mitre.mpf.wfm.camel.TrackOutputHelper.TrackGroupKey;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJobImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.TaskAnnotatorService;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

public class TestTrackOutputHelper extends MockitoTest.Strict {

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @Mock
    private TaskAnnotatorService _mockTaskAnnotatorService;

    @InjectMocks
    private TrackOutputHelper _trackOutputHelper;


    @Test
    public void testNoAnnotators() {
        var tracks = List.of(
            createTrack(0, 0),

            createTrack(1, 0),
            createTrack(1, 0),

            createTrack(2, 0),
            createTrack(2, 0),
            createTrack(2, 0),

            createTrack(3, 0),
            createTrack(3, 0),
            createTrack(3, 0),
            createTrack(3, 0)
        );
        var job = createTestJob();
        setTracks(job, tracks);
        var media = job.getMedia().iterator().next();
        when(_mockTaskAnnotatorService.createIsAnnotatedChecker(job, media))
            .thenReturn(t -> false);

        var trackGroups = _trackOutputHelper.getTrackGroups(job, media);
        assertThat(trackGroups.size()).isEqualTo(tracks.size());
        assertThat(trackGroups.keySet()).hasSize(4);

        assertThat(trackGroups.get(new TrackGroupKey(0, 0, ImmutableSet.of())))
            .hasSize(1);
        assertThat(trackGroups.get(new TrackGroupKey(1, 0, ImmutableSet.of())))
            .hasSize(2);
        assertThat(trackGroups.get(new TrackGroupKey(2, 0, ImmutableSet.of())))
            .hasSize(3);
        assertThat(trackGroups.get(new TrackGroupKey(3, 0, ImmutableSet.of())))
            .hasSize(4);
    }


    @Test
    public void testWithAnnotators() {
        var t0AnnotatedTrack = createTrack(0, 0);

        var t1TransitiveAnnotatedTrack = createTrack(1, 0);
        var t1SingleAnnotatedTrack = createTrack(1, 0);

        var t2TransitiveAnnotatedTrack = createTrack(2, 0, List.of(0, 1));
        var t2SingleAnnotatedTrack = createTrack(2, 0, List.of(1));

        var tracks = List.of(
            t0AnnotatedTrack,

            t1TransitiveAnnotatedTrack,
            t1SingleAnnotatedTrack,

            t2TransitiveAnnotatedTrack,
            t2SingleAnnotatedTrack,
            createTrack(2, 0),

            createTrack(3, 0, List.of(0, 1, 2)),
            createTrack(3, 0, List.of(0, 1, 2)),
            createTrack(3, 0, List.of(1, 2)),
            createTrack(3, 0, List.of(2)),
            createTrack(3, 0)
        );

        var job = createTestJob();
        setTracks(job, tracks);
        var media = job.getMedia().iterator().next();

        var annotatedTracks = Set.of(
                t0AnnotatedTrack,
                t1TransitiveAnnotatedTrack,
                t1SingleAnnotatedTrack,
                t2TransitiveAnnotatedTrack,
                t2SingleAnnotatedTrack);

        when(_mockTaskAnnotatorService.createIsAnnotatedChecker(job, media))
            .thenReturn(annotatedTracks::contains);

        var trackGroups = _trackOutputHelper.getTrackGroups(job, media);
        assertThat(trackGroups.size()).isEqualTo(tracks.size() - annotatedTracks.size());
        assertThat(trackGroups.keySet()).hasSize(5);

        var task0Key = new TrackGroupKey(0, 0, ImmutableSet.of(
                new Annotator(1, 0),
                new Annotator(2, 0),
                new Annotator(3, 0)));
        assertThat(trackGroups.get(task0Key)).containsExactly(
                tracks.get(6), tracks.get(7));

        var task1Key = new TrackGroupKey(1, 0, ImmutableSet.of(
                new Annotator(2, 0),
                new Annotator(3, 0)
        ));
        assertThat(trackGroups.get(task1Key)).containsExactly(tracks.get(8));

        var task2UnannotatedKey = new TrackGroupKey(2, 0, ImmutableSet.of());
        assertThat(trackGroups.get(task2UnannotatedKey)).containsExactly(tracks.get(5));

        var task2AnnotatedKey = new TrackGroupKey(2, 0, ImmutableSet.of(
                new Annotator(3, 0)));
        assertThat(trackGroups.get(task2AnnotatedKey)).containsExactly(tracks.get(9));

        var task3Key = new TrackGroupKey(3, 0, ImmutableSet.of());
        assertThat(trackGroups.get(task3Key)).containsExactly(tracks.get(10));
    }


    private void setTracks(BatchJob job, Collection<Track> tracks) {
        var media = job.getMedia().iterator().next();
        when(_mockInProgressJobs.getTracksStream(eq(job.getId()), eq(media.getId()), anyInt(), anyInt()))
            .thenAnswer(inv -> {
                int taskIdx = inv.getArgument(2);
                int actionIdx = inv.getArgument(3);
                return tracks.stream()
                    .filter(t -> t.getTaskIndex() == taskIdx && t.getActionIndex() == actionIdx);
            });
    }


    private static BatchJobImpl createTestJob() {
        return createTestJob(createTestPipeline());
    }

    private static BatchJobImpl createTestJob(JobPipelineElements pipelineElements) {
        var media = new MediaImpl(
                321, MediaUri.create("file:///fake-uri"), UriScheme.FILE, null, Map.of(), Map.of(),
                List.of(), List.of(), List.of(), null, null);

        return new BatchJobImpl(
            123, null, null,
            pipelineElements, 0, null, null,
            List.of(media), Map.of(), Map.of());
    }

    private static JobPipelineElements createTestPipeline() {
        var algos = new ArrayList<Algorithm>();
        var actions = new ArrayList<Action>();
        var tasks = new ArrayList<Task>();

        for (int i = 0; i < 4; i++) {
            var algo = new Algorithm(
                    "ALGO" + i, "", ActionType.DETECTION, "", OptionalInt.empty(), null, null,
                    true, false);
            algos.add(algo);

            var action = new Action("ACTION" + i, "", algo.name(), List.of());
            actions.add(action);

            var task = new Task("TASK" + i, "", List.of(action.name()));
            tasks.add(task);
        }

        var taskNames = tasks.stream()
                .map(Task::name)
                .toList();
        var pipeline = new Pipeline("PIPELINE", "", taskNames);
        return new JobPipelineElements(pipeline, tasks, actions, algos);
    }


    private static Track createTrack(int taskIndex, int actionIndex) {
        return createTrack(taskIndex, actionIndex, ImmutableSortedSet.of());
    }

    private static Track createTrack(
            int taskIndex, int actionIndex, Collection<Integer> annotatedTaskIndices) {
        var track = mock(Track.class);
        when(track.getTaskIndex())
            .thenReturn(taskIndex);
        when(track.getActionIndex())
            .thenReturn(actionIndex);
        lenient().when(track.getAnnotatedTaskIndices())
            .thenReturn(ImmutableSortedSet.copyOf(annotatedTaskIndices));
        return track;
    }
}
