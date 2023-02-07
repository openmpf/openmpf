/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import javax.inject.Inject;

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


    public Collection<Track> getTriggeredTracks(Media media, DetectionContext context) {
        if (context.getTaskIndex() == 0) {
            return context.getPreviousTracks();
        }

        var trigger = context.getAlgorithmProperties()
            .stream()
            .filter(ap -> ap.getPropertyName().equals(MpfConstants.TRIGGER))
            .map(ap -> ap.getPropertyValue())
            .filter(pv -> pv != null && !pv.isBlank())
            .findAny()
            .orElse(null);

        var job = _inProgressJobs.getJob(context.getJobId());
        var triggerFilter = createTriggerFilter(trigger);
        var ctxTracks = context.getPreviousTracks()
            .stream()
            .filter(triggerFilter);
        var prevTaskTracks = findPreviousUnTriggered(job, media, context, triggerFilter);
        var results = Stream.concat(ctxTracks, prevTaskTracks).toList();

        if (results.isEmpty()) {
            _inProgressJobs.setProcessedAction(
                context.getJobId(),
                media.getId(),
                context.getTaskIndex(),
                context.getActionIndex());
        }
        return results;
    }


    private Stream<Track> findPreviousUnTriggered(
            BatchJob job, Media media, DetectionContext context,
            Predicate<Track> currentFilter) {
        var prevUnTriggered = Stream.<Track>builder();
        for (var prevTaskIdx = context.getTaskIndex() - 1; prevTaskIdx > 0; prevTaskIdx--) {
            var prevTrigger = createTriggerFilter(job, media, prevTaskIdx);
            if (prevTrigger == ALL_MATCH) {
                // Since the previous action didn't use a trigger, it would have processed all
                // un-triggered tracks from earlier in the pipeline.
                return prevUnTriggered.build();
            }
            var prevTracks = _inProgressJobs.getTracks(
                    job.getId(), media.getId(), prevTaskIdx - 1, 0);

            prevTracks.stream()
                .filter(prevTrigger.negate())
                .filter(currentFilter)
                .forEach(prevUnTriggered);
        }
        return prevUnTriggered.build();
    }


    private Predicate<Track> createTriggerFilter(BatchJob job, Media media, int taskIdx) {
        var action = job.getPipelineElements().getAction(taskIdx, 0);
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
}
