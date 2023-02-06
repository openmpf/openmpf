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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

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
        var trigger = properties.apply(MpfConstants.TRIGGER);
        if (trigger == null || trigger.isBlank()) {
            return;
        }

        var triggerParts = trigger.split("=", 2);
        if (triggerParts.length != 2) {
            throw new WfmProcessingException(
                "The \"TRIGGER\" property did not contain \"=\".");
        }
        if (triggerParts[0].equals("")) {
            throw new WfmProcessingException(
                "The \"TRIGGER\" property did not contain any text to the "
                            + "left of the \"=\" character.");
        }
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
        if (trigger == null) {
            return handleNoCurrentTrigger(job, media, context);
        }

        var triggerFilter = createTriggerFilter(trigger);
        var results = findPreviousUnTriggered(job, media, context, triggerFilter);
        context.getPreviousTracks()
            .stream()
            .filter(triggerFilter)
            .forEach(results::add);
        if (results.isEmpty()) {
            _inProgressJobs.setProcessedAction(
                context.getJobId(),
                media.getId(),
                context.getTaskIndex(),
                context.getActionIndex());
        }
        return results;
    }


    private Collection<Track> handleNoCurrentTrigger(
            BatchJob job, Media media, DetectionContext context) {
        var prevUnTriggered = findPreviousUnTriggered(job, media, context, t -> true);
        if (prevUnTriggered.isEmpty()) {
            return context.getPreviousTracks();
        }
        else {
            var result = prevUnTriggered;
            result.addAll(context.getPreviousTracks());
            return result;
        }
    }

    private List<Track> findPreviousUnTriggered(
            BatchJob job, Media media, DetectionContext context,
            Predicate<Track> currentFilter) {
        var prevUnTriggered = new ArrayList<Track>();
        for (var prevTaskIdx = context.getTaskIndex() - 1; prevTaskIdx > 0; prevTaskIdx--) {
            var prevTrigger = getTrigger(job, media, prevTaskIdx);
            if (prevTrigger.isEmpty()) {
                // Since the previous action didn't use a trigger, it would have processed all
                // un-triggered tracks from earlier in the pipeline.
                return prevUnTriggered;
            }
            var prevTracks = _inProgressJobs.getTracks(
                    job.getId(), media.getId(), prevTaskIdx - 1, 0);

            prevTracks.stream()
                .filter(createTriggerFilter(prevTrigger.get()).negate())
                .filter(currentFilter)
                .forEach(prevUnTriggered::add);
        }
        return prevUnTriggered;
    }


    private Optional<String> getTrigger(BatchJob job, Media media, int taskIdx) {
        var action = job.getPipelineElements().getAction(taskIdx, 0);
        var trigger = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.TRIGGER, job, media, action);
        return Optional.ofNullable(trigger)
            .filter(t -> !t.isBlank());
    }


    private static Predicate<Track> createTriggerFilter(String triggerProperty) {
        var triggerParts = triggerProperty.split("=", 2);
        var triggerKey = triggerParts[0];
        var triggerValue = triggerParts[1];
        return t -> triggerValue.equals(t.getTrackProperties().get(triggerKey));
    }
}
