/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

package org.mitre.mpf.rest.api;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;

public record TiesDbRepostResponse(
        @ApiModelProperty(
            "A list containing the job ids that were successfully re-posted to TiesDb.")
        List<Long> success,

        @ApiModelProperty(
            value = "A list containing the job ids and descriptions of failed TiesDb re-posts.",
            position = 1)
        List<Failure> failures) {


    public record Failure(
            @ApiModelProperty(
                value = "An id of a job that was not successfully re-posted to TiesDb.",
                example = "2")
            long jobId,

            @ApiModelProperty(
                value = "A textual description explaining why the TiesDb re-post failed.",
                example = "Failed due to ...")
            String error) {
    }
}