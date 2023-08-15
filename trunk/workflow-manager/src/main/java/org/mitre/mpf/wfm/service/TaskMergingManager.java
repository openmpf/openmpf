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


package org.mitre.mpf.wfm.service;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.Message;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.springframework.stereotype.Component;


@Component
public class TaskMergingManager {

    private static final String BREAD_CRUMB_ID = "breadcrumbId";

    // Camel will automatically add a bread crumb if one is not included in the message headers.
    // Adding a prefix to the bread crumb makes it possible to identify which bread crumbs were
    // added by this class.
    private static final String CUSTOM_BREAD_CRUMB_PREFIX = "mpf-";

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final ConcurrentMap<Long, ConcurrentMap<UUID, String>> _trackSourceAlgos
            = new ConcurrentHashMap<>();

    @Inject
    TaskMergingManager(
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil) {
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
    }


    public RequestTaskMergingContext getRequestContext(
            BatchJob job, Media media, int taskIdx, int actionIdx) {
        if (needsBreadCrumb(job, media, taskIdx, actionIdx)) {
            return (m, t) -> addBreadCrumb(m, t, job.getId());
        }
        else {
            return (m, t) -> m;
        }
    }


    public ResponseTaskMergingContext getResponseContext(
            BatchJob job,
            Media media,
            int taskIdx,
            int actionIdx,
            Map<String, Object> headers) {

        var breadCrumbCtx = getBreadCrumbContext(job, headers);
        if (breadCrumbCtx.isPresent()) {
            return breadCrumbCtx.get();
        }

        var pipelineElements = job.getPipelineElements();
        var algo = getTransitiveMergeTargets(job, media, taskIdx, actionIdx)
                .min()
                .stream()
                .mapToObj(ti -> pipelineElements.getAlgorithm(ti, 0))
                .findAny()
                .orElseGet(() -> pipelineElements.getAlgorithm(taskIdx, actionIdx));
        return new ResponseTaskMergingContext(algo);
    }


    public void clearJob(long jobId) {
        // When no errors occur, there will not be an entry by the time this method is called.
        _trackSourceAlgos.remove(jobId);
    }


    public boolean isMergeSource(BatchJob job, Media media, int taskIdx, int actionIdx) {
        if (taskIdx == 0) {
            return false;
        }
        var action = job.getPipelineElements().getAction(taskIdx, actionIdx);
        return isMergeSource(job, media, action);
    }


    private boolean isMergeSource(BatchJob job, Media media, Action action) {
        var mergeProperty = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY, job, media, action);
        return Boolean.parseBoolean(mergeProperty);
    }


    public boolean isMergeTarget(BatchJob job, Media media, int taskIdx) {
        var pipelineElements = job.getPipelineElements();
        int lastDetectionTaskIdx = pipelineElements.getLastDetectionTaskIdx();

        for (int futureTaskIdx = taskIdx + 1;
                futureTaskIdx < lastDetectionTaskIdx;
                futureTaskIdx++) {
            var futureTask = pipelineElements.getTask(futureTaskIdx);
            var taskAppliesToMedia = false;
            for (var futureAction : pipelineElements.getActionsInOrder(futureTask)) {
                if (_aggregateJobPropertiesUtil.actionAppliesToMedia(job, media, futureAction)) {
                    taskAppliesToMedia = true;
                    if (isMergeSource(job, media, futureAction)) {
                        return true;
                    }
                }
            }
            if (taskAppliesToMedia) {
                return false;
            }
        }
        return false;
    }


    public IntStream getTransitiveMergeTargets(
            BatchJob job, Media media, int srcTaskIdx, int srcActionIdx) {
        return Stream.iterate(
                getDirectMergeTarget(job, media, srcTaskIdx, srcActionIdx),
                OptionalInt::isPresent,
                p -> getDirectMergeTarget(job, media, p.getAsInt(), 0))
            .mapToInt(OptionalInt::getAsInt);
    }


    private Message addBreadCrumb(
            Message message, Track feedForwardTrack, long jobId) {
        var trackId = UUID.randomUUID();
        _trackSourceAlgos
                .computeIfAbsent(jobId, k -> new ConcurrentHashMap<>())
                .put(trackId, feedForwardTrack.getMergedAlgorithm());
        message.setHeader(BREAD_CRUMB_ID, CUSTOM_BREAD_CRUMB_PREFIX + trackId);
        return message;
    }


    private static Optional<UUID> parseBreadCrumb(Map<String, Object> headers) {
        return Optional.ofNullable(headers.get(BREAD_CRUMB_ID))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(hv -> hv.startsWith(CUSTOM_BREAD_CRUMB_PREFIX))
            .map(hv -> hv.substring(CUSTOM_BREAD_CRUMB_PREFIX.length()))
            .map(hv -> {
                try {
                    return UUID.fromString(hv);
                }
                catch (IllegalArgumentException e) {
                    return null;
                }
            });
    }


    private Optional<ResponseTaskMergingContext> getBreadCrumbContext(
            BatchJob job, Map<String, Object> headers) {
        var uuid = parseBreadCrumb(headers).orElse(null);
        if (uuid == null) {
            return Optional.empty();
        }
        var jobMap = _trackSourceAlgos.get(job.getId());
        if (jobMap == null)  {
            return Optional.empty();
        }
        var algoName = jobMap.get(uuid);
        if (algoName == null) {
            return Optional.empty();
        }
        var algo = job.getPipelineElements().getAlgorithm(algoName);

        var result = new ResponseTaskMergingContext(algo) {
            @Override
            public void close() {
                jobMap.remove(uuid);
                // Need to use computeIfPresent so that checking if the job specific sub-map is
                // empty and removal from _trackSourceAlgos occur atomically.
                _trackSourceAlgos.computeIfPresent(
                        job.getId(),
                        // Returning null causes the entry to be removed from the map.
                        (k, v) -> v.isEmpty() ? null : v);
            }
        };
        return Optional.of(result);
    }




    public interface RequestTaskMergingContext {
        public Message addBreadCrumbIfNeeded(Message message, Track track);
    }


    public static class ResponseTaskMergingContext implements AutoCloseable {

        private final Algorithm _algorithm;

        public ResponseTaskMergingContext(Algorithm algorithm) {
            _algorithm = algorithm;
        }

        public Algorithm getAlgorithm() {
            return _algorithm;
        }

        @Override
        public void close() {
            // Overridden to get rid of "throws Exception" from the base AutoCloseable.close().
        }
    }


    private OptionalInt getDirectMergeTarget(
            BatchJob job, Media media, int srcTaskIdx, int srcActionIdx) {
        if (!isMergeSource(job, media, srcTaskIdx, srcActionIdx)) {
            return OptionalInt.empty();
        }

        for (int prevTaskIdx = srcTaskIdx - 1; prevTaskIdx >= 0; prevTaskIdx--) {
            var prevAction = job.getPipelineElements().getAction(prevTaskIdx, 0);
            if (_aggregateJobPropertiesUtil.actionAppliesToMedia(job, media, prevAction)) {
                return OptionalInt.of(prevTaskIdx);
            }
        }
        return OptionalInt.of(0);
    }


    private boolean needsBreadCrumb(BatchJob job, Media media, int taskIdx, int actionIdx) {
        return getTransitiveMergeTargets(job, media, taskIdx, actionIdx)
            .min()
            .stream()
            .mapToObj(ti -> job.getPipelineElements().getAction(ti, 0))
            .map(a -> _aggregateJobPropertiesUtil.getValue(MpfConstants.TRIGGER, job, media, a))
            .anyMatch(t -> t != null && !t.isBlank());
    }
}
