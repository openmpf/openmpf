/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableMap;

import java.time.Instant;
import java.util.Optional;

// Suppress because it's better than having to explicitly use StreamingJobImpl during deserialization.
@SuppressWarnings("ClassReferencesSubclass")
@JsonDeserialize(as = StreamingJobImpl.class)
public interface StreamingJob {
    public long getId();

    public StreamingJobStatus getJobStatus();

    public JobPipelineElements getPipelineElements();

    public Optional<String> getExternalId();

    public int getPriority();

    public long getStallTimeout();

    public boolean isOutputEnabled();

    public String getOutputObjectDirectory();

    public MediaStreamInfo getStream();

    // The key of the top level map is the algorithm name. The sub-map is the overridden properties for that algorithm.
    public ImmutableMap<String, ImmutableMap<String, String>> getOverriddenAlgorithmProperties();

    public ImmutableMap<String, String> getJobProperties();

    public boolean isCancelled();

    public Optional<String> getHealthReportCallbackURI();

    public Optional<String> getSummaryReportCallbackURI();

    public long getLastActivityFrame();

    public Instant getLastActivityTime();

    public boolean isCleanupEnabled();
}
