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
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// This class is a container class for the detection.* system property values that are captured when a job (i.e. batch job only)
// is created. Storing detection.* system property values in REDIS so that the system property values will remain
// consistent throughout all pipeline stages of a batch job, even if the system property values are changed on the
// UI while a job is being processed. This processing is not implemented for streaming jobs, since
// streaming jobs may only be single stage at this time.
public class TransientDetectionSystemProperties {

    private static final Logger log = LoggerFactory.getLogger(TransientDetectionSystemProperties.class);

    private long id; // Unique id for this transient object.
    public long getId() { return this.id; }

    private ArtifactExtractionPolicy artifactExtractionPolicy;
    public ArtifactExtractionPolicy getArtifactExtractionPolicy() { return artifactExtractionPolicy; }

    private int samplingInterval;
    public int getSamplingInterval() { return samplingInterval; }

    private int frameRateCap;
    public int getFrameRateCap() { return frameRateCap; }

    private double confidenceThreshold;
    public double getConfidenceThreshold() { return confidenceThreshold; }

    private int minAllowableSegmentGap;
    public int getMinAllowableSegmentGap() { return minAllowableSegmentGap; }

    private int targetSegmentLength;
    public int getTargetSegmentLength() { return targetSegmentLength; }

    private int minSegmentLength;
    public int getMinSegmentLength() { return minSegmentLength; }

    private boolean trackMerging;
    public boolean isTrackMerging() { return trackMerging; }

    private int minAllowableTrackGap;
    public int getMinAllowableTrackGap() { return minAllowableTrackGap; }

    private int minTrackLength;
    public int getMinTrackLength() { return minTrackLength; }

    private double trackOverlapThreshold;
    public double getTrackOverlapThreshold() { return trackOverlapThreshold; }

    /**
     * Constructor sets this containers detection properties using PropertiesUtil detection system property getter methods.
     * @param id unique id for this object (required for REDIS storage)
     * @propertiesUtil provides access to current system property values.
     */
    public TransientDetectionSystemProperties(long id, PropertiesUtil propertiesUtil) {
        this.id = id;
        artifactExtractionPolicy = propertiesUtil.getArtifactExtractionPolicy();
        samplingInterval = propertiesUtil.getSamplingInterval();
        frameRateCap = propertiesUtil.getFrameRateCap();
        confidenceThreshold = propertiesUtil.getConfidenceThreshold();
        minAllowableSegmentGap = propertiesUtil.getMinAllowableSegmentGap();
        targetSegmentLength = propertiesUtil.getTargetSegmentLength();
        minSegmentLength = propertiesUtil.getMinSegmentLength();
        trackMerging = propertiesUtil.isTrackMerging();
        minAllowableTrackGap = propertiesUtil.getMinAllowableTrackGap();
        minTrackLength = propertiesUtil.getMinTrackLength();
        trackOverlapThreshold = propertiesUtil.getTrackOverlapThreshold();
        log.info("TransientDetectionSystemProperties: debug, created TransientDetectionSystemProperties this = " + this);
    }

    @JsonCreator
    public TransientDetectionSystemProperties(@JsonProperty("id") long id, @JsonProperty("artifactExtractionPolicy") ArtifactExtractionPolicy artifactExtractionPolicy,
        @JsonProperty("samplingInterval") int samplingInterval,
        @JsonProperty("frameRateCap") int frameRateCap,
        @JsonProperty("confidenceThreshold") double confidenceThreshold,
        @JsonProperty("minAllowableSegmentGap") int minAllowableSegmentGap,
        @JsonProperty("targetSegmentLength") int targetSegmentLength,
        @JsonProperty("minSegmentLength") int minSegmentLength,
        @JsonProperty("trackMerging") boolean trackMerging,
        @JsonProperty("minAllowableTrackGap") int minAllowableTrackGap,
        @JsonProperty("minTrackLength") int minTrackLength,
        @JsonProperty("trackOverlapThreshold") double trackOverlapThreshold ) {
        this.id = id;
        this.artifactExtractionPolicy = artifactExtractionPolicy;
        this.samplingInterval = samplingInterval;
        this.frameRateCap = frameRateCap;
        this.confidenceThreshold = confidenceThreshold;
        this.minAllowableSegmentGap = minAllowableSegmentGap;
        this.targetSegmentLength = targetSegmentLength;
        this.minSegmentLength = minSegmentLength;
        this.trackMerging = trackMerging;
        this.minAllowableTrackGap = minAllowableTrackGap;
        this.minTrackLength = minTrackLength;
        this.trackOverlapThreshold = trackOverlapThreshold;
        log.info("TransientDetectionSystemProperties: JSON debug, created TransientDetectionSystemProperties this = " + this);
    }

    public String toString() {
        return "TransientDetectionSystemProperties: id = " + id + ", artifactExtractionPolicy = " + artifactExtractionPolicy +
            ", samplingInterval = " + samplingInterval + ", frameRateCap = " + frameRateCap;
    }
}
