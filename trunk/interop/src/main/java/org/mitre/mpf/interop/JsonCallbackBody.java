/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
    /** The internal identifier assigned to this job by MPF. Changed to protected so other classes in this package may extend this class. */
    protected long jobId;
    public long getJobId() { return jobId; }

    /** The ID that was provided to this job when it was initially submitted. Changed to protected so other classes in this package may extend this class. */
    protected String externalid;
    public String getExternalId() { return externalid; }

    @JsonCreator
    public JsonCallbackBody(@JsonProperty("jobId") long jobId, @JsonProperty("externalId") String externalid) {
        this.jobId = jobId;
        this.externalid = externalid;
    }
}