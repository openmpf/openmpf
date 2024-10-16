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


package org.mitre.mpf.wfm.util;

import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;

import com.google.common.collect.AbstractSequentialIterator;
import com.google.common.collect.Streams;

public class JobPartsIter {

    private JobPartsIter(){
    }

    public static Iterable<JobPart> of(BatchJob job) {
        return () -> stream(job).iterator();
    }

    public static Stream<JobPart> stream(BatchJob job) {
        return job.getMedia().stream().flatMap(m -> stream(job, m));
    }


    public static Iterable<JobPart> of(BatchJob job, Media media) {
        return () -> new JobPartsFixedMediaIter(job, media);
    }

    public static Stream<JobPart> stream(BatchJob job, Media media) {
        return Streams.stream(new JobPartsFixedMediaIter(job, media));
    }

    public static Stream<JobPart> taskStream(BatchJob job, int taskIdx) {
        var task = job.getPipelineElements().getTask(taskIdx);
        var hasSingleAction = task.actions().size() == 1;
        var hasSingleMedia = job.getMedia().size() == 1;
        if (hasSingleMedia && hasSingleAction) {
            return Stream.of(new JobPart(job, job.getMedia().iterator().next(), 0, taskIdx, 0));
        }
        if (hasSingleMedia) {
            return createJobPartsForTaskAndMedia(
                    job, job.getMedia().iterator().next(), 0, taskIdx, task.actions().size());
        }
        if (hasSingleAction) {
            return Streams.mapWithIndex(job.getMedia().stream(),
                    (m, mi) -> new JobPart(job, m, (int) mi, taskIdx, 0));
        }

        return Streams.mapWithIndex(
                job.getMedia().stream(),
                (m, mi) -> createJobPartsForTaskAndMedia(
                        job, m, mi, taskIdx, task.actions().size()))
            .flatMap(Function.identity());
    }

    public static Iterable<JobPart> task(BatchJob job, int taskIdx) {
        return taskStream(job, taskIdx)::iterator;
    }

    private static Stream<JobPart> createJobPartsForTaskAndMedia(
            BatchJob job, Media media, long mediaIdx, int taskIdx, int actionCount) {
        return IntStream.range(0, actionCount)
            .mapToObj(ai -> new JobPart(job, media, (int) mediaIdx, taskIdx, ai));
    }


    private static class JobPartsFixedMediaIter extends AbstractSequentialIterator<JobPart> {

        public JobPartsFixedMediaIter(BatchJob job, Media media) {
            this(job, media, getMediaIndex(job, media));
        }

        public JobPartsFixedMediaIter(BatchJob job, Media media, int mediaIndex) {
            // parent media have a creation task of -1, so +1 gets task 0
            super(new JobPart(job, media, mediaIndex, media.getCreationTask() + 1, 0));
        }

        @Override
        protected JobPart computeNext(JobPart previous) {
            var job = previous.job();
            var pipelineParts = job.getPipelineElements();

            var nextActionIndex = previous.actionIndex() + 1;
            if (previous.task().actions().size() > nextActionIndex) {
                return new JobPart(
                        job,
                        previous.media(),
                        previous.mediaIndex(),
                        previous.taskIndex(),
                        nextActionIndex);
            }

            var nextTaskIdx = previous.taskIndex() + 1;
            if (pipelineParts.getTaskCount() > nextTaskIdx) {
                return new JobPart(
                        job,
                        previous.media(),
                        previous.mediaIndex(),
                        nextTaskIdx,
                        0);
            }

            return null;
        }
    }

    private static int getMediaIndex(BatchJob job, Media targetMedia) {
        int i = 0;
        for (var media : job.getMedia()) {
            if (media.getId() == targetMedia.getId()) {
                return i;
            }
            i++;
        }
        throw new IllegalArgumentException("targetMedia does not belong to job.");
    }
}
