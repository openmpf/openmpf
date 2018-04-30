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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Wrapper class for the detection.* system property values that should have been captured in a
// ImmutableConfiguration object when a batch job is created. The detection.* system property values will be
// stored in REDIS so that the system property values applicable to a job will remain
// consistent throughout all pipeline stages of a batch job, even if the system property values are changed on the
// UI while the job is being processed. This processing is not implemented for streaming jobs, since
// streaming jobs may only be single stage at this time.
// Use of this wrapper class is necessary for two reasons: (1) ImmutableConfiguration isn't Serializable
// (2) use of the wrapper class means that we can add getter methods for the detection properties.
public class TransientDetectionSystemProperties {

    private static final Logger log = LoggerFactory.getLogger(TransientDetectionSystemProperties.class);

    // HashMap created from Collections.unmodifiableMap contains a snapshot of the detection.* system property values
    private Map detectionSystemPropertiesSnapshot;
    public Map getDetectionSystemPropertiesSnapshot() { return detectionSystemPropertiesSnapshot; }

    @JsonIgnore
    public boolean isEmpty() { return detectionSystemPropertiesSnapshot.isEmpty(); }

    @JsonIgnore
    public boolean equals(TransientDetectionSystemProperties otherTransientDetectionSystemProperties) {
        return detectionSystemPropertiesSnapshot.equals(otherTransientDetectionSystemProperties.detectionSystemPropertiesSnapshot);
    }

    @JsonIgnore
    public ArtifactExtractionPolicy getArtifactExtractionPolicy() {
        return ArtifactExtractionPolicy.parse((String)detectionSystemPropertiesSnapshot.get("detection.artifact.extraction.policy"));
     }

    @JsonIgnore
    public int getSamplingInterval() {
        return Integer.valueOf((String)detectionSystemPropertiesSnapshot.get("detection.sampling.interval"));
    }

    @JsonIgnore
    public int getFrameRateCap() {
        return Integer.valueOf((String)detectionSystemPropertiesSnapshot.get("detection.frame.rate.cap"));
    }

    @JsonIgnore
    public double getConfidenceThreshold() {
        return Double.valueOf((String)detectionSystemPropertiesSnapshot.get("detection.confidence.threshold"));
    }

    @JsonIgnore
    public int getMinAllowableSegmentGap() {
        return Integer.valueOf((String)detectionSystemPropertiesSnapshot.get("detection.segment.minimum.gap"));
    }

    @JsonIgnore
    public int getTargetSegmentLength() {
        return Integer.valueOf((String)detectionSystemPropertiesSnapshot.get("detection.segment.target.length"));
    }

    @JsonIgnore
    public int getMinSegmentLength() {
        return Integer.valueOf((String)detectionSystemPropertiesSnapshot.get("detection.segment.minimum.length"));
    }

    @JsonIgnore
    public boolean isTrackMerging() {
        return Boolean.valueOf((String)detectionSystemPropertiesSnapshot.get("detection.track.merging.enabled"));
    }

    @JsonIgnore
    public int getMinAllowableTrackGap() {
        return Integer.valueOf((String)detectionSystemPropertiesSnapshot.get("detection.track.min.gap"));
    }

    @JsonIgnore
    public int getMinTrackLength() {
        return Integer.valueOf((String)detectionSystemPropertiesSnapshot.get("detection.track.minimum.length"));
    }

    @JsonIgnore
    public double getTrackOverlapThreshold() {
        return Double.valueOf((String)detectionSystemPropertiesSnapshot.get("detection.track.overlap.threshold"));
    }

    public String lookup(String propertyName) {
        if ( detectionSystemPropertiesSnapshot.containsKey(propertyName ) ) {
            return (String) detectionSystemPropertiesSnapshot.get(propertyName);
        } else {
            return null;
        }
    }

    /**
     * Constructor set the detection properties in this wrapper using PropertiesUtil getDetectionConfiguration method.
     * @param detectionSystemPropertiesConfig immutable object containing current value of the detection system properties
     */
    public TransientDetectionSystemProperties(ImmutableConfiguration detectionSystemPropertiesConfig) {
        Map<String,Object> detMap = new HashMap<String,Object>();
        // Put each property from the detectionSystemPropertiesConfig into the HashMap. Note that all property
        // values were going into the HashMap as a String, because that is how they are represented in the ImmutableConfiguration object.
        detectionSystemPropertiesConfig.getKeys().forEachRemaining( key -> detMap.put(key,detectionSystemPropertiesConfig.getProperty(key)) );
        detectionSystemPropertiesSnapshot = Collections.unmodifiableMap(detMap);
    }

    @JsonCreator
    public TransientDetectionSystemProperties(@JsonProperty("detectionSystemPropertiesSnapshot") Map detectionSystemPropertiesSnapshot) {
        this.detectionSystemPropertiesSnapshot = Collections.unmodifiableMap(detectionSystemPropertiesSnapshot);
    }

    public String toString() {
        return "TransientDetectionSystemProperties: contains snapshot " + detectionSystemPropertiesSnapshot;
    }
}
