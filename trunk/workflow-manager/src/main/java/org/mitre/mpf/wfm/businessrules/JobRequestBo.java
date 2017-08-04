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

package org.mitre.mpf.wfm.businessrules;

import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;

import java.util.List;
import java.util.Map;

public interface JobRequestBo {

	/**
	 * Convenience method: Creates a new JSON-compatible request using the provided inputs, but no record of the
	 * newly-created request is made in the persistent database.
	 */
	JsonJobRequest createRequest(String externalId, String pipelineName, List<JsonMediaInputObject> media, Map<String,Map> algorithmProperties, Map<String, String> jobProperties, boolean buildOutput, int priority);

	/**
	 * Convenience method: Creates a new JSON-compatible request using the provided inputs, but no record of the
	 * newly-created request is made in the persistent database; this method also exposes functionality for making
	 * a callback when the job completes.
	 */
	JsonJobRequest createRequest(String externalId, String pipelineName, List<JsonMediaInputObject> media, Map<String,Map> algorithmProperties, Map<String, String> jobProperties, boolean buildOutput, int priority, String callback, String method);

	/**
	 * Validates and begins executing the specified jobRequest.
	 * @param jobRequest The request to execute.
	 * @return A persistent entity whose identity {@link org.mitre.mpf.wfm.data.entities.persistent.JobRequest#getId()} id} may be
	 * used to track the progress of the job.
	 * @throws WfmProcessingException If the job could not be executed.
	 */
	JobRequest run(JsonJobRequest jobRequest) throws WfmProcessingException;

	/**
	 * Resubmits a job that has already been received by the system.
	 * @param jobId The id of the job to resubmit.
	 * @return A persistent entity whose identity {@link org.mitre.mpf.wfm.data.entities.persistent.JobRequest#getId()} id} may be
	 * used to track the progress of the job.
	 * @throws WfmProcessingException If the job could not be executed.
	 */
	JobRequest resubmit(long jobId) throws WfmProcessingException;

	/**
	 * Resubmits a job that has already been received by the system using a new priority.
	 * @param jobId The id of the job to resubmit.
	 * @param priority The new priority to assign to the job.
	 * @return A persistent entity whose identity {@link org.mitre.mpf.wfm.data.entities.persistent.JobRequest#getId()} id} may be
	 * used to track the progress of the job.
	 * @throws WfmProcessingException If the job could not be executed.
	 */
	JobRequest resubmit(long jobId, int priority) throws WfmProcessingException;

	/**
	 * Creates, but does not submit, a persistent entity created from the provided input request. The primary benefit
	 * of this method is to return a {@link org.mitre.mpf.wfm.data.entities.persistent.JobRequest#getId() record} of
	 * the job in the persistent database.
	 * @param jobRequest The request to execute.
	 * @return A persistent entity whose identity may be used to track the progress of the job.
	 * @throws WfmProcessingException If the job could not be executed.
	 */
	JobRequest initialize(JsonJobRequest jobRequest) throws WfmProcessingException;

	boolean cancel(long jobId) throws WfmProcessingException;
}
