/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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
package org.mitre.mpf.mvc.model;

import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class JobStatusMessage extends AtmosphereMessage {

    private void setContent (long id, double progress, String updatedJobStatus, Instant endDate) {
        HashMap<String, Object> datamap = new HashMap<String, Object>();
        datamap.put("id", id);
        datamap.put("progress", progress);
        datamap.put("jobStatus", updatedJobStatus);
        datamap.put("endDate", endDate);
        datamap.put("isSessionJob", false);
        this.setContent(datamap);
    }

    public JobStatusMessage(long id, double progress, BatchJobStatusType batchJobStatus, Instant endDate) {
        super( AtmosphereChannel.SSPC_JOBSTATUS, "OnStatusChanged" );
        // If there are job progress updates and the status is null it will be updated to IN_PROGRESS
        BatchJobStatusType updatedJobStatus = (batchJobStatus != null) ? batchJobStatus : BatchJobStatusType.IN_PROGRESS;
        setContent(id, progress, updatedJobStatus.name(), endDate);
    }

    public JobStatusMessage(long id, double progress, StreamingJobStatusType streamingJobStatus, Instant endDate) {
        super( AtmosphereChannel.SSPC_JOBSTATUS, "OnStatusChanged" );
        // If there are job progress updates and the status is null it will be updated to IN_PROGRESS
        StreamingJobStatusType updatedJobStatus = (streamingJobStatus != null) ? streamingJobStatus : StreamingJobStatusType.IN_PROGRESS;
        setContent(id, progress, updatedJobStatus.name(), endDate);
    }

    private JobStatusMessage(Map<String, Object> dataMap) {
        super(AtmosphereChannel.SSPC_JOBSTATUS, "OnStatusChanged" );
        setContent(dataMap);
    }

    public JobStatusMessage sessionJobCopy() {
        var dataMap = new HashMap<>(getContent());
        dataMap.put("isSessionJob", true);
        return new JobStatusMessage(dataMap);
    }

}
