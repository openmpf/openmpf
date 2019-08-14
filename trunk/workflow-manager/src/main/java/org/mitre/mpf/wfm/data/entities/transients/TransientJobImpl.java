/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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


package org.mitre.mpf.wfm.data.entities.transients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.*;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.*;
import java.util.function.Function;

public class TransientJobImpl implements TransientJob {

    private final long _id;
    @Override
    public long getId() { return _id; }


    private BatchJobStatusType _status = BatchJobStatusType.INITIALIZED;
    @Override
    public BatchJobStatusType getStatus() { return _status; }
    public void setStatus(BatchJobStatusType status) { _status = status; }


    private final TransientPipeline _transientPipeline;
    @Override
    public TransientPipeline getTransientPipeline() { return _transientPipeline; }


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


    private final boolean _outputEnabled;
    @Override
    public boolean isOutputEnabled() { return _outputEnabled; }


    private final ImmutableSortedMap<Long, TransientMediaImpl> _media;
    @Override
    public ImmutableCollection<TransientMediaImpl> getMedia() { return _media.values(); }
    @Override
    public TransientMediaImpl getMedia(long mediaId) {
        return _media.get(mediaId);
    }


    private final ImmutableMap<String, ImmutableMap<String, String>> _overriddenAlgorithmProperties;
    @Override
    public ImmutableMap<String, ImmutableMap<String, String>> getOverriddenAlgorithmProperties() {
        return _overriddenAlgorithmProperties;
    }


    private final ImmutableMap<String, String> _jobProperties;
    @Override
    public ImmutableMap<String, String> getJobProperties() { return _jobProperties; }


    private boolean _cancelled;
    @Override
    public boolean isCancelled() { return _cancelled; }
    public void setCancelled(boolean isCancelled) { _cancelled = isCancelled; }


    private final String _callbackUrl;
    @Override
    public Optional<String> getCallbackUrl() { return Optional.ofNullable(_callbackUrl); }


    private final String _callbackMethod;
    @Override
    public Optional<String> getCallbackMethod() { return Optional.ofNullable(_callbackMethod); }


    private final Set<String> _errors = new HashSet<>();
    @Override
    public Set<String> getErrors() {
        return Collections.unmodifiableSet(_errors);
    }
    public void addError(String errorMsg) {
        _errors.add(errorMsg);
    }


    private final Set<String> _warnings = new HashSet<>();
    @Override
    public Set<String> getWarnings() {
        return Collections.unmodifiableSet(_warnings);
    }
    public void addWarning(String warningMsg) {
        _warnings.add(warningMsg);
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


    public TransientJobImpl(
            long id,
            String externalId,
            SystemPropertiesSnapshot systemPropertiesSnapshot,
            TransientPipeline transientPipeline,
            int priority,
            boolean outputEnabled,
            String callbackUrl,
            String callbackMethod,
            Collection<TransientMediaImpl> media,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> overriddenAlgorithmProperties) {
        this(id, externalId, systemPropertiesSnapshot, transientPipeline, priority, outputEnabled, callbackUrl,
             callbackMethod, media, jobProperties, overriddenAlgorithmProperties, List.of());
    }


    @JsonCreator
    TransientJobImpl(
            @JsonProperty("id") long id,
            @JsonProperty("externalId") String externalId,
            @JsonProperty("systemPropertiesSnapshot") SystemPropertiesSnapshot systemPropertiesSnapshot,
            @JsonProperty("transientPipeline") TransientPipeline transientPipeline,
            @JsonProperty("priority") int priority,
            @JsonProperty("outputEnabled") boolean outputEnabled,
            @JsonProperty("callbackUrl") String callbackUrl,
            @JsonProperty("callbackMethod") String callbackMethod,
            @JsonProperty("media") Collection<TransientMediaImpl> media,
            @JsonProperty("jobProperties") Map<String, String> jobProperties,
            @JsonProperty("overriddenAlgorithmProperties")
                    Map<String, ? extends Map<String, String>> overriddenAlgorithmProperties,
            @JsonProperty("detectionProcessingErrors") Collection<DetectionProcessingError> detectionProcessingErrors) {
        _id = id;
        _externalId = externalId;
        _systemPropertiesSnapshot = systemPropertiesSnapshot;
        _transientPipeline = transientPipeline;
        _priority = priority;
        _outputEnabled = outputEnabled;
        _callbackUrl = StringUtils.trimToNull(callbackUrl);
        _callbackMethod = TextUtils.trimToNullAndUpper(callbackMethod);

        _media = media.stream()
                .collect(ImmutableSortedMap.toImmutableSortedMap(
                        Comparator.naturalOrder(),
                        TransientMediaImpl::getId,
                        Function.identity()));

        _jobProperties = ImmutableMap.copyOf(jobProperties);

        _overriddenAlgorithmProperties = overriddenAlgorithmProperties
                .entrySet()
                .stream()
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> ImmutableMap.copyOf(e.getValue())));
        _detectionProcessingErrors = new ArrayList<>(detectionProcessingErrors);
    }
}
