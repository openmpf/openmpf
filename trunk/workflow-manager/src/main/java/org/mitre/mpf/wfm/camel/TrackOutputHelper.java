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


package org.mitre.mpf.wfm.camel;

import static java.util.stream.Collectors.toCollection;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.IntStream;

import javax.inject.Inject;

import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.segmenting.TriggerProcessor;
import org.mitre.mpf.wfm.service.TaskMergingManager;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;


@Component
public class TrackOutputHelper {

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final InProgressBatchJobsService _inProgressBatchJobsService;

    private final TriggerProcessor _triggerProcessor;

    private final TaskMergingManager _taskMergingManager;

    @Inject
    TrackOutputHelper(
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            InProgressBatchJobsService inProgressBatchJobsService,
            TriggerProcessor triggerProcessor,
            TaskMergingManager taskMergingManager) {
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _inProgressBatchJobsService = inProgressBatchJobsService;
        _triggerProcessor = triggerProcessor;
        _taskMergingManager = taskMergingManager;
    }

    public record TrackInfo(
            boolean isSuppressed,
            boolean isMergeSource,
            boolean isMergeTarget,
            boolean hadAnyTracks,
            Multimap<String, Track> tracksGroupedByAction) { }


    public TrackInfo getTrackInfo(BatchJob job, Media media, int taskIdx, int actionIdx) {
        boolean isSuppressed = isSuppressed(job, media, taskIdx, actionIdx);
        boolean isMergeTarget = _taskMergingManager.isMergeTarget(job, media, taskIdx);
        boolean needToCheckTrackTriggers = needToCheckSuppressedTriggers(job, media, taskIdx);
        boolean isMergeSource = _taskMergingManager.isMergeSource(job, media, taskIdx, actionIdx);

        boolean canAvoidGettingTracks =
                isMergeTarget || (isSuppressed && !needToCheckTrackTriggers);
        if (canAvoidGettingTracks) {
            int trackCount = _inProgressBatchJobsService.getTrackCount(
                    job.getId(), media.getId(), taskIdx, actionIdx);
            return new TrackInfo(
                    isSuppressed, isMergeSource, isMergeTarget, trackCount > 0,
                    ImmutableMultimap.of());
        }

        var tracks = _inProgressBatchJobsService.getTracks(
                job.getId(), media.getId(), taskIdx, actionIdx);

        if (needToCheckTrackTriggers) {
            tracks = findUntriggered(job, media, taskIdx, tracks);
            if (!tracks.isEmpty()) {
                // Since there are un-triggered tracks, this task is the last task that
                // processed those tracks.
                isSuppressed = false;
            }
        }
        var indexedTracks = Multimaps.index(tracks, t -> getMergedAction(t, job));
        return new TrackInfo(
                isSuppressed, isMergeSource, isMergeTarget,
                !indexedTracks.isEmpty(), indexedTracks);
    }

    private boolean isSuppressed(BatchJob job, Media media, int taskIdx, int actionIdx) {
        return actionIdx != -1 ? _aggregateJobPropertiesUtil.isSuppressTrack(media, job,
            job.getPipelineElements().getAction(taskIdx, actionIdx)) : _aggregateJobPropertiesUtil.isSuppressTrack(media, job);
    }

    private boolean isSuppressed(BatchJob job, Media media, int taskIdx) {
        return isSuppressed(job, media, taskIdx, -1);
    }

    private boolean needToCheckSuppressedTriggers(BatchJob job, Media media, int taskIdx) {
        if (!isSuppressed(job, media, taskIdx)) {
            return false;
        }

        var pipelineElements = job.getPipelineElements();
        // Check if a task later in the pipeline does not have a trigger set. When a trigger is not
        // set, all tracks are passed as input to the task. That means that all of the tracks
        // created in current task were input to a task.
        boolean futureTaskMissingTrigger = IntStream
            .rangeClosed(taskIdx + 1, pipelineElements.getLastDetectionTaskIdx())
            .mapToObj(pipelineElements::getTask)
            .flatMap(pipelineElements::getActionStreamInOrder)
            .map(a -> _aggregateJobPropertiesUtil.getValue(MpfConstants.TRIGGER, job, media, a))
            .anyMatch(t -> t == null || t.isBlank());
        return !futureTaskMissingTrigger;
    }


    private SortedSet<Track> findUntriggered(
            BatchJob job, Media media, int taskIdx, SortedSet<Track> tracks) {
        int lastDetectionTaskIdx = job.getPipelineElements().getLastDetectionTaskIdx();
        if (taskIdx == lastDetectionTaskIdx) {
            return tracks;
        }
        var wasTriggeredFilter = _triggerProcessor.createWasEverTriggeredFilter(
                job, media, taskIdx, lastDetectionTaskIdx);
        return tracks.stream()
            .filter(wasTriggeredFilter.negate())
            .collect(toCollection(TreeSet::new));
    }


    private String getMergedAction(Track track, BatchJob job) {
        int actionIndex;
        var trackMergingEnabled = track.getMergedTaskIndex() != track.getTaskIndex();
        if (trackMergingEnabled) {
            // When track merging is enabled, the merged task will never be the last task.  Only
            // the last task can have more than one action, so the merged action has to be the
            // first and only action in the task.
            actionIndex = 0;
        }
        else {
            actionIndex = track.getActionIndex();
        }
        return job.getPipelineElements()
                .getAction(track.getMergedTaskIndex(), actionIndex)
                .name();
    }
}
