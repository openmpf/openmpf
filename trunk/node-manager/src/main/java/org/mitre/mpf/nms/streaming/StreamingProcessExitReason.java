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


package org.mitre.mpf.nms.streaming;

import java.util.stream.Stream;

public enum StreamingProcessExitReason {
    CANCELLED(0, "Cancelled by user"),
    UNEXPECTED_ERROR(1, "Unexpected error. See logs for details."),
    INVALID_COMMAND_LINE_ARGUMENTS(2, "C++ Streaming Component Executor received invalid command line arguments"),

    INVALID_INI_FILE(65, "Unable to load ini file"),
    UNABLE_TO_READ_FROM_STANDARD_IN(66, "C++ Streaming Component Executor is unable to read from standard in"),
    MESSAGE_BROKER_ERROR(69, "Message broker error"),
    INTERNAL_COMPONENT_ERROR(70, "Error in component logic"),
    COMPONENT_LOAD_ERROR(71, "Failed to load component library"),
    UNABLE_TO_CONNECT_TO_STREAM(75, "Unable to connect to stream"),
    STREAM_STALLED(76, "Stream stalled for too long. It is no longer possible to read frames.");


    public final int exitCode;
    public final String detail;

    StreamingProcessExitReason(int exitCode, String detail) {
        this.exitCode = exitCode;
        this.detail = detail;
    }

    public static StreamingProcessExitReason fromExitCode(int exitCode) {
        return Stream.of(StreamingProcessExitReason.values())
                .filter(r -> r.exitCode == exitCode)
                .findAny()
                .orElse(UNEXPECTED_ERROR);
    }
}
