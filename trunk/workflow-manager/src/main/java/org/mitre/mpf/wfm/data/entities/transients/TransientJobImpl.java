/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

import com.google.common.collect.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;

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


    private final TransientPipeline _pipeline;
    @Override
    public TransientPipeline getPipeline() { return _pipeline; }


    private int _currentStage;
    @Override
    public int getCurrentStage() { return _currentStage; }
    public void setCurrentStage(int currentStage) { _currentStage = currentStage; }


    private final String _externalId;
    @Override
    public String getExternalId() { return _externalId; }


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


    private final ImmutableTable<String, String, String> _overriddenAlgorithmProperties;
    @Override
    public ImmutableTable<String, String, String> getOverriddenAlgorithmProperties() {
        return _overriddenAlgorithmProperties;
    }


    private final ImmutableMap<String, String> _overriddenJobProperties;
    @Override
    public ImmutableMap<String, String> getOverriddenJobProperties() { return _overriddenJobProperties; }


    private boolean _cancelled;
    @Override
    public boolean isCancelled() { return _cancelled; }
    public void setCancelled(boolean isCancelled) { _cancelled = isCancelled; }


    private final String _callbackUrl;
    @Override
    public String getCallbackUrl() { return _callbackUrl; }


    private final String _callbackMethod;
    @Override
    public String getCallbackMethod() { return _callbackMethod; }


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


    private final List<DetectionProcessingError> _detectionProcessingErrors = new ArrayList<>();
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
            TransientPipeline pipeline,
            int priority,
            boolean outputEnabled,
            String callbackUrl,
            String callbackMethod,
            List<TransientMediaImpl> media,
            Map<String, String> jobProperties,
            Map<String, Map<String, String>> algorithmProperties) {
        _id = id;
        _externalId = externalId;
        _systemPropertiesSnapshot = systemPropertiesSnapshot;
        _pipeline = pipeline;
        _priority = priority;
        _outputEnabled = outputEnabled;
        _callbackUrl = callbackUrl;
        _callbackMethod = callbackMethod;

        _media = media.stream()
                .collect(ImmutableSortedMap.toImmutableSortedMap(
                        Comparator.naturalOrder(),
                        TransientMediaImpl::getId,
                        Function.identity()));

        _overriddenJobProperties = ImmutableMap.copyOf(jobProperties);

        ImmutableTable.Builder<String, String, String> tableBuilder = ImmutableTable.builder();
        for (Map.Entry<String, Map<String, String>> algoEntry : algorithmProperties.entrySet()) {
            for (Map.Entry<String, String> algoProp : algoEntry.getValue().entrySet()) {
                tableBuilder.put(algoEntry.getKey(), algoProp.getKey(), algoProp.getValue());
            }
        }
        _overriddenAlgorithmProperties = tableBuilder.build();
    }
}
