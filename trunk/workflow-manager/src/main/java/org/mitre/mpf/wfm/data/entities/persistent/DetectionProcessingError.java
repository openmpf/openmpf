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

package org.mitre.mpf.wfm.data.entities.persistent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.interop.util.CompareUtils;

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

    private final int _startFrame;
    public int getStartFrame() { return _startFrame; }

    private final int _stopFrame;
    public int getStopFrame() { return _stopFrame; }

    private final int _startTime;
    public int getStartTime() { return _startTime; }

    private final int _stopTime;
    public int getStopTime() { return _stopTime; }

    private final String _errorCode;
    public String getErrorCode() { return _errorCode; }

    private final String _errorMessage;
    public String getErrorMessage() { return _errorMessage; }

    @JsonCreator
    public DetectionProcessingError(
            @JsonProperty("jobId") long jobId,
            @JsonProperty("mediaId") long mediaId,
            @JsonProperty("taskIndex") int taskIndex,
            @JsonProperty("actionIndex") int actionIndex,
            @JsonProperty("startFrame") int startFrame,
            @JsonProperty("stopFrame") int stopFrame,
            @JsonProperty("startTime") int startTime,
            @JsonProperty("stopTime") int stopTime,
            @JsonProperty("errorCode") String errorCode,
            @JsonProperty("errorMessage") String errorMessage) {
        _jobId = jobId;
        _mediaId = mediaId;
        _taskIndex = taskIndex;
        _actionIndex = actionIndex;
        _startFrame = startFrame;
        _stopFrame = stopFrame;
        _startTime = startTime;
        _stopTime = stopTime;
        _errorCode = errorCode;
        _errorMessage = errorMessage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_jobId, _mediaId, _taskIndex, _actionIndex, _startFrame, _stopFrame, _startTime, _stopTime,
                            _errorCode, _errorMessage);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof DetectionProcessingError) && compareTo((DetectionProcessingError) obj) == 0;
    }

    @Override
    public String toString() {
        return String.format(
            "%s#<jobId=%d, mediaId=%d, taskIndex=%d, actionIndex=%d, startFrame=%d, stopFrame=%d, startTime=%d, stopTime=%d, errorCode='%s', errorMessage='%s'>",
            getClass().getSimpleName(), _jobId, _mediaId, _taskIndex, _actionIndex, _startFrame, _stopFrame, _startTime, _stopTime,
            _errorCode, _errorMessage);
    }

    private static final Comparator<DetectionProcessingError> COMPARATOR = Comparator.nullsFirst(
            Comparator.comparingLong(DetectionProcessingError::getJobId)
            .thenComparingLong(DetectionProcessingError::getMediaId)
            .thenComparingInt(DetectionProcessingError::getTaskIndex)
            .thenComparingInt(DetectionProcessingError::getActionIndex)
            .thenComparingInt(DetectionProcessingError::getStartFrame)
            .thenComparingInt(DetectionProcessingError::getStopFrame)
            .thenComparingInt(DetectionProcessingError::getStartTime)
            .thenComparingInt(DetectionProcessingError::getStopTime)
            .thenComparing(CompareUtils.stringCompare(DetectionProcessingError::getErrorCode))
            .thenComparing(CompareUtils.stringCompare(DetectionProcessingError::getErrorMessage)));

    @Override
    public int compareTo(DetectionProcessingError other) {
        if (this == other) {
            return 0;
        }
        return COMPARATOR.compare(this, other);
    }
}

