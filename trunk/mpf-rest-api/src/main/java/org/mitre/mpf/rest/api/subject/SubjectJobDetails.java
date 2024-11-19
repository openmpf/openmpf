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

package org.mitre.mpf.rest.api.subject;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.SortedSet;

import com.fasterxml.jackson.annotation.JsonView;

public record SubjectJobDetails(
        long id,
        SubjectJobRequest request,
        Instant startDate,
        Optional<Instant> endDate,
        boolean retrievedDetectionJobs,
        CancellationState cancellationState,
        SortedSet<String> errors,
        SortedSet<String> warnings,

        // @JsonView is being used to prevent the "outputUri" and "callbackStatus" fields from
        // appearing in the JSON output object.
        @JsonView(RestView.class)
        Optional<URI> outputUri,

        // The callback occurs after the output object is written, so the callback status is not
        // known at the time the output object is created.
        @JsonView(RestView.class)
        String callbackStatus) {


    // When this view is used, outputUri and callbackStatus will not be included when writing out
    // the JSON.
    public interface OutputObjectView {
    }

    // When this view is used, or no view is specified, all fields will be included when writing
    // out the JSON.
    public interface RestView {
    }
}
