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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

// Suppress because it's better than having to explicitly use TransientJobImpl during deserialization.
@SuppressWarnings("ClassReferencesSubclass")
@JsonDeserialize(as = TransientJobImpl.class)
public interface TransientJob {
    public long getId();

    public BatchJobStatusType getStatus();

    public TransientPipeline getTransientPipeline();

    public int getCurrentTaskIndex();

    public Optional<String> getExternalId();

    public int getPriority();

    public boolean isOutputEnabled();

    public ImmutableCollection<? extends TransientMedia> getMedia();

    public TransientMedia getMedia(long mediaId);

    // The table's row key is the algorithm name, the column key is the property name,
    // and the value is the property value.
    public ImmutableMap<String, ImmutableMap<String, String>> getOverriddenAlgorithmProperties();

    public ImmutableMap<String, String> getJobProperties();

    public boolean isCancelled();

    public Optional<String> getCallbackUrl();

    public Optional<String> getCallbackMethod();

    public SystemPropertiesSnapshot getSystemPropertiesSnapshot();

    public Set<String> getWarnings();

    public Set<String> getErrors();

    public List<DetectionProcessingError> getDetectionProcessingErrors();
}
