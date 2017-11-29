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

package org.mitre.mpf.wfm.data;

import java.time.LocalDateTime;
import org.javasimon.aop.Monitored;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessorInterface;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.data.entities.transients.DetectionProcessingError;
import org.mitre.mpf.wfm.enums.JobStatus;

import java.util.*;

@Monitored
public interface Redis {

	/**
	 * Adds a detection processing error (which encapsulates is itself associated with a job-media-task-action 4-ple) to the Redis data store.
	 * @param detectionProcessingError The non-null error to add.
	 * @return {@literal true} iff the error was successfully stored in Redis.
	 */
    boolean addDetectionProcessingError(DetectionProcessingError detectionProcessingError) throws WfmProcessingException;

	/**
	 * Adds a track instance to the collection of tracks stored in the Redis data store for the associated job, task, and action.
	 * If the track cannot be serialized, the track is silently dropped.
	 * @param track The non-null track to add.
	 * @return {@literal true} iff the track was added to Redis.
	 */
	boolean addTrack(Track track);

	/**
	 * Marks a batch job as cancelled/cancelling in the Redis data store.
	 * @param jobId The MPF-assigned ID of the batch job.
	 */
	boolean cancel(long jobId);

	/** Clears the contents of the Redis data store. Equivalent to performing "FLUSH ALL". */
	void clear();

	/**
	 * Purges all information from the Redis data store associated with a job referenced by its ID.
	 * @param jobId The MPF-assigned ID of the job to purge.
	 */
	void clearJob(long jobId) throws WfmProcessingException;

	/**
	 * Determines if the provided job ID is known to Redis.
	 * @param jobId The ID of the job to look up.
	 * @return {@literal true} iff the job is known to Redis.
     */
	boolean containsJob(long jobId);

	/**
	 * Gets the index of the current task in an in-progress job.
	 * @param jobId The MPF-assigned ID of the job.
	 * @return The current task index.
	 */
	int getCurrentTaskIndexForJob(long jobId);

	/**
	 * Gets the status of a job.
	 * @param jobId The MPF-assigned ID of the job.
	 * @return The status of the job.
	 */
	JobStatus getJobStatus(long jobId);

	/**
	 * Gets the collection of detection processing errors associated with a (job, media, task, action) 4-ple.
	 * @param jobId The MPF-assigned ID of the job.
	 * @param mediaId The MPF-assigned media ID.
	 * @param taskIndex The index of the task in the job's pipeline which generated these errors.
	 * @param actionIndex The index of the action in the job's pipeline's task which generated these errors.
	 * @return A non-null collection of detection processing errors.
	 */
	SortedSet<DetectionProcessingError> getDetectionProcessingErrors(long jobId, long mediaId, int taskIndex, int actionIndex);

	/**
	 * Retrieves the batch job data structure with the specified id from the Redis data store.
	 * @param jobId The MPF-assigned ID of the batch job.
	 * @param mediaIds Optionally, a list of mediaIds to load.  If values are provided for media ids, the returned
	 *                 TransientJob will contain only the TransientMedia objects which relate to those ids. If no
	 *                 values are provided, the TransientJob will include all associated TransientMedia.
	 * @return The transient batch job or {@literal null}.
	 * @throws WfmProcessingException
	 */
	TransientJob getJob(long jobId, Long... mediaIds) throws WfmProcessingException;

	/**
	 * Retrieves the streaming job data structure with the specified id from the Redis data store.
	 * @param jobId The MPF-assigned ID of the streaming job.
	 * @return The transient streaming job or {@literal null}.
	 * @throws WfmProcessingException
	 */
	TransientStreamingJob getStreamingJob(long jobId) throws WfmProcessingException;

	/**
	 * Generates and returns a new and unique sequence value. These IDs are used internally and are not meaningful
	 * outside of the current execution of the WFM.
	 * @return A new and unique value - applies only to this current execution of the WFM.
	 */
	long getNextSequenceValue();

	/**
	 * Retrieves all of the tracks associated with a specific (job, media, task, action) 4-ple.
	 * @param jobId The MPF-assigned ID of the job.
	 * @param mediaId The MPF-assigned media ID.
	 * @param taskIndex The index of the task which created the tracks in the job's pipeline.
	 * @param actionIndex The index of the action in the job's pipeline's task which generated the tracks.
	 * @return A non-null collection of tracks.
	 */
	SortedSet<Track> getTracks(long jobId, long mediaId, int taskIndex, int actionIndex);

	/**
	 * Returns the number of tasks in the pipeline associated with the provided job.
	 * @param jobId The MPF-assigned ID of the job.
	 * @return The number of tasks in the pipeline associated with the provided job. If no such job exists, 0 is returned.
	 * @throws WfmProcessingException
	 */
    int getTaskCountForJob(long jobId) throws WfmProcessingException;

    /**
     * Creates a "key" from one or more components. This is a convenience method for creating
     * keys like JOB:1:MEDIA:15:DETECTION_ERRORS.
     * @param component The required, non-null and non-empty root of the key.
     * @param components The optional collection of additional components in the key.
     * @return A single string built from the concatenation of all of the specified parameters and joined by a delimiter.
     */
    String key(Object component, Object... components);

	/**
	 * Persists the given {@literal TransientJob} instance in the Redis data store.
	 * @param transientJob The non-null instance to store.
	 * @throws WfmProcessingException If persisting the job fails for any reason.
	 */
    void persistJob(TransientJob transientJob) throws WfmProcessingException;

	/**
	 * Persists the given {@literal TransientMedia} instance in the Redis data store and associates it with the given {@literal jobId}.
	 * @param jobId The MPF-assigned ID of the job with which this media is associated.
	 * @param transientMedia The non-null media instance to persist.
	 * @throws WfmProcessingException
	 */
	void persistMedia(long jobId, TransientMedia transientMedia) throws WfmProcessingException;


	/**
	 * Persists the given {@literal TransientStreamingJob} instance in the Redis data store.
	 * @param transientStreamingJob The non-null instance to store.
	 * @throws WfmProcessingException If persisting the job fails for any reason.
	 */
	void persistJob(TransientStreamingJob transientStreamingJob) throws WfmProcessingException;

	/**
	 * Persists the given {@literal TransientStream} instance in the Redis data store and associates it with the given {@literal jobId}.
	 * @param jobId The MPF-assigned ID of the job with which this stream is associated.
	 * @param transientStream The non-null stream instance to persist.
	 * @throws WfmProcessingException
	 */
	void persistStream(long jobId, TransientStream transientStream) throws WfmProcessingException;


	/**
	 * Updates the "current task index" of a job to the specified task index.
	 * @param jobId The MPF-assigned ID of the job.
	 * @param taskIndex The index of the task which should be used as the "current" task.
	 */
	void setCurrentTaskIndex(long jobId, int taskIndex);

	/**
	 * Updates the collection of tracks associated with a given (job, media, task, action) 4-ple using to the provided collection of tracks.
	 * @param jobId The MPF-assigned ID of the job.
	 * @param mediaId The MPF-assigned media ID.
	 * @param taskIndex The index of the task which created the tracks in the job's pipeline.
	 * @param actionIndex The index of the action in the job's pipeline's task which generated the tracks.
	 * @param tracks The collection of tracks to associate with the (job, media, task, action) 4-ple.
	 */
	void setTracks(long jobId, long mediaId, int taskIndex, int actionIndex, Collection<Track> tracks);

	/**
	 * Updates the status of a job to the specified status.
	 * @param jobId The MPF-assigned ID of the job.
	 * @param jobStatus The new status of the specified job.
	 */
	void setJobStatus(long jobId, JobStatus jobStatus);

	/**
	 * The URL of the Callback to connect to when the batch job is completed.
	 * @param jobId The MPF-assigned ID of the batch job to which this Callback URL will refer to.
	 * @return The URL of the Callback.
	 * @throws WfmProcessingException
	 */
	String getCallbackURL(final long jobId) throws WfmProcessingException;

	/**
	 * The URL of the SummaryReportCallback to connect to when the streaming job is completed.
	 * @param jobId The MPF-assigned ID of the streaming job to which this SummaryReportCallback URI will refer to.
	 * @return The URI of the SummaryReportCallback.
	 * @throws WfmProcessingException
	 */
	String getSummaryReportCallbackURI(final long jobId) throws WfmProcessingException;

	/**
	 * The URL of the HealthReportCallback to connect to when the health report for a streaming job needs to be sent.
	 * @param jobId The MPF-assigned ID of the streaming job to which this HealthReportCallback URI will refer to.
	 * @return The URI of the HealthReportCallback.
	 * @throws WfmProcessingException
	 */
	String getHealthReportCallbackURI(final long jobId) throws WfmProcessingException;

	/**
	 * The METHOD of the Callback to connect to when the job is completed. POST or GET.
	 * @param jobId The MPF-assigned ID of the job to which this Callback Method will refer to.
	 * @return The METHOD of the Callback to connect to when the job is completed. POST or GET.
	 * @throws WfmProcessingException
	 */
	String getCallbackMethod(final long jobId) throws WfmProcessingException;

	/**
	 * Returns the external id assigned to a job with JobId.
	 * @param jobId The MPF-assigned ID of the job.
	 * @return returns a job external_id or null if no job.
	 * @throws WfmProcessingException
     */
	String getExternalId(final long jobId) throws WfmProcessingException;

	/** Will return true if the specified jobId is a batch job stored in the transient data store
	 * @param jobId The MPF-assigned ID of the job
	 * @return true if the specified jobId is a batch job stored in the transient data store, false otherwise
	 */
	boolean isJobTypeBatch(final long jobId);

	/** Will return true if the specified jobId is a streaming job stored in the transient data store
	 * @param jobId The MPF-assigned ID of the job
	 * @return true if the specified jobId is a streaming job stored in the transient data store, false otherwise
	 */
	boolean isJobTypeStreaming(final long jobId);

	void setHealthReportLastTimestamp(long jobId, LocalDateTime lastHealthReportTimestamp) throws WfmProcessingException;
	LocalDateTime getHealthReportLastTimestamp(long jobId) throws WfmProcessingException;

    void setHealthReportLastNewActivityAlertFrameId(long jobId, String lastNewActivityAlertFrameId) throws WfmProcessingException;
    String getHealthReportLastNewActivityAlertFrameId(long jobId) throws WfmProcessingException;

	}
