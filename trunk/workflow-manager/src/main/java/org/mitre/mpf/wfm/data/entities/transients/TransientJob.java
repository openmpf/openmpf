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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TransientJob {
	public long getId();

	public BatchJobStatusType getStatus();

	public TransientPipeline getPipeline();

	public int getCurrentStage();

	public Optional<String> getExternalId();

	public int getPriority();

	public boolean isOutputEnabled();

	public ImmutableCollection<? extends TransientMedia> getMedia();

	public TransientMedia getMedia(long mediaId);

	// The table's row key is the algorithm name, the column key is the property name,
	// and the value is the property value.
	public ImmutableTable<String, String, String> getOverriddenAlgorithmProperties();

	public ImmutableMap<String, String> getOverriddenJobProperties();

	public boolean isCancelled();

	public Optional<String> getCallbackUrl();

	public Optional<String> getCallbackMethod();

	// Detection system properties for this job should be immutable, the detection system property values
	// shouldn't change once the job is created even if they are changed on the UI by an admin..
	// The detectionSystemPropertiesSnapshot contains the values of the detection system properties at the time this batch job was created.
    public SystemPropertiesSnapshot getSystemPropertiesSnapshot();

	public Set<String> getWarnings();

	public Set<String> getErrors();

	public List<DetectionProcessingError> getDetectionProcessingErrors();
}
