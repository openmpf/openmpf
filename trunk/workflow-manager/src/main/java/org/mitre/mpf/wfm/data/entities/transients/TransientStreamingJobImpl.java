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

import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class TransientStreamingJobImpl implements TransientStreamingJob {

    private final long id;
    @Override
    public long getId() { return id; }


    private StreamingJobStatus jobStatus = new StreamingJobStatus(StreamingJobStatusType.INITIALIZING, null);
    @Override
    public StreamingJobStatus getJobStatus() { return jobStatus; }
    public void setJobStatus(StreamingJobStatus jobStatus) { this.jobStatus = jobStatus; }


    private final TransientPipeline pipeline;
    @Override
    public TransientPipeline getPipeline() { return pipeline; }


    private final String externalId;
    @Override
    public String getExternalId() { return externalId; }


    private final int priority;
    @Override
    public int getPriority() { return  priority; }


    private final long stallTimeout;
    @Override
    public long getStallTimeout() { return stallTimeout; }


    private final boolean outputEnabled;
    @Override
    public boolean isOutputEnabled() { return outputEnabled; }


    private final String outputObjectDirectory;
    @Override
    public String getOutputObjectDirectory() { return outputObjectDirectory; }


    private final TransientStream stream;
    @Override
    public TransientStream getStream() { return stream; }


    private final Map<String, Map<String, String>> overriddenAlgorithmProperties;
    @Override
    public Map<String, Map<String, String>> getOverriddenAlgorithmProperties() { return overriddenAlgorithmProperties; }


    private final Map<String, String> overriddenJobProperties;
    @Override
    public Map<String, String> getOverriddenJobProperties() { return overriddenJobProperties; }


    private boolean cancelled;
    @Override
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean isCancelled) { this.cancelled = isCancelled; }


    private final String healthReportCallbackURI;
    @Override
    public String getHealthReportCallbackURI() { return healthReportCallbackURI; }


    private final String summaryReportCallbackURI;
    @Override
    public String getSummaryReportCallbackURI() { return summaryReportCallbackURI; }


    private long lastActivityFrame = -1;
    @Override
    public long getLastActivityFrame() { return lastActivityFrame; }
    public void setLastActivityFrame(long frame) { lastActivityFrame = frame; }


    private Instant lastActivityTime;
    @Override
    public Instant getLastActivityTime() { return lastActivityTime; }
    public void setLastActivityTime(Instant time) { lastActivityTime = time; }


    private boolean cleanupEnabled;
    @Override
    public boolean isCleanupEnabled() { return cleanupEnabled; }
    public void setCleanupEnabled(boolean cleanupEnabled) { this.cleanupEnabled = cleanupEnabled; }


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
        this.id = id;
        this.externalId = externalId;
        this.pipeline = pipeline;
        this.stream = stream;
        this.priority = priority;
        this.stallTimeout = stallTimeout;
        this.outputEnabled = outputEnabled;
        this.outputObjectDirectory = outputObjectDirectory;
        this.healthReportCallbackURI = healthReportCallbackURI;
        this.summaryReportCallbackURI = summaryReportCallbackURI;
        this.overriddenJobProperties = Collections.unmodifiableMap(new HashMap<>(jobProperties));
        this.overriddenAlgorithmProperties = Collections.unmodifiableMap(new HashMap<>(algorithmProperties));
    }
}
