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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;


public class StreamingJobImpl implements StreamingJob {

    private final long _id;
    @Override
    public long getId() { return _id; }


    private StreamingJobStatus _jobStatus = new StreamingJobStatus(StreamingJobStatusType.INITIALIZING);
    @Override
    public StreamingJobStatus getJobStatus() { return _jobStatus; }
    public void setJobStatus(StreamingJobStatus jobStatus) { _jobStatus = jobStatus; }


    private final JobPipelineElements _pipelineElements;
    @Override
    public JobPipelineElements getPipelineElements() { return _pipelineElements; }


    private final String _externalId;
    @Override
    public Optional<String> getExternalId() { return Optional.ofNullable(_externalId); }


    private final int _priority;
    @Override
    public int getPriority() { return _priority; }


    private final long _stallTimeout;
    @Override
    public long getStallTimeout() { return _stallTimeout; }


    private final boolean _outputEnabled;
    @Override
    public boolean isOutputEnabled() { return _outputEnabled; }


    private final String _outputObjectDirectory;
    @Override
    public String getOutputObjectDirectory() { return _outputObjectDirectory; }


    private final MediaStreamInfo _stream;
    @Override
    public MediaStreamInfo getStream() { return _stream; }


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


    private final String _healthReportCallbackURI;
    @Override
    public Optional<String> getHealthReportCallbackURI() {
        return Optional.ofNullable(_healthReportCallbackURI);
    }


    private final String _summaryReportCallbackURI;
    @Override
    public Optional<String> getSummaryReportCallbackURI() { return Optional.ofNullable(_summaryReportCallbackURI); }


    private long _lastActivityFrame = -1;
    @Override
    public long getLastActivityFrame() { return _lastActivityFrame; }
    public void setLastActivityFrame(long frame) { _lastActivityFrame = frame; }


    private Instant _lastActivityTime;
    @Override
    public Instant getLastActivityTime() { return _lastActivityTime; }
    public void setLastActivityTime(Instant time) { _lastActivityTime = time; }


    private boolean _cleanupEnabled;
    @Override
    public boolean isCleanupEnabled() { return _cleanupEnabled; }
    public void setCleanupEnabled(boolean cleanupEnabled) { _cleanupEnabled = cleanupEnabled; }


    @JsonCreator
    public StreamingJobImpl(
            @JsonProperty("id") long id,
            @JsonProperty("externalId") String externalId,
            @JsonProperty("pipelineElements") JobPipelineElements pipelineElements,
            @JsonProperty("stream") MediaStreamInfo stream,
            @JsonProperty("priority") int priority,
            @JsonProperty("stallTimeout") long stallTimeout,
            @JsonProperty("outputEnabled") boolean outputEnabled,
            @JsonProperty("outputObjectDirectory") String outputObjectDirectory,
            @JsonProperty("healthReportCallbackURI") String healthReportCallbackURI,
            @JsonProperty("summaryReportCallbackURI") String summaryReportCallbackURI,
            @JsonProperty("jobProperties") Map<String, String> jobProperties,
            @JsonProperty("overriddenAlgorithmProperties")
                    Map<String, Map<String, String>> overriddenAlgorithmProperties) {
        _id = id;
        _externalId = externalId;
        _pipelineElements = pipelineElements;
        _stream = stream;
        _priority = priority;
        _stallTimeout = stallTimeout;
        _outputEnabled = outputEnabled;
        _outputObjectDirectory = outputObjectDirectory;
        _healthReportCallbackURI = healthReportCallbackURI;
        _summaryReportCallbackURI = summaryReportCallbackURI;
        _jobProperties = ImmutableMap.copyOf(jobProperties);

        _overriddenAlgorithmProperties = overriddenAlgorithmProperties
                .entrySet()
                .stream()
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> ImmutableMap.copyOf(e.getValue())));
    }
}
