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

import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.service.TaskAnnotatorService;
import org.mitre.mpf.wfm.util.JobPartsIter;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;


@Component
public class TrackOutputHelper {

    private final InProgressBatchJobsService _inProgressJobs;

    private final TaskAnnotatorService _taskAnnotatorService;

    @Inject
    TrackOutputHelper(
            InProgressBatchJobsService inProgressJobs,
            TaskAnnotatorService taskAnnotatorService) {
        _inProgressJobs = inProgressJobs;
        _taskAnnotatorService = taskAnnotatorService;
    }


    public record TrackGroupKey(
            int taskIdx,
            int actionIdx,
            ImmutableSet<Annotator> annotators) {
    }

    public record Annotator(int taskIdx, int actionIdx) {
    }


    public ImmutableSetMultimap<TrackGroupKey, Track> getTrackGroups(BatchJob job, Media media) {
        var isNotAnnotated = _taskAnnotatorService.createIsAnnotatedChecker(job, media).negate();
        return JobPartsIter.stream(job, media)
            .flatMap(jp -> _inProgressJobs.getTracksStream(
                    jp.id(), media.getId(), jp.taskIndex(), jp.actionIndex()))
            .filter(isNotAnnotated)
            .collect(ImmutableSetMultimap.toImmutableSetMultimap(
                    TrackOutputHelper::createTrackGroupKey, Function.identity()));
    }

    private static TrackGroupKey createTrackGroupKey(Track track) {
        if (track.getAnnotatedTaskIndices().isEmpty()) {
            return new TrackGroupKey(
                    track.getTaskIndex(), track.getActionIndex(), ImmutableSet.of());
        }

        int originTaskIdx = track.getAnnotatedTaskIndices().first();
        var prevAnnotators = track.getAnnotatedTaskIndices()
                .tailSet(originTaskIdx, false)
                .stream()
                .map(ti -> new Annotator(ti, 0));
        var currentAnnotator = new Annotator(track.getTaskIndex(), track.getActionIndex());

        var combinedAnnotators = Stream.concat(prevAnnotators, Stream.of(currentAnnotator))
                .collect(ImmutableSet.toImmutableSet());
        return new TrackGroupKey(originTaskIdx, 0, combinedAnnotators);
    }
}
