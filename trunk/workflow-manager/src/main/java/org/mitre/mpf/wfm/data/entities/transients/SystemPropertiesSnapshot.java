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
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.mitre.mpf.wfm.service.StorageBackend;
import org.mitre.mpf.wfm.service.StorageException;

import java.net.URI;
import java.util.Map;

// Wrapper class for the system property values that should have been captured in a
// ImmutableConfiguration object when a batch job is created. The system property values will be
// stored in a TransientJob so that the system property values applicable to a job will remain
// consistent throughout all pipeline stages of a batch job, even if the system property values are changed on the
// UI while the job is being processed. This processing is not implemented for streaming jobs, since
// streaming jobs may only be single stage at this time.
public class SystemPropertiesSnapshot {

    // Map contains a snapshot of the necessary system property values
    private final ImmutableMap<String, String> _properties;
    public ImmutableMap<String, String> getProperties() {
        return _properties;
    }

    public boolean isEmpty() { return _properties.isEmpty(); }

    public ArtifactExtractionPolicy getArtifactExtractionPolicy() {
        return ArtifactExtractionPolicy.parse(_properties.get("detection.artifact.extraction.policy"));
     }

    public int getSamplingInterval() {
        return Integer.valueOf(_properties.get("detection.sampling.interval"));
    }

    public int getFrameRateCap() {
        return Integer.valueOf(_properties.get("detection.frame.rate.cap"));
    }

    public double getConfidenceThreshold() {
        return Double.valueOf(_properties.get("detection.confidence.threshold"));
    }

    public int getMinAllowableSegmentGap() {
        return Integer.valueOf(_properties.get("detection.segment.minimum.gap"));
    }

    public int getTargetSegmentLength() {
        return Integer.valueOf(_properties.get("detection.segment.target.length"));
    }

    public int getMinSegmentLength() {
        return Integer.valueOf(_properties.get("detection.segment.minimum.length"));
    }

    public boolean isTrackMerging() {
        return Boolean.valueOf(_properties.get("detection.video.track.merging.enabled"));
    }

    public int getMinAllowableTrackGap() {
        return Integer.valueOf(_properties.get("detection.video.track.min.gap"));
    }

    public int getMinTrackLength() {
        return Integer.valueOf(_properties.get("detection.video.track.min.length"));
    }

    public double getTrackOverlapThreshold() {
        return Double.valueOf(_properties.get("detection.video.track.overlap.threshold"));
    }

    public StorageBackend.Type getHttpObjectStorageType() throws StorageException {
        String storageType = _properties.get("http.object.storage.type");
        try {
            return StorageBackend.Type.valueOf(storageType);
        }
        catch (IllegalArgumentException e) {
            throw new StorageException("Invalid storage type: " + storageType, e);
        }
    }

    public URI getHttpStorageServiceUri() {
        return URI.create(_properties.get("http.object.storage.service_uri"));
    }

    public boolean isOutputObjectExemplarOnly() {
        return Boolean.parseBoolean(_properties.get("mpf.output.objects.exemplars.only"));
    }

    public boolean isOutputObjectLastStageOnly() {
        return Boolean.parseBoolean(_properties.get("mpf.output.objects.last.stage.only"));
    }


    public String lookup(String propertyName) {
        return _properties.get(propertyName);
    }


    public SystemPropertiesSnapshot(Map<String, String> properties) {
        _properties = ImmutableMap.copyOf(properties);
    }

    @Override
    public String toString() {
        return "SystemPropertiesSnapshot: contains snapshot: " + _properties;
    }
}
