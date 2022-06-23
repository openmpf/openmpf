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

package org.mitre.mpf.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonCallbackBody {
    /** The identifier assigned to this job by MPF. */
    private final String _jobId;
    public String getJobId() { return _jobId; }

    /** The ID that was provided to this job when it was initially submitted. */
    private final String _externalId;
    public String getExternalId() { return _externalId; }

    private final String _outputObjectUri;
    public String getOutputObjectUri() { return _outputObjectUri; }

    @JsonCreator
    public JsonCallbackBody(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("externalId") String externalId,
            @JsonProperty("outputObjectUri") String outputObjectUri) {
        _jobId = jobId;
        _externalId = externalId;
        _outputObjectUri = outputObjectUri;
    }
}
