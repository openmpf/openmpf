/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.mitre.mpf.interop.JsonSegmentSummaryReport;
import org.mitre.mpf.interop.JsonStreamingInputObject;
import org.mitre.mpf.interop.JsonStreamingJobRequest;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.exceptions.JobCancellationInvalidOutputObjectDirectoryWfmProcessingException;
import org.mitre.mpf.wfm.exceptions.JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException;

import java.util.Map;

public interface StreamingJobRequestBo {

	/**
	 * Convenience method: Creates a new JSON-compatible request using the provided inputs, but no record of the
	 * newly-created request is made in the persistent database; this method also exposes functionality for making
	 * a callback when the job completes.
	 */
	JsonStreamingJobRequest createRequest(String externalId, String pipelineName, JsonStreamingInputObject stream,
										  Map<String, Map<String,String>> algorithmProperties, Map<String, String> jobProperties,
										  boolean buildOutput, int priority,
										  long stallTimeout,
										  String healthReportCallbackURI, String summaryReportCallbackURI);

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
	 * Marks a streaming job as CANCELLING in both the TransientStreamingJob and in the long-term database.
     * @param jobId     The OpenMPF-assigned identifier for the streaming job. The job must be a streaming job.
     * @param doCleanup if true, delete the streaming job files from disk as part of cancelling the streaming job.
	 * @exception WfmProcessingException may be thrown if a warning or error occurs.
	 */
	void cancel(long jobId, boolean doCleanup) throws WfmProcessingException;

    /**
     * Deletes files generated by a streaming job.
     * @param jobId The OpenMPF-assigned identifier for the streaming job. The job must be a streaming job.
     * @param outputObjectDirPath location where the job's generated files are stored
     * @exception JobCancellationOutputObjectDirectoryCleanupWarningWfmProcessingException may be thrown
     * if the streaming job has been cancelled, but the jobs output object directory couldn't be deleted when doCleanup is enabled.
     * @exception JobCancellationInvalidOutputObjectDirectoryWfmProcessingException may be thrown if the streaming job has been cancelled, but an error
     * was detected in specification of the jobs output object directory.
     */
	void cleanup(long jobId, String outputObjectDirPath) throws WfmProcessingException;

	/**
	 * Send a health report for all current streaming jobs to the health report callback associated with each streaming job.
	 * @param jobIds all job ids to send health reports for.
	 * @param isActive If true, then streaming jobs which have terminal JobStatus will be
	 * filtered out. Otherwise, all current streaming jobs will be processed.
	 * @throws WfmProcessingException thrown if an error occurs
	 */
	void sendHealthReports() throws WfmProcessingException;

	void handleJobStatusChange(long jobId, StreamingJobStatus status, long timestamp);

	void handleNewActivityAlert(long jobId, long frameId, long timestamp);

	void handleNewSummaryReport(JsonSegmentSummaryReport summaryReport);
}

