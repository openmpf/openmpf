/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MediaType;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

public class ArtifactExtractionRequest {
    /** The identifier of the medium associated with this request. */
    private final long _mediaId;
    public long getMediaId() { return _mediaId; }

    /** The local path of the medium. */
    private final String _mediaPath;
    public String getMediaPath() { return _mediaPath; }

    /** The type of media associated with this request. */
    private final MediaType _mediaType;
    public MediaType getMediaType() { return _mediaType; }

    /** The media metadata. */
    private final Map<String, String> _mediaMetadata;
    public Map<String, String> getMediaMetadata() { return _mediaMetadata; }

    /** The job id associated with this request. */
    private final long _jobId;
    public long getJobId() { return _jobId; }

    /** The index of the task from which this request was derived. */
    private final Integer _taskIndex;
    public Integer getTaskIndex() { return _taskIndex; }

    /** The index of the action from which this request was derived. */
    private final Integer _actionIndex;
    public Integer getActionIndex() {  return _actionIndex; }

    /** If the cropping flag is set to true, then each extraction will be cropped according to
     * the bounding box in the corresponding detection. If false, then the entire frame
     * will be extracted. */
    private final boolean _croppingFlag;
    public boolean getCroppingFlag() { return _croppingFlag; }

    private final boolean _rotationFillIsBlack;
    public boolean getRotationFillIsBlack() { return _rotationFillIsBlack; }

    private final TrackCache _trackCache;
    public SortedSet<Track> getTracks() {
        return _trackCache.getTracks(_mediaId, _actionIndex);
    }

    public void updateTracks(SortedSet<Track> tracks) {
        _trackCache.updateTracks(_mediaId, _actionIndex, tracks);
    }

    /** Maps frame numbers to pairs of trackIndex and detection to be extracted. */
    private final SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> extractionsMap = new TreeMap<>();
    public SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> getExtractionsMap() {
        return extractionsMap;
    }


    public ArtifactExtractionRequest(
            long jobId,
            long mediaId,
            String mediaPath,
            MediaType mediaType,
            Map<String, String> mediaMetadata,
            int taskIndex,
            int actionIndex,
            boolean croppingFlag,
            boolean rotationFillIsBlack,
            TrackCache trackCache) {
        _jobId = jobId;
        _mediaId = mediaId;
        _mediaPath = mediaPath;
        _mediaType = mediaType;
        _mediaMetadata = mediaMetadata;
        _taskIndex = taskIndex;
        _actionIndex = actionIndex;
        _croppingFlag = croppingFlag;
        _rotationFillIsBlack = rotationFillIsBlack;
        _trackCache = trackCache;
    }
}
