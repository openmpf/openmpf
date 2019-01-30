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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;


public class TransientStreamingJobImpl implements TransientStreamingJob {

    private final long _id;
    @Override
    public long getId() { return _id; }


    private StreamingJobStatus _jobStatus = new StreamingJobStatus(StreamingJobStatusType.INITIALIZING);
    @Override
    public StreamingJobStatus getJobStatus() { return _jobStatus; }
    public void setJobStatus(StreamingJobStatus jobStatus) { _jobStatus = jobStatus; }


    private final TransientPipeline _pipeline;
    @Override
    public TransientPipeline getPipeline() { return _pipeline; }


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


    private final TransientStream _stream;
    @Override
    public TransientStream getStream() { return _stream; }


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


    public TransientStreamingJobImpl(
            long id,
            String externalId,
            TransientPipeline pipeline,
            TransientStream stream,
            int priority,
            long stallTimeout,
            boolean outputEnabled,
            String outputObjectDirectory,
            String healthReportCallbackURI,
            String summaryReportCallbackURI,
            Map<String, String> jobProperties,
            Map<String, Map<String, String>> algorithmProperties) {
        _id = id;
        _externalId = externalId;
        _pipeline = pipeline;
        _stream = stream;
        _priority = priority;
        _stallTimeout = stallTimeout;
        _outputEnabled = outputEnabled;
        _outputObjectDirectory = outputObjectDirectory;
        _healthReportCallbackURI = healthReportCallbackURI;
        _summaryReportCallbackURI = summaryReportCallbackURI;
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
