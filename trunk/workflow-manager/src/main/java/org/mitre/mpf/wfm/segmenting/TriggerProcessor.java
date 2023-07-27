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

import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.springframework.stereotype.Service;

@Service
public class TriggerProcessor {

    private static final Predicate<Track> ALL_MATCH = t -> true;

    private static final Predicate<Track> NONE_MATCH = t -> false;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final InProgressBatchJobsService _inProgressJobs;

    @Inject
    TriggerProcessor(
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            InProgressBatchJobsService inProgressJobs) {
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _inProgressJobs = inProgressJobs;
    }


    public static void validateTrigger(UnaryOperator<String> properties) {
        createTriggerFilter(properties.apply(MpfConstants.TRIGGER));
    }


    public Stream<Track> getTriggeredTracks(Media media, DetectionContext context) {
        var trigger = context.getAlgorithmProperties()
            .stream()
            .filter(ap -> ap.getPropertyName().equals(MpfConstants.TRIGGER))
            .map(ap -> ap.getPropertyValue())
            .filter(pv -> pv != null && !pv.isBlank())
            .findAny()
            .orElse(null);

        return Stream.concat(
                    context.getPreviousTracks().stream(),
                    findPreviousUnTriggered(media, context))
            .filter(createTriggerFilter(trigger));
    }


    public Predicate<Track> createWasTriggeredFilter(
            BatchJob job, Media media, int creationTaskIdx, int lastTaskIdx) {
        var fullTrigger = NONE_MATCH;
        for (int taskIdx = creationTaskIdx + 1; taskIdx <= lastTaskIdx; taskIdx++) {
            var taskFilter = getTaskFilter(job, media, taskIdx);
            if (taskFilter == ALL_MATCH) {
                return ALL_MATCH;
            }
            fullTrigger = taskFilter.or(fullTrigger);
        }
        return fullTrigger;
    }


    private Predicate<Track> getTaskFilter(BatchJob job, Media media, int taskIdx) {
        var task = job.getPipelineElements().getTask(taskIdx);
        var taskFilter = NONE_MATCH;
        for (var action : job.getPipelineElements().getActionsInOrder(task)) {
            var algo = job.getPipelineElements().getAlgorithm(action.getAlgorithm());
            if (algo.getActionType() == ActionType.MARKUP) {
                continue;
            }
            var filter = createTriggerFilter(job, media, action);
            if (filter == ALL_MATCH) {
                return ALL_MATCH;
            }
            taskFilter = filter.or(taskFilter);
        }
        return taskFilter;
    }


    private Stream<Track> findPreviousUnTriggered(
            Media media, DetectionContext context) {

        var job = _inProgressJobs.getJob(context.getJobId());
        var tracks = Stream.<Track>empty();

        for (var creationTaskIdx = context.getTaskIndex() - 2; creationTaskIdx >= 0;
                creationTaskIdx--) {

            var triggerFilter = createWasTriggeredFilter(
                    job, media, creationTaskIdx, context.getTaskIndex() - 1);
            if (triggerFilter == ALL_MATCH) {
                // Since the previous task didn't use a trigger, it would have processed all
                // un-triggered tracks from earlier in the pipeline.
                break;
            }

            var taskTracks = getTracks(creationTaskIdx, media, context)
                .filter(triggerFilter.negate());
            tracks = Stream.concat(tracks, taskTracks);
        }
        return tracks;
    }


    private Predicate<Track> createTriggerFilter(
            BatchJob job, Media media, Action action) {
        var trigger = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.TRIGGER, job, media, action);
        return createTriggerFilter(trigger);
    }


    private static Predicate<Track> createTriggerFilter(String triggerProperty) {
        if (triggerProperty == null || triggerProperty.isBlank()) {
            return ALL_MATCH;
        }
        var triggerParts = triggerProperty.split("=", 2);
        if (triggerParts.length != 2) {
            throw new WfmProcessingException(
                "The \"TRIGGER\" property did not contain \"=\".");
        }
        var triggerKey = triggerParts[0];
        if (triggerKey.isBlank()) {
            throw new WfmProcessingException(
                "The \"TRIGGER\" property did not contain any text to the "
                            + "left of the \"=\" character.");
        }
        var triggerValue = triggerParts[1];
        return t -> triggerValue.equals(t.getTrackProperties().get(triggerKey));
    }


    private Stream<Track> getTracks(int taskIdx, Media media, DetectionContext context) {
        return _inProgressJobs.getTracksStream(context.getJobId(), media.getId(), taskIdx, 0);
    }
}
