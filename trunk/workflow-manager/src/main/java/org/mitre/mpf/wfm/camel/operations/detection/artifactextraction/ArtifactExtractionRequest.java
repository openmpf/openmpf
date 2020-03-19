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

package org.mitre.mpf.wfm.camel.operations.detection.artifactextraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.wfm.enums.MediaType;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class ArtifactExtractionRequest {
    /** The identifier of the medium associated with this request. */
    private final long _mediaId;
    public long getMediaId() { return _mediaId; }

    /** The local path of the medium. */
    private final String _path;
    public String getPath() { return _path; }

    /** The type of media associated with this request. */
    private final MediaType _mediaType;
    public MediaType getMediaType() { return _mediaType; }

    /** The job id associated with this request. */
    private final long _jobId;
    public long getJobId() { return _jobId; }

    /** The index of the task from which this request was derived. */
    private final Integer _taskIndex;
    public Integer getTaskIndex() { return _taskIndex; }

    /** The index of the action from which this request was derived. */
    private final Integer _actionIndex;
    public Integer getActionIndex() {  return _actionIndex; }

    // Maps frame numbers to pairs of trackId and detection to be extracted.
    private final SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> extractionsMap = new TreeMap<>();
    public SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> getExtractionsMap() {
        return extractionsMap;
    }

    @JsonCreator
    public ArtifactExtractionRequest(
            @JsonProperty("jobId") long jobId,
            @JsonProperty("mediaId") long mediaId,
            @JsonProperty("path") String path,
            @JsonProperty("mediaType") MediaType mediaType,
            @JsonProperty("taskIndex") int taskIndex,
            @JsonProperty("actionIndex") int actionIndex) {
        _jobId = jobId;
        _mediaId = mediaId;
        _path = path;
        _mediaType = mediaType;
        _taskIndex = taskIndex;
        _actionIndex = actionIndex;
    }
}
