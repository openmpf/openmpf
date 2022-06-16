/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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


package org.mitre.mpf.wfm.data.entities.persistent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.interop.JsonIssueDetails;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// Deprecated. outputEnabled is no longer a batch job property. Left for backwards compatibility.
@JsonIgnoreProperties({ "outputEnabled" })
public class BatchJobImpl implements BatchJob {

    private final long _id;
    @Override
    public long getId() { return _id; }


    private BatchJobStatusType _status = BatchJobStatusType.INITIALIZED;
    @Override
    public BatchJobStatusType getStatus() { return _status; }
    public void setStatus(BatchJobStatusType status) { _status = status; }


    private final JobPipelineElements _pipelineElements;
    @Override
    public JobPipelineElements getPipelineElements() { return _pipelineElements; }


    private int _currentTaskIndex = 0;
    @Override
    public int getCurrentTaskIndex() { return _currentTaskIndex; }
    public void setCurrentTaskIndex(int currentTaskIndex) { _currentTaskIndex = currentTaskIndex; }


    private final String _externalId;
    @Override
    public Optional<String> getExternalId() { return Optional.ofNullable(_externalId); }


    private final int _priority;
    @Override
    public int getPriority() { return _priority; }


    private final SortedMap<Long, MediaImpl> _media;
    @Override
    public Collection<MediaImpl> getMedia() {
        return Collections.unmodifiableCollection(_media.values());
    }
    @Override
    public MediaImpl getMedia(long mediaId) {
        return _media.get(mediaId);
    }


    @Override
    public void addDerivativeMedia(Media media) {
        _media.put(media.getId(), MediaImpl.toMediaImpl(media));
    }


    private final ImmutableMap<String, ImmutableMap<String, String>> _overriddenAlgorithmProperties;
    @Override
    public ImmutableMap<String, ImmutableMap<String, String>> getOverriddenAlgorithmProperties() {
        return _overriddenAlgorithmProperties;
    }


    private final ImmutableMap<String, String> _jobProperties;
    @Override
    public ImmutableMap<String, String> getJobProperties() { return _jobProperties; }


    @Override
    @JsonIgnore
    public boolean isCancelled() {
        switch (getStatus()) {
            case CANCELLED:
            case CANCELLING:
            case CANCELLED_BY_SHUTDOWN:
                return true;
            default:
                return false;
        }
    }

    private final String _callbackUrl;
    @Override
    public Optional<String> getCallbackUrl() { return Optional.ofNullable(_callbackUrl); }


    private final String _callbackMethod;
    @Override
    public Optional<String> getCallbackMethod() { return Optional.ofNullable(_callbackMethod); }


    private final Map<Long, Set<JsonIssueDetails>> _errors;
    @Override
    public Map<Long, Set<JsonIssueDetails>> getErrors() {
        return Collections.unmodifiableMap(_errors);
    }
    public void addError(long mediaId, String source, String code, String message) {
        var details = new JsonIssueDetails(source, code, message);
        _errors.computeIfAbsent(mediaId, k -> new HashSet<>()).add(details);
    }


    private final Map<Long, Set<JsonIssueDetails>> _warnings;
    @Override
    public Map<Long, Set<JsonIssueDetails>> getWarnings() {
        return Collections.unmodifiableMap(_warnings);
    }
    public void addWarning(long mediaId, String source, String code, String message) {
        var details = new JsonIssueDetails(source, code, message);
        _warnings.computeIfAbsent(mediaId, k -> new HashSet<>()).add(details);
    }


    // Contains values of system properties at the time the job was created so that if the properties are changed
    // during the execution of the job, the job will still have access to the original property values.
    private final SystemPropertiesSnapshot _systemPropertiesSnapshot;
    @Override
    public SystemPropertiesSnapshot getSystemPropertiesSnapshot() { return _systemPropertiesSnapshot; }


    private final List<DetectionProcessingError> _detectionProcessingErrors;
    @Override
    public List<DetectionProcessingError> getDetectionProcessingErrors() {
        return Collections.unmodifiableList(_detectionProcessingErrors);
    }
    public void addDetectionProcessingError(DetectionProcessingError error) {
        _detectionProcessingErrors.add(error);
    }


    public BatchJobImpl(
            long id,
            String externalId,
            SystemPropertiesSnapshot systemPropertiesSnapshot,
            JobPipelineElements pipelineElements,
            int priority,
            String callbackUrl,
            String callbackMethod,
            Collection<MediaImpl> media,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> overriddenAlgorithmProperties) {
        this(id, externalId, systemPropertiesSnapshot, pipelineElements, priority, callbackUrl,
             callbackMethod, media, jobProperties, overriddenAlgorithmProperties, List.of(), Map.of(), Map.of());
    }


    @JsonCreator
    BatchJobImpl(
            @JsonProperty("id") long id,
            @JsonProperty("externalId") String externalId,
            @JsonProperty("systemPropertiesSnapshot") SystemPropertiesSnapshot systemPropertiesSnapshot,
            @JsonProperty("pipelineElements") JobPipelineElements pipelineElements,
            @JsonProperty("priority") int priority,
            @JsonProperty("callbackUrl") String callbackUrl,
            @JsonProperty("callbackMethod") String callbackMethod,
            @JsonProperty("media") Collection<MediaImpl> media,
            @JsonProperty("jobProperties") Map<String, String> jobProperties,
            @JsonProperty("overriddenAlgorithmProperties")
                    Map<String, ? extends Map<String, String>> overriddenAlgorithmProperties,
            @JsonProperty("detectionProcessingErrors") Collection<DetectionProcessingError> detectionProcessingErrors,
            @JsonProperty("errors") Map<Long, Set<JsonIssueDetails>> errors,
            @JsonProperty("warnings") Map<Long, Set<JsonIssueDetails>> warnings) {
        _id = id;
        _externalId = externalId;
        _systemPropertiesSnapshot = systemPropertiesSnapshot;
        _pipelineElements = pipelineElements;
        _priority = priority;
        _callbackUrl = StringUtils.trimToNull(callbackUrl);
        _callbackMethod = TextUtils.trimToNullAndUpper(callbackMethod);

        _media = media.stream()
                .collect(Collectors.toMap(
                        MediaImpl::getId,
                        Function.identity(),
                        (u,v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); },
                        TreeMap::new));

        _jobProperties = ImmutableMap.copyOf(jobProperties);

        _overriddenAlgorithmProperties = overriddenAlgorithmProperties
                .entrySet()
                .stream()
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> ImmutableMap.copyOf(e.getValue())));
        _detectionProcessingErrors = new ArrayList<>(detectionProcessingErrors);

        _errors = new HashMap<>();
        // Can't just pass errors to HashMap constructor because we also want to copy the sets.
        errors.forEach((k, v) -> _errors.put(k, new HashSet<>(v)));

        _warnings = new HashMap<>();
        // Can't just pass warnings to HashMap constructor because we also want to copy the sets.
        warnings.forEach((k, v) -> _warnings.put(k, new HashSet<>(v)));
    }
}
