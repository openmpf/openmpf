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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO, could delete this class - but might end up reusing it as a wrapper class for system detection properties snapshot (ImmutableConfiguration) so we won't have to specify raw keys anymore
// This class is a container class for the detection.* system property values that are captured in the
// ImmutableConfiguration object detectionSystemPropertiesSnapshot when a batch job
// is created. The detection.* system property values are being stored in REDIS so that
// the system property values applicable to a job will remain
// consistent throughout all pipeline stages of a batch job, even if the system property values are changed on the
// UI while the job is being processed. This processing is not implemented for streaming jobs, since
// streaming jobs may only be single stage at this time.
public class TransientDetectionSystemProperties {

    private static final Logger log = LoggerFactory.getLogger(TransientDetectionSystemProperties.class);

    private long id; // Unique id for this transient object.
    public long getId() { return this.id; }

    private ImmutableConfiguration detectionSystemPropertiesSnapshot;
    public ImmutableConfiguration getDetectionSystemPropertiesSnapshot() { return this.detectionSystemPropertiesSnapshot; }

    /**
     * Constructor sets this containers detection properties using PropertiesUtil getDetectionConfiguration method.
     * @param id unique id for this object (a unique id is required for REDIS storage).
     * @propertiesUtil provides access to current system property values via the PropertiesUtil getDetectionConfiguration method.
     */
    public TransientDetectionSystemProperties(long id, PropertiesUtil propertiesUtil) {
        this.id = id;
        this.detectionSystemPropertiesSnapshot = propertiesUtil.getDetectionConfiguration();
        log.info("TransientDetectionSystemProperties: debug, created TransientDetectionSystemProperties this = " + this);
    }

    @JsonCreator
    public TransientDetectionSystemProperties(@JsonProperty("id") long id,
        @JsonProperty("detectionSystemPropertiesSnapshot") ImmutableConfiguration detectionSystemPropertiesSnapshot) {
        this.id = id;
        this.detectionSystemPropertiesSnapshot = detectionSystemPropertiesSnapshot;
        log.info("TransientDetectionSystemProperties: JSON debug, created TransientDetectionSystemProperties this = " + this);
    }

    public String toString() {
        return "TransientDetectionSystemProperties: id = " + id +
            ", artifactExtractionPolicy = " + detectionSystemPropertiesSnapshot.get(ArtifactExtractionPolicy.class, "detection.artifact.extraction.policy") +
            ", samplingInterval = " + detectionSystemPropertiesSnapshot.getInt("detection.sampling.interval") +
            ", frameRateCap = " + detectionSystemPropertiesSnapshot.getInt("detection.frame.rate.cap") +
            ", confidenceThreshold = " + detectionSystemPropertiesSnapshot.getDouble("detection.confidence.threshold");
    }
}
