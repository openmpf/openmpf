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

import java.util.HashSet;
import java.util.Set;
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
        var trigger = parseTriggerProperty(properties.apply(MpfConstants.TRIGGER));
        if (trigger == ALL_MATCH) {
            return;
        }
        var feedForwardType = properties.apply(MediaSegmenter.FEED_FORWARD_TYPE);
        if (!MediaSegmenter.feedForwardIsEnabled(feedForwardType)) {
            throw new WfmProcessingException(
                    "The \"TRIGGER\" property was set, but feed forward was not enabled.");
        }
    }


    public Stream<Track> getTriggeredTracks(Media media, DetectionContext context) {
        var trigger = context.getAlgorithmProperties().get(MpfConstants.TRIGGER);
        return Stream.concat(
                    context.getPreviousTracks().stream(),
                    findPreviousUnTriggered(media, context))
            .filter(parseTriggerProperty(trigger));
    }


    private Stream<Track> findPreviousUnTriggered(
            Media media, DetectionContext context) {

        record TriggerEntry(int taskIdx, Predicate<Track> trigger) { }

        var job = _inProgressJobs.getJob(context.getJobId());
        var triggerEntries = Stream.<TriggerEntry>builder();

        for (var creationTaskIdx = context.getTaskIndex() - 2;
                creationTaskIdx >= 0;
                creationTaskIdx--) {

            var triggerFilter = createWasEverTriggeredFilter(
                    job, media, creationTaskIdx, context.getTaskIndex() - 1);
            if (triggerFilter == ALL_MATCH) {
                // Since the previous task didn't use a trigger, it would have processed all
                // un-triggered tracks from earlier in the pipeline.
                break;
            }
            triggerEntries.add(new TriggerEntry(creationTaskIdx, triggerFilter));
        }
        return triggerEntries.build()
            .flatMap(te -> getTracks(te.taskIdx, media, context).filter(te.trigger.negate()));
    }


    public Predicate<Track> createWasEverTriggeredFilter(
            BatchJob job, Media media, int creationTaskIdx, int lastTaskIdx) {
        var fullTrigger = NONE_MATCH;
        for (int taskIdx = creationTaskIdx + 1; taskIdx <= lastTaskIdx; taskIdx++) {
            var taskTrigger = createTaskTrigger(job, media, taskIdx);
            if (taskTrigger == ALL_MATCH) {
                return ALL_MATCH;
            }
            fullTrigger = taskTrigger.or(fullTrigger);
        }
        return fullTrigger;
    }


    private Predicate<Track> createTaskTrigger(BatchJob job, Media media, int taskIdx) {
        var task = job.getPipelineElements().getTask(taskIdx);
        var taskTrigger = NONE_MATCH;
        for (var action : job.getPipelineElements().getActionsInOrder(task)) {
            var algo = job.getPipelineElements().getAlgorithm(action.algorithm());
            if (algo.actionType() == ActionType.MARKUP) {
                continue;
            }
            var actionTrigger = parseTriggerProperty(job, media, action);
            if (actionTrigger == ALL_MATCH) {
                return ALL_MATCH;
            }
            taskTrigger = actionTrigger.or(taskTrigger);
        }
        return taskTrigger;
    }


    private Predicate<Track> parseTriggerProperty(
            BatchJob job, Media media, Action action) {
        var trigger = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.TRIGGER, job, media, action);
        return parseTriggerProperty(trigger);
    }

    private static Predicate<Track> parseTriggerProperty(String triggerProperty) {
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
        var triggerValues = parseTriggerValue(triggerParts[1]);
        if (triggerValues.size() == 1) {
            var triggerValue = triggerValues.iterator().next();
            return track -> triggerValue.equals(getTrackTrigger(track, triggerKey));
        }
        else {
            return track -> triggerValues.contains(getTrackTrigger(track, triggerKey));
        }
    }


    private static String getTrackTrigger(Track track, String triggerKey) {
        var propValue = track.getTrackProperties().get(triggerKey);
        if (propValue == null) {
            return null;
        }
        return propValue.strip();
    }


    private static Set<String> parseTriggerValue(String trigger) {
        if (!trigger.contains(";") && !trigger.contains("\\")) {
            return Set.of(trigger.strip());
        }

        var values = new HashSet<String>();
        var currentSegment = new StringBuilder();
        boolean inEscapeSequence = false;
        for (int i = 0; i < trigger.length(); i++) {
            char ch = trigger.charAt(i);
            if (inEscapeSequence) {
                inEscapeSequence = false;
                currentSegment.append(ch);
                continue;
            }
            switch (ch) {
                case '\\' -> inEscapeSequence = true;
                case ';' -> {
                    var newValue = currentSegment.toString().strip();
                    if (!newValue.isBlank()) {
                        values.add(newValue);
                    }
                    currentSegment.setLength(0);
                }
                default -> currentSegment.append(ch);
            }
        }

        if (!currentSegment.isEmpty()) {
            var newValue = currentSegment.toString().strip();
            if (!newValue.isBlank()) {
                values.add(newValue);
            }
        }
        return values;
    }


    private Stream<Track> getTracks(int taskIdx, Media media, DetectionContext context) {
        return _inProgressJobs.getTracksStream(context.getJobId(), media.getId(), taskIdx, 0);
    }
}
