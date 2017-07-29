/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

import org.mitre.mpf.interop.JsonStreamingJobRequest;
import org.mitre.mpf.interop.JsonStreamingInputObject;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;

import java.util.List;
import java.util.Map;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.NotificationConsumer;

public interface StreamingJobRequestBo {

	/**
	 * Convenience method: Creates a new JSON-compatible request using the provided inputs, but no record of the
	 * newly-created request is made in the persistent database; this method also exposes functionality for making
	 * a callback when the job completes.
	 */
	JsonStreamingJobRequest createRequest(String externalId, String pipelineName, JsonStreamingInputObject stream,
										  Map<String, Map<String,String>> algorithmProperties, Map<String, String> jobProperties,
										  boolean buildOutput, int priority,
										  long stallAlertDetectionThreshold, long stallAlertRate, long stallTimeout,
										  String healthReportCallbackURI, String summaryReportCallbackURI, String newTrackAlertCallbackURI, String method);

	/**
	 * Validates and begins executing the specified streamingJobRequest.
	 * @param streamingJobRequest The request to execute.
	 * @return A persistent entity whose identity {@link StreamingJobRequest#getId()} id} may be
	 * used to track the progress of the streaming job.
	 * @throws WfmProcessingException If the streaming job could not be executed.
	 */
	StreamingJobRequest run(JsonStreamingJobRequest streamingJobRequest) throws WfmProcessingException;

	void subscribe(NotificationConsumer<JobCompleteNotification> consumer);
	void unsubscribe(NotificationConsumer<JobCompleteNotification> consumer);

	/**
	 * Creates, but does not submit, a persistent entity created from the provided input request. The primary benefit
	 * of this method is to return a {@link StreamingJobRequest#getId() record} of
	 * the streaming job in the persistent database.
	 * @param streamingJobRequest The request to execute.
	 * @return A persistent entity whose identity may be used to track the progress of the streaming job.
	 * @throws WfmProcessingException If the streaming job could not be executed.
	 */
	StreamingJobRequest initialize(JsonStreamingJobRequest streamingJobRequest) throws WfmProcessingException;

	/**
	 * Cancel a streaming job.
	 * @param jobId The OpenMPF-assigned identifier for the streaming job. The job must be a streaming job
	 * @param doCleanup if true, delete the streaming job files from disk after canceling the streaming job
	 * @return
	 * @throws WfmProcessingException
	 */
	boolean cancel(long jobId, Boolean doCleanup) throws WfmProcessingException;
}
