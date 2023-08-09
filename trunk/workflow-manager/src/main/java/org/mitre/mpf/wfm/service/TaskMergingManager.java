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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;

import org.apache.camel.Message;
import org.mitre.mpf.interop.JsonActionOutputObject;
import org.mitre.mpf.wfm.data.Redis;
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

    private final Redis _redis;

    private record AlgoTrackType(String algorithm, String trackType) {}
    private final ConcurrentMap<Long, ConcurrentMap<UUID, AlgoTrackType>> _algoTrackTypes
            = new ConcurrentHashMap<>();

    @Inject
    TaskMergingManager(
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            Redis redis) {
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _redis = redis;
    }


    public RequestTaskMergingContext getRequestContext(
            BatchJob job, Media media, int taskIdx, int actionIdx) {
        if (mergeEnabled(job, media, taskIdx, actionIdx)) {
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
            String responseDetectionType,
            Map<String, Object> headers) {

        var breadCrumbCtx = getBreadCrumbContext(job, headers);
        if (breadCrumbCtx.isPresent()) {
            return breadCrumbCtx.get();
        }

        var mergeTarget = getMergeTarget(job, media, taskIdx, actionIdx, responseDetectionType);
        return new ResponseTaskMergingContext(mergeTarget.algorithm, mergeTarget.trackType);
    }


    public void clearJob(long jobId) {
        // When no errors occur, there will not be an entry by the time this method is called.
        _algoTrackTypes.remove(jobId);
    }


    private boolean mergeEnabled(BatchJob job, Media media, int taskIdx, int actionIdx) {
        if (taskIdx == 0) {
            return false;
        }
        var action = job.getPipelineElements().getAction(taskIdx, actionIdx);
        var mergeProperty = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY, job, media, action);
        return Boolean.parseBoolean(mergeProperty);
    }


    private Message addBreadCrumb(
            Message message, Track feedForwardTrack, long jobId) {
        var jobMap = _algoTrackTypes.computeIfAbsent(jobId, k -> new ConcurrentHashMap<>());
        var trackId = UUID.randomUUID();
        jobMap.put(trackId, new AlgoTrackType(
                feedForwardTrack.getMergedAlgorithm(), feedForwardTrack.getMergedType()));
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
        var jobMap = _algoTrackTypes.get(job.getId());
        if (jobMap == null)  {
            return Optional.empty();
        }
        var algoTrackType = jobMap.get(uuid);
        if (algoTrackType == null) {
            return Optional.empty();
        }

        var result = new ResponseTaskMergingContext(
                    algoTrackType.algorithm, algoTrackType.trackType) {
            @Override
            public void close() {
                jobMap.remove(uuid);
                // Need to use computeIfPresent so that checking if the job specific sub-map is
                // empty and removal from _algoTrackTypes occur atomically.
                _algoTrackTypes.computeIfPresent(
                        job.getId(),
                        // Returning null causes the entry to be removed from the map.
                        (k, v) -> v.isEmpty() ? null : v);
            }
        };
        return Optional.of(result);
    }


    private AlgoTrackType getMergeTarget(
            BatchJob job, Media media, int taskIdx, int actionIdx, String responseDetectionType) {
        var pipelineElements = job.getPipelineElements();
        var algorithm = pipelineElements.getAlgorithm(taskIdx, actionIdx).getName();
        var trackType = responseDetectionType;

        if (!mergeEnabled(job, media, taskIdx, actionIdx)) {
            return new AlgoTrackType(algorithm, trackType);
        }

        for (int prevTaskIdx = taskIdx - 1; prevTaskIdx >= 0; prevTaskIdx--) {
            var prevAction = pipelineElements.getAction(prevTaskIdx, 0);
            if (_aggregateJobPropertiesUtil.actionAppliesToMedia(job, media, prevAction)
                    && !mergeEnabled(job, media, prevTaskIdx, 0)) {

                algorithm = pipelineElements.getAlgorithm(prevTaskIdx, 0)
                        .getName();
                trackType = _redis.getTrackType(job.getId(), media.getId(), prevTaskIdx, 0)
                        .orElse(JsonActionOutputObject.NO_TRACKS_TYPE);
                break;
            }
        }
        return new AlgoTrackType(algorithm, trackType);
    }


    public interface RequestTaskMergingContext {
        public Message addBreadCrumb(Message message, Track track);
    }


    public static class ResponseTaskMergingContext implements AutoCloseable {

        private final String _algorithm;

        private final String _responseDetectionType;

        public ResponseTaskMergingContext(String algorithm, String responseDetectionType) {
            _algorithm = algorithm;
            _responseDetectionType = responseDetectionType;
        }


        public String getAlgorithm() {
            return _algorithm;
        }

        public String getDetectionType() {
            return _responseDetectionType;
        }

        @Override
        public void close() {
        }
    }
}
