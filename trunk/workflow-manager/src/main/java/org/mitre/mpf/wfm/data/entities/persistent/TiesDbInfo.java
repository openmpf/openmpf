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

import java.time.Instant;
import java.util.SortedSet;

import org.mitre.mpf.interop.JsonTiming;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;

public record TiesDbInfo(String tiesDbUrl, Assertion assertion) {

    public record Assertion(
            String assertionId,
            String informationType,
            String securityTag,
            String system,
            DataObject dataObject) {

        public Assertion(String assertionId, DataObject dataObject) {
            this(assertionId,
                 "OpenMPF Tracks",
                 "UNCLASSIFIED",
                 "OpenMPF",
                 dataObject);
        }
    }

    public record DataObject(
            String pipeline,
            SortedSet<String> outputTypes,
            String jobId,
            String outputUri,
            String sha256OutputHash,
            Instant processDate,
            BatchJobStatusType jobStatus,
            String systemVersion,
            String systemHostname,
            int trackCount,
            String jobConfigHash,
            JsonTiming timing) {}
}
