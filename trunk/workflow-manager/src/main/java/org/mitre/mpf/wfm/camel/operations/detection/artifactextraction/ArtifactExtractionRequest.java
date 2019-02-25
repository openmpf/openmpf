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

package org.mitre.mpf.wfm.camel.operations.detection.artifactextraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.mitre.mpf.wfm.enums.MediaType;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

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

    /** The stage index in the pipeline. This information is necessary to map an artifact back to a track. */
    private final int _stageIndex;
    public int getStageIndex() { return _stageIndex; }

    /** A mapping of actionIndexes to frame numbers which should be extracted for that action. */
    private final ImmutableMap<Integer, ImmutableSet<Integer>> _actionToFrameNumbers;
    public ImmutableMap<Integer, ImmutableSet<Integer>> getActionToFrameNumbers() {
        return _actionToFrameNumbers;
    }

    private final ImmutableSet<Integer> _frameNumbers;
    @JsonIgnore
    public ImmutableSet<Integer> getFrameNumbers() {
        return _frameNumbers;
    }


    @JsonCreator
    public ArtifactExtractionRequest(
            @JsonProperty("jobId") long jobId,
            @JsonProperty("mediaId") long mediaId,
            @JsonProperty("path") String path,
            @JsonProperty("mediaType") MediaType mediaType,
            @JsonProperty("stageIndex") int stageIndex,
            @JsonProperty("actionToFrameNumbers") Map<Integer, Set<Integer>> actionToFrameNumbers) {
        _jobId = jobId;
        _mediaId = mediaId;
        _path = path;
        _mediaType = mediaType;
        _stageIndex = stageIndex;

        _actionToFrameNumbers = actionToFrameNumbers.entrySet()
                .stream()
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> ImmutableSet.copyOf(e.getValue())));

        _frameNumbers = _actionToFrameNumbers.values().stream()
                .flatMap(Collection::stream)
                .collect(ImmutableSet.toImmutableSet());
    }
}
