/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

import org.javasimon.aop.Monitored;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;

import java.time.DateTimeException;
import java.time.LocalDateTime;
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
	boolean addTrack(Track track) throws WfmProcessingException;

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
     * Get the job status type for the specified batch job
     * @param jobId The OpenMPF-assigned ID of the batch job, must be unique.
     * @return Method will return job status type for a batch job.
     */
    BatchJobStatusType getBatchJobStatus(long jobId);
    List<BatchJobStatusType> getBatchJobStatuses(List<Long> jobIds);
    List<String> getBatchJobStatusesAsStrings(List<Long> jobIds);

	/**
	 * Get the job status for the specified streaming job
	 * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
	 * @return Method will return job status for a streaming job.
	 */
	StreamingJobStatus getStreamingJobStatus(long jobId);
	List<StreamingJobStatus> getStreamingJobStatuses(List<Long> jobIds);

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
	 * Set the job status type of the specified batch job.
	 * @param jobId The OpenMPF-assigned ID of the batch job, must be unique.
	 * @param batchJobStatusType The new status type of the specified batch job.
	 * @throws WfmProcessingException is thrown if this method is attempted to be used for a streaming job.
	 */
	void setJobStatus(long jobId, BatchJobStatusType batchJobStatusType)throws WfmProcessingException;

    /**
     * Set the job status type of the specified streaming job. Use this version of the method when
     * streaming job status doesn't include additional detail information.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @param streamingJobStatusType The new status type of the specified streaming job.
     * @throws WfmProcessingException is thrown if this method is attempted to be used for a batch job.
     */
	void setJobStatus(long jobId, StreamingJobStatusType streamingJobStatusType) throws WfmProcessingException;

	/**
	 * Set the job status type of the specified streaming job.
	 * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
	 * @param streamingJobStatus The new status of the specified streaming job.
	 * @throws WfmProcessingException is thrown if this method is attempted to be used for a batch job.
	 */
	void setJobStatus(long jobId, StreamingJobStatus streamingJobStatus) throws WfmProcessingException;

    /**
     * Set the job status of the specified streaming job. Use this form of the method if job status needs
     * to include additional details about the streaming job status.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @param streamingJobStatusType The new status type of the specified streaming job.
     * @param streamingJobStatusDetail Detail information associated with the streaming job status.
     * @throws WfmProcessingException is thrown if this method is attempted to be used for a batch job.
     */
	void setJobStatus(long jobId, StreamingJobStatusType streamingJobStatusType, String streamingJobStatusDetail) throws WfmProcessingException;

    /**
	 * The URL of the callback to connect to when the batch job is completed.
	 * @param jobId The OpenMPF-assigned ID of the batch job to which this callback URL will refer to.
	 * @return The URL of the callback.
	 * @throws WfmProcessingException
	 */
	// TODO change this to method name to URI.
	String getCallbackURL(final long jobId) throws WfmProcessingException;

	/**
	 * The URI of the SummaryReportCallback to connect to when the streaming job is completed.
	 * @param jobId The OpenMPF-assigned ID of the streaming job to which this SummaryReportCallback URI will refer to.
	 * @return The URI of the SummaryReportCallback.
	 * @throws WfmProcessingException
	 */
	String getSummaryReportCallbackURI(final long jobId) throws WfmProcessingException;

	/**
	 * The URI of the HealthReportCallback to connect to when the health report for a streaming job needs to be sent.
	 * @param jobId The OpenMPF-assigned ID of the streaming job to which this HealthReportCallback URI will refer to.
	 * @return The URI of the HealthReportCallback.
	 * @throws WfmProcessingException
	 */
	String getHealthReportCallbackURI(final long jobId) throws WfmProcessingException;

    /**
     * Get the map of unique health report callback URIs associated with the specified jobs.
     * @param jobIds unique job ids of streaming jobs
     * @return Map of healthReportCallbackUri (keys), with each key mapping to the List of jobIds that specified that healthReportCallbackUri
     */
    Map<String, List<Long>> getHealthReportCallbackURIAsMap(List<Long> jobIds);

    /**
     * The method of the callback to connect to when the job is completed. POST or GET.
     * @param jobId The OpenMPF-assigned ID of the job to which this callback method will refer to.
     * @return The method of the callback to connect to when the job is completed. POST or GET.
     * @throws WfmProcessingException
     */
	String getCallbackMethod(final long jobId) throws WfmProcessingException;

	/**
	 * Returns the external id assigned to a job with JobId.
	 * @param jobId The OpenMPF-assigned ID of the job.
	 * @return returns the external id specified for that job or null if an external id was not specified for the job.
	 * @throws WfmProcessingException
     */
	String getExternalId(final long jobId) throws WfmProcessingException;
    List<String> getExternalIds(List<Long> jobIds) throws WfmProcessingException;

	/** Will return true if the specified jobId is a batch job stored in the transient data store
	 * @param jobId The OpenMPF-assigned ID of the job
	 * @return true if the specified jobId is a batch job stored in the transient data store, false otherwise
	 */
	boolean isJobTypeBatch(final long jobId);

	/** Will return true if the specified jobId is a streaming job stored in the transient data store
	 * @param jobId The OpenMPF-assigned ID of the job
	 * @return true if the specified jobId is a streaming job stored in the transient data store, false otherwise
	 */
    boolean isJobTypeStreaming(final long jobId);

    void setStreamingActivity(long jobId, long activityFrameId, LocalDateTime activityTimestamp) throws WfmProcessingException;

    String getActivityFrameIdAsString(long jobId) throws WfmProcessingException;
    List<String> getActivityFrameIdsAsStrings(List<Long> jobIds) throws WfmProcessingException;

    LocalDateTime getActivityTimestamp(long jobId) throws WfmProcessingException, DateTimeException;
    String getActivityTimestampAsString(long jobId) throws WfmProcessingException;

    List<LocalDateTime> getActivityTimestamps(List<Long> jobIds) throws WfmProcessingException, DateTimeException;
	List<String> getActivityTimestampsAsStrings(List<Long> jobIds) throws WfmProcessingException, DateTimeException;

	List<Long> getCurrentStreamingJobs(List<Long> jobIds, boolean isActive );

	/**
	 * Set the doCleanup flag of the specified streaming job
	 * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
	 * @param doCleanup If true, delete the streaming job files from disk as part of cancelling the streaming job.
	 */
	void setDoCleanup(long jobId, boolean doCleanup);

    /**
     * Get the doCleanup flag for the specified streaming job
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @return true if the flag is set and cleanup should be performed; false otherwise
     */
    boolean getDoCleanup(long jobId);

    void addJobWarning(long jobId, String message);

    Set<String> getJobWarnings(long jobId);

    void addJobError(long jobId, String message);

    Set<String> getJobErrors(long jobId);

    TransientDetectionSystemProperties getPropertiesSnapshot(long jobId);
}
