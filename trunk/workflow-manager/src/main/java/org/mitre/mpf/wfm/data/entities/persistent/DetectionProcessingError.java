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

package org.mitre.mpf.wfm.data.entities.persistent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.util.TextUtils;

import java.util.Comparator;
import java.util.Objects;

public class DetectionProcessingError implements Comparable<DetectionProcessingError> {
    private final long _jobId;
    public long getJobId() { return _jobId; }

    private final long _mediaId;
    public long getMediaId() { return _mediaId; }

    private final int _taskIndex;
    public int getTaskIndex() { return _taskIndex; }

    private final int _actionIndex;
    public int getActionIndex() { return _actionIndex; }

    private final int _startOffset;
    public int getStartOffset() { return _startOffset; }

    private final int _endOffset;
    public int getEndOffset() { return _endOffset; }

    private final String _error;
    public String getError() { return _error; }

    @JsonCreator
    public DetectionProcessingError(
            @JsonProperty("jobId") long jobId,
            @JsonProperty("mediaId") long mediaId,
            @JsonProperty("taskIndex") int taskIndex,
            @JsonProperty("actionIndex") int actionIndex,
            @JsonProperty("startOffset") int startOffset,
            @JsonProperty("endOffset") int endOffset,
            @JsonProperty("error") String error) {
        _jobId = jobId;
        _mediaId = mediaId;
        _taskIndex = taskIndex;
        _actionIndex = actionIndex;
        _startOffset = startOffset;
        _endOffset = endOffset;
        _error = error;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_jobId, _mediaId, _taskIndex, _actionIndex, _startOffset, _endOffset, _error);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DetectionProcessingError && compareTo((DetectionProcessingError) obj) == 0;
    }

    @Override
    public String toString() {
        return String.format(
                "%s#<jobId=%d, mediaId=%d, taskIndex=%d, actionIndex=%d, startOffset=%d, endOffset=%d, error='%s'>",
                getClass().getSimpleName(), _jobId, _mediaId, _taskIndex, _actionIndex, _startOffset, _endOffset,
                _error);
    }

    private static final Comparator<DetectionProcessingError> COMPARATOR = Comparator.nullsFirst(
            Comparator.comparingLong(DetectionProcessingError::getJobId)
            .thenComparingLong(DetectionProcessingError::getMediaId)
            .thenComparingInt(DetectionProcessingError::getTaskIndex)
            .thenComparingInt(DetectionProcessingError::getActionIndex)
            .thenComparingInt(DetectionProcessingError::getStartOffset)
            .thenComparingInt(DetectionProcessingError::getEndOffset)
            .thenComparing(DetectionProcessingError::getError, TextUtils::nullSafeCompare));

    @Override
    public int compareTo(DetectionProcessingError other) {
        if (this == other) {
            return 0;
        }
        return COMPARATOR.compare(this, other);
    }
}
