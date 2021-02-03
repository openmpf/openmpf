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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.service.StorageException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

// Wrapper class for the system property values that should have been captured in a
// ImmutableConfiguration object when a batch job is created. The system property values will be
// stored in a BatchJob so that the system property values applicable to a job will remain
// consistent throughout all pipeline stages of a batch job, even if the system property values are changed on the
// UI while the job is being processed. This processing is not implemented for streaming jobs, since
// streaming jobs may only be single stage at this time.

// Disable auto detect since we only want to deserialize the properties field.
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class SystemPropertiesSnapshot {

    // Map contains a snapshot of the necessary system property values
    private final ImmutableMap<String, String> _properties;
    @JsonProperty
    public ImmutableMap<String, String> getProperties() {
        return _properties;
    }

    public boolean isEmpty() { return _properties.isEmpty(); }

    public ArtifactExtractionPolicy getArtifactExtractionPolicy() {
        return ArtifactExtractionPolicy.parse(_properties.get("detection.artifact.extraction.policy"));
     }

    public int getSamplingInterval() {
        return Integer.parseInt(_properties.get("detection.sampling.interval"));
    }

    public int getFrameRateCap() {
        return Integer.parseInt(_properties.get("detection.frame.rate.cap"));
    }

    public double getConfidenceThreshold() {
        return Double.parseDouble(_properties.get("detection.confidence.threshold"));
    }

    public int getMinAllowableSegmentGap() {
        return Integer.parseInt(_properties.get("detection.segment.minimum.gap"));
    }

    public int getTargetSegmentLength() {
        return Integer.parseInt(_properties.get("detection.segment.target.length"));
    }

    public int getMinSegmentLength() {
        return Integer.parseInt(_properties.get("detection.segment.minimum.length"));
    }

    public boolean isTrackMerging() {
        return Boolean.parseBoolean(_properties.get("detection.video.track.merging.enabled"));
    }

    public int getMinAllowableTrackGap() {
        return Integer.parseInt(_properties.get("detection.video.track.min.gap"));
    }

    public int getMinTrackLength() {
        return Integer.parseInt(_properties.get("detection.video.track.min.length"));
    }

    public double getTrackOverlapThreshold() {
        return Double.parseDouble(_properties.get("detection.video.track.overlap.threshold"));
    }

    public Optional<URI> getNginxStorageServiceUri() throws StorageException {
        String uriString = _properties.get("http.object.storage.nginx.service.uri");
        if (StringUtils.isBlank(uriString)) {
            return Optional.empty();
        }

        try {
            URI uri = new URI(uriString);
            if (StringUtils.isBlank(uri.getScheme())) {
                throw new StorageException(String.format(
                        "Expected the \"http.object.storage.nginx.service.uri\" property to either be " +
                                "a valid URI or an empty string but it was \"%s\", which is missing the URI scheme.",
                        uriString));
            }
            return Optional.of(uri);
        }
        catch (URISyntaxException e) {
            throw new StorageException(String.format(
                    "Expected the \"http.object.storage.nginx.service.uri\" property to either be " +
                            "a valid URI or an empty string but it was \"%s\", which is invalid due to: %s",
                    uriString, e), e);
        }
    }

    public int getArtifactExtractionPolicyExemplarFramePlus() {
        return Integer.parseInt(_properties.get("detection.artifact.extraction.policy.exemplar.frame.plus"));
    }

    public int getArtifactExtractionPolicyTopConfidenceCount() {
        return Integer.parseInt(_properties.get("detection.artifact.extraction.policy.top.confidence.count"));
    }

    public ArtifactExtractionPolicy getDefaultArtifactExtractionPolicy() {
        return ArtifactExtractionPolicy.parse(_properties.get("detection.artifact.extraction.policy"));
    }

    public boolean isMarkupVideoVp9Enabled() {
        return Boolean.parseBoolean(_properties.get("markup.video.vp9.enabled"));
    }


    public String lookup(String propertyName) {
        return _properties.get(propertyName);
    }

    @JsonCreator
    public SystemPropertiesSnapshot(
            @JsonProperty("properties") Map<String, String> properties) {
        _properties = ImmutableMap.copyOf(properties);
    }

    @Override
    public String toString() {
        return "SystemPropertiesSnapshot: contains snapshot: " + _properties;
    }
}
