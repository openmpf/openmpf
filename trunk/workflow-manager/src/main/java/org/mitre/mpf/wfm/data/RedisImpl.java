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

import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.interop.util.TimeUtils;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Component(RedisImpl.REF)
public class RedisImpl implements Redis {

    public static final String REF = "redisImpl";
    private static final Logger log = LoggerFactory.getLogger(RedisImpl.class);

    @Autowired
    @SuppressWarnings("unused")
    private RedisTemplate redisTemplate;

    @Autowired
    @SuppressWarnings("unused")
    private JsonUtils jsonUtils;

    @PostConstruct
    @SuppressWarnings({"unchecked", "unused"})
    private synchronized void init() {
        // On startup, clear the contents of the Redis data store. Once cleared, create the sequence value used by
        // getNextSequenceValue() and initialize it to 0.
        clear();
        redisTemplate.boundValueOps(SEQUENCE).set(0L);
    }

    //
    // KEY CREATION
    //

    // The following constants are provided to avoid making typographical errors when formulating keys.
    // Note: BATCH_JOB represents a batch job, while STREAMING_JOB represents a streaming job.
    private static final String
            CANCELLED = "CANCELLED",
            DETAIL = "DETAIL",
            ERRORS = "ERRORS",
            EXTERNAL_ID = "EXTERNAL_ID",
            BATCH_JOB = "BATCH_JOB",
            MEDIA = "MEDIA",
            OUTPUT_ENABLED = "OUTPUT_ENABLED",
            OUTPUT_OBJECT_PATH = "OUTPUT_OBJECT_PATH",
            PIPELINE = "PIPELINE",
            PRIORITY = "PRIORITY",
            OVERRIDDEN_ALGORITHM_PROPERTIES = "OVERRIDDEN_ALGORITHM_PROPERTIES",
            OVERRIDDEN_JOB_PROPERTIES = "OVERRIDDEN_JOB_PROPERTIES",
            CALLBACK_URL = "CALLBACK_URL",
            CALLBACK_METHOD = "CALLBACK_METHOD",
            TASK = "TASK",
            TASK_COUNT = "TASK_COUNT",
            TRACK = "TRACK",
            SEQUENCE = "SEQUENCE",
            BATCH_JOB_STATUS = "BATCH_JOB_STATUS",
            STREAMING_JOB_STATUS = "STREAMING_JOB_STATUS",
            STREAMING_JOB_STATUS_DETAIL = "STREAMING_JOB_STATUS_DETAIL",
            STREAMING_JOB = "STREAMING_JOB",
            STREAM = "STREAM",
            STALL_TIMEOUT = "STALL_TIMEOUT",
            HEALTH_REPORT_CALLBACK_URI = "HEALTH_REPORT_CALLBACK_URI",
            ACTIVITY_FRAME_ID = "ACTIVITY_FRAME_ID",
            ACTIVITY_TIMESTAMP = "ACTIVITY_TIMESTAMP",
            SUMMARY_REPORT_CALLBACK_URI = "SUMMARY_REPORT_CALLBACK_URI",
            DO_CLEANUP = "DO_CLEANUP";

    /**
     * Creates a "key" from one or more components. This is a convenience method for creating
     * keys like BATCH_JOB:1:MEDIA:15:DETECTION_ERRORS or STREAMING_JOB:1:STREAM:15
     * @param component The required, non-null and non-empty root of the key.
     * @param components The optional collection of additional components in the key.
     * @return A single string built from the concatenation of all of the specified parameters and joined by a delimiter.
     */
    @Override
    public String key(Object component, Object... components) {
        // Return a key of the format FOO, FOO:BAR, FOO:BAR:BUZZ, etc...
        return component + ( (components == null || components.length == 0) ? "" : ":" + StringUtils.join(components, ":") );
    }

    //
    // INTERFACE IMPLEMENTATION (See interface for documentation)
    //

    @Override
    @SuppressWarnings("unchecked")
    public synchronized boolean addDetectionProcessingError(DetectionProcessingError detectionProcessingError) throws WfmProcessingException {
        if ( detectionProcessingError == null ) {
            // Receiving a null parameter may be a symptom of a much larger issue. This shouldn't happen, but if it does handle it gracefully by logging a error and returning false.
            log.error("IInvalid argument. detectionProcessingError should not be null.");
            return false;
        } else if ( isJobTypeStreaming(detectionProcessingError.getJobId()) ) {
            // This method should not be called for a streaming job.
            throw new WfmProcessingException("Error: This method should not be called for streaming jobs. Rejected this call for streaming job " + detectionProcessingError.getJobId());
        } else if ( isJobTypeBatch(detectionProcessingError.getJobId()) ) {
            // If we get to here, we know this job should be a valid batch job.
            try {
                redisTemplate
                    .boundListOps(
                        key(BATCH_JOB, detectionProcessingError.getJobId(),
                            MEDIA, detectionProcessingError.getMediaId(),
                            ERRORS, detectionProcessingError.getStageIndex(),
                            detectionProcessingError
                                .getActionIndex())) // e.g., BATCH_JOB:5:MEDIA:3:ERRORS:0:1
                    .rightPush(jsonUtils.serialize(detectionProcessingError));
                return true;
            } catch (Exception exception) {
                log.error("Failed to persist the detection processing error {} in the transient data store due to an exception.",
                    detectionProcessingError, exception);
                return false;
            }
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning false.
            log.warn("Warning, jobId " + detectionProcessingError.getJobId() + " is not known to the system. Failed to add the detection processing error.");
            return false;
        }
    }


    @Override
    @SuppressWarnings("unchecked")
    public boolean addTrack(Track track) throws WfmProcessingException{
        if ( track == null ) {
            // Receiving a null parameter may be a symptom of a much larger issue. This shouldn't happen, but if it does handle it gracefully by logging a error and returning false.
            log.error("Invalid argument. Track should not be null.");
            return false;
        } else if( isJobTypeStreaming(track.getJobId()) ) {
            // This method should not be called for a streaming job.
            throw new WfmProcessingException("Error: This method should not be called for streaming jobs. Rejected this call for streaming job " + track.getJobId());
        } else if ( isJobTypeBatch(track.getJobId()) ) {
            try {
                redisTemplate
                        .boundListOps(key(BATCH_JOB, track.getJobId(), MEDIA, track.getMediaId(), TRACK, track.getStageIndex(), track.getActionIndex())) // e.g., BATCH_JOB:5:MEDIA:10:0:0
                        .rightPush(jsonUtils.serialize(track));
                return true;
            } catch ( Exception exception ) {
                log.error("Failed to serialize or store the track instance '{}' due to an exception.", track, exception);
                return false;
            }
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning false.
            log.warn("Warning, jobId " + track.getJobId() + " is not known to the system. Failed to add the track.");
            return false;
        }
    }

    /**
     * marked the batch or streaming job as CANCELLED in REDIS
     * @param jobId The OpenMPF-assigned ID of the batch or streaming job (must be unique)
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized boolean cancel(long jobId) {
        if ( isJobTypeBatch(jobId) ) {
            // confirmed that this is a batch job
            if ( redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).size() > 0 ) {
                redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).put(CANCELLED, true);
                return true;
            } else {
                log.warn("Batch Job #{} is not running and cannot be cancelled.", jobId);
                return false;
            }
        } else if ( isJobTypeStreaming(jobId) ) {
            // confirmed that this is a streaming job
            if ( redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).size() > 0 ) {
                redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).put(CANCELLED, true);
                return true;
            } else {
                log.warn("Streaming Job #{} is not running and cannot be cancelled.", jobId);
                return false;
            }
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning false.
            log.warn("Job #{} was not found as a batch or a streaming job so it cannot be cancelled in REDIS.", jobId);
            return false;
        }

    }

    /** Removes everything in the Redis datastore. */
    @Override
    public void clear() {
        redisTemplate.execute(new RedisCallback() {
            @Override
            public Object doInRedis(RedisConnection redisConnection) throws DataAccessException {
                redisConnection.flushAll();
                return null;
            }
        });
    }

    /**
     * Clear the specified job from the REDIS database
     * @param jobId The OpenMPF-assigned ID of the batch or streaming job to purge (must be unique)
     * @throws WfmProcessingException
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void clearJob(long jobId) throws WfmProcessingException {
        if ( isJobTypeBatch(jobId) ) {
            // confirmed that this is a batch job that was requested to be cleared
            TransientJob transientJob = getJob(jobId);
            if ( transientJob == null ) {
                return;
            }
            redisTemplate.boundSetOps(BATCH_JOB).remove(Long.toString(jobId));
            redisTemplate.delete(key(BATCH_JOB, jobId));
            for ( TransientMedia transientMedia : transientJob.getMedia() ) {
                redisTemplate.delete(key(BATCH_JOB, jobId, MEDIA, transientMedia.getId()));
                if ( transientJob.getPipeline() != null ) {
                    for ( int taskIndex = 0; taskIndex < transientJob.getPipeline().getStages().size(); taskIndex++ ) {
                        for ( int actionIndex = 0; actionIndex < transientJob.getPipeline().getStages().get(taskIndex).getActions().size(); actionIndex++ ) {
                            redisTemplate.delete(key(BATCH_JOB, jobId, MEDIA, transientMedia.getId(), ERRORS, taskIndex, actionIndex));
                            redisTemplate.delete(key(BATCH_JOB, jobId, MEDIA, transientMedia.getId(), TRACK, taskIndex, actionIndex));
                        }
                    }
                }

                if ( transientMedia.getUriScheme().isRemote() ) {
                    if ( transientMedia.getLocalPath() != null ) {
                        try {
                            File file = new File(transientMedia.getLocalPath());
                            if ( file.exists() ) {
                                if ( !file.delete() ) {
                                    log.warn("Failed to delete the file '{}'. It has been leaked and must be removed manually.", file);
                                }
                            }
                        } catch ( Exception exception ) {
                            log.warn("Failed to delete the local file '{}' which was created retrieved from a remote location - it must be manually deleted.", transientMedia.getLocalPath(), exception);
                        }
                    }
                }
            }
        } else if ( isJobTypeStreaming(jobId) ) {
            // confirmed that this is a streaming job that was requested to be cleared
            TransientStreamingJob transientStreamingJob = getStreamingJob(jobId);
            if ( transientStreamingJob == null ) {
                return;
            }
            redisTemplate.boundSetOps(STREAMING_JOB).remove(Long.toString(jobId));
            redisTemplate.delete(key(STREAMING_JOB, jobId));
            TransientStream transientStream = transientStreamingJob.getStream();

            redisTemplate.delete(key(STREAMING_JOB, jobId, STREAM, transientStream.getId()));
            if ( transientStreamingJob.getPipeline() != null ) {
                for ( int taskIndex = 0; taskIndex < transientStreamingJob.getPipeline().getStages().size(); taskIndex++ ) {
                    for ( int actionIndex = 0; actionIndex < transientStreamingJob.getPipeline().getStages().get(taskIndex).getActions().size(); actionIndex++ ) {
                        redisTemplate.delete(key(STREAMING_JOB, jobId, STREAM, transientStream.getId(), ERRORS, taskIndex, actionIndex));
                        redisTemplate.delete(key(STREAMING_JOB, jobId, STREAM, transientStream.getId(), TRACK, taskIndex, actionIndex));
                    }
                }
            }
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning.
            log.warn("Job #{} was not found as a batch or a streaming job so it cannot be cleared from REDIS.", jobId);
        }
    }

    /**
     * See if the specified job has been persisted in the REDIS database
     * @param jobId The ID of the batch or streaming job to look up, must be unique
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized boolean containsJob(long jobId) {
        if ( isJobTypeBatch(jobId) ) {
            // confirmed that the specified job is a batch job
            return redisTemplate.boundSetOps(BATCH_JOB).members().contains(Long.toString(jobId));
        } else if ( isJobTypeStreaming(jobId) ) {
            // confirmed that the specified job is a streaming job
            return redisTemplate.boundSetOps(STREAMING_JOB).members().contains(Long.toString(jobId));
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning false.
            log.warn("Job #{} was not found as a batch or a streaming job so we don't know if this job was persisted.", jobId);
            return false;
        }
    }

    /**
     * Get the task index for the specified batch job. Note that stage tracking is not supported for streaming jobs
     * @param jobId The OpenMPF-assigned ID of the batch job to look up, must be unique.
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public int getCurrentTaskIndexForJob(long jobId)  {
        if ( isJobTypeBatch(jobId) ) {
            // confirmed that the specified job is a batch job
            if ( !redisTemplate.boundSetOps(BATCH_JOB).members().contains(Long.toString(jobId)) ) {
                return 0;
            } else {
                Map jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();
                return (int)(jobHash.get(TASK));
            }
        } else if ( isJobTypeStreaming(jobId) ) {
            // WFM does not keep track of the stage for streaming jobs.
            throw new WfmProcessingException("Error: This method should not be called for streaming jobs. Rejected this call for streaming job " + jobId);
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning -1.
            log.warn("Warning, jobId " + jobId + " is not known to the system. Failed to get the current task index for this job.");
            return -1;
        }
    }

    /**
     * Set the job status type of the specified batch job.
     * @param jobId The OpenMPF-assigned ID of the batch job, must be unique.
     * @param batchJobStatusType The new status type of the specified batch job.
     * @throws WfmProcessingException is thrown if this method is attempted to be used for a streaming job.
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void setJobStatus(long jobId, BatchJobStatusType batchJobStatusType) throws WfmProcessingException {
        if ( isJobTypeBatch(jobId) ) {
            redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).put(BATCH_JOB_STATUS, batchJobStatusType);
        } else if ( isJobTypeStreaming(jobId) ) {
            // this method shouldn't be called for streaming jobs.
            throw new WfmProcessingException("Error: this method shouldn't be called for streaming jobs. Rejected this call for job " + jobId);
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and ignoring the request.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't set the job status", jobId);
        }
    }

    /**
     * Set the job status type of the specified streaming job. Use this version of the method when
     * streaming job status doesn't include additional detail information.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @param streamingJobStatusType The new status type of the specified streaming job.
     * @throws WfmProcessingException is thrown if this method is attempted to be used for a batch job.
     */
    @Override
    public synchronized void setJobStatus(long jobId, StreamingJobStatusType streamingJobStatusType) throws WfmProcessingException {
        setJobStatus(jobId, streamingJobStatusType, null);
    }

    /**
     * Set the job status type of the specified streaming job.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @param streamingJobStatus The new status of the specified streaming job.
     * @throws WfmProcessingException is thrown if this method is attempted to be used for a batch job.
     */
    @Override
    public synchronized void setJobStatus(long jobId, StreamingJobStatus streamingJobStatus) throws WfmProcessingException {
        setJobStatus(jobId, streamingJobStatus.getType(), streamingJobStatus.getDetail());
    }

    /**
     * Set the job status of the specified streaming job. Use this form of the method if job status needs
     * to include additional details about the streaming job status.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @param streamingJobStatusType The new status type of the specified streaming job.
     * @param streamingJobStatusDetail Detail information associated with the streaming job status.
     * @throws WfmProcessingException is thrown if this method is attempted to be used for a batch job.
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void setJobStatus(long jobId, StreamingJobStatusType streamingJobStatusType, String streamingJobStatusDetail) throws WfmProcessingException {
        if ( isJobTypeBatch(jobId) ) {
            // this method shouldn't be called for batch jobs.
            throw new WfmProcessingException("Error: this method shouldn't be called for batch jobs. Rejected this call for job " + jobId);
        } else if ( isJobTypeStreaming(jobId) ) {
            redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).put(STREAMING_JOB_STATUS, streamingJobStatusType);
            if ( streamingJobStatusDetail != null ) {
                redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).put(STREAMING_JOB_STATUS_DETAIL, streamingJobStatusDetail);
            } else {
                // Clear the streaming job status detail if the job status detail was passed as null
                redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).delete(STREAMING_JOB_STATUS_DETAIL);
            }
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and ignoring the request.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't set the job status", jobId);
        }
    }

    /**
     * Get the job status for the specified batch job
     * @param jobId The OpenMPF-assigned ID of the batch job, must be unique.
     * @return Method will return job status for the batch job.
     * @exception WfmProcessingException is thrown if this method is called for a streaming job.
     */
    @Override
    @SuppressWarnings("unchecked")
    public BatchJobStatusType getBatchJobStatus(long jobId) throws WfmProcessingException {
        if ( isJobTypeBatch(jobId) ) {
            // confirmed that the specified job is a batch job
            if( !redisTemplate.boundSetOps(BATCH_JOB).members().contains(Long.toString(jobId)) ) {
                // There is no batch job with that job id in REDIS, return null
                return null;
            } else {
                Map jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();
                return (BatchJobStatusType) jobHash.get(BATCH_JOB_STATUS);
            }
        } else if ( isJobTypeStreaming(jobId) ) {
            // this method shouldn't be called for streaming jobs.
            throw new WfmProcessingException("Error: this method shouldn't be called for streaming jobs. Rejected this call for job " + jobId);
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't return the job status (returning null).", jobId);
            return null;
        }
    }

    /**
     * Get the job status for the specified streaming job
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @return Method will return job status of the streaming job.
     * @exception WfmProcessingException is thrown if this method is called for a batch job.
     */
    @Override
    @SuppressWarnings("unchecked")
    public StreamingJobStatus getStreamingJobStatus(long jobId) throws WfmProcessingException {
        if ( isJobTypeBatch(jobId) ) {
            // this method shouldn't be called for batch jobs.
            throw new WfmProcessingException("Error: this method shouldn't be called for batch jobs. Rejected this call for job " + jobId);
        } else if ( isJobTypeStreaming(jobId) ) {
            // confirmed that the specified job is a streaming job
            if ( !redisTemplate.boundSetOps(STREAMING_JOB).members().contains(Long.toString(jobId)) ) {
                // There is no streaming job with that job id in REDIS, return null
                return null;
            } else {
                Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
                StreamingJobStatusType jobStatusType = (StreamingJobStatusType) jobHash.get(STREAMING_JOB_STATUS);
                if ( jobStatusType != null ) {
                    // Return streaming job status that was stored for this job in REDIS. Note the constructor handles possible null for jobStatusDetail
                    String jobStatusDetail = (String) jobHash.get(STREAMING_JOB_STATUS_DETAIL);
                    return new StreamingJobStatus(jobStatusType, jobStatusDetail);
                } else {
                    // Job status hasn't been stored for this job, return null;
                    return null;
                }
            }
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't return the job status (returning null).", jobId);
            return null;
        }
    }

    /** This method is the same as {@link #getBatchJobStatus(long)}, it's just adapted for use with Lists.
     * @param jobIds List of batch jobIds
     * @return List of BatchJobStatusType for the specified batch jobs. The List may contain nulls for invalid jobIds.
     */
    @Override
    public List<BatchJobStatusType> getBatchJobStatuses(List<Long> jobIds) throws WfmProcessingException {
        return jobIds.stream().map(this::getBatchJobStatus).collect(Collectors.toList());
    }

    /** This method is the same as {@link #getStreamingJobStatus(long)}, it's just adapted for use with Lists.
     * @param jobIds List of streaming jobIds
     * @return List of StreamingJobStatus for the specified jobs. The List may contain nulls for invalid jobIds.
     */
    @Override
    public List<StreamingJobStatus> getStreamingJobStatuses(List<Long> jobIds) throws WfmProcessingException {
        return jobIds.stream().map(this::getStreamingJobStatus).collect(Collectors.toList());
    }

    /** This method is similar to {@link #getBatchJobStatuses(List<Long>)}, it just returns a List of Strings describing the batch job status types instead of a List of BatchJobStatuses.
     * @param jobIds List of batch jobIds
     * @return List of batch job status types as Strings for the specified jobs. The List may contain nulls for invalid jobIds.
     */
    @Override
    public List<String> getBatchJobStatusesAsStrings(List<Long> jobIds) {
        return getBatchJobStatuses(jobIds).stream().map(Enum::name).collect(Collectors.toList());
    }

    /**
     * Get the detection errors for the batch job
     * @param jobId The OpenMPF-assigned ID of the batch job.
     * @param mediaId The OpenMPF-assigned media ID.
     * @param taskIndex The index of the task in the job's pipeline which generated these errors.
     * @param actionIndex The index of the action in the job's pipeline's task which generated these errors.
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized SortedSet<DetectionProcessingError> getDetectionProcessingErrors(long jobId, long mediaId, int taskIndex, int actionIndex) {
        if ( isJobTypeBatch(jobId) ) {
            // confirmed that the specified job is a batch job
            final String key = key(BATCH_JOB, jobId, MEDIA, mediaId, ERRORS, taskIndex, actionIndex);
            int length = (Integer) (redisTemplate.execute(new RedisCallback() {
                @Override
                public Object doInRedis(RedisConnection redisConnection) throws DataAccessException {
                    return Integer.valueOf(redisConnection.execute("llen", key.getBytes()).toString());
                }
            }));

            if ( length == 0 ) {
                log.debug("No detection processing errors for BATCH_JOB:{}:MEDIA:{}:{}:{}.", jobId, mediaId, taskIndex, actionIndex);
                return new TreeSet<>();
            } else {
                log.debug("{} detection processing errors for BATCH_JOB:{}:MEDIA:{}:{}:{}.", length, jobId, mediaId, taskIndex, actionIndex);
                SortedSet<DetectionProcessingError> errors = new TreeSet<>();
                for ( Object errorJson : redisTemplate.boundListOps(key).range(0, length) ) {
                    try {
                        errors.add(jsonUtils.deserialize((byte[]) (errorJson), DetectionProcessingError.class));
                    } catch ( Exception exception ) {
                        log.warn("Failed to deserialize '{}'.", errorJson);
                    }
                }
                return errors;
            }
        } else if ( isJobTypeStreaming(jobId) ) {
            // This method should not be called for a streaming jobs.
            throw new WfmProcessingException("Error: This method should not be called for streaming jobs. Rejected this call for streaming job " + jobId);
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't return the detection processing errors (returning null).", jobId);
            return null;
        }
    }

    /**
     * Get the transient representation of the batch job as specified by the batch jobs unique jobId.
     * REDIS should only contain non-terminal jobs.
     * This method should not be called for streaming jobs.
     * @param jobId The OpenMPF-assigned ID of the batch job, must be unique
     * @param mediaIds Optionally, a list of mediaIds to load.  If values are provided for media ids, the returned
     *                 TransientJob will contain only the TransientMedia objects which relate to those ids. If no
     *                 values are provided, the TransientJob will include all associated TransientMedia.
     * @return
     * @throws WfmProcessingException
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized TransientJob getJob(long jobId, Long... mediaIds) throws WfmProcessingException {
        if ( isJobTypeStreaming(jobId) ) {
            // This method should not be called for a streaming job.
            throw new WfmProcessingException("Error: This method should not be called for streaming jobs. Rejected this call for streaming job " + jobId);
        } else if ( isJobTypeBatch(jobId) ) {
            // The batch job is known to the system and should be retrievable.
            // Get the hash containing the job properties.
            Map<String, Object> jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();

            TransientJob transientJob = new TransientJob(jobId,
                    (String) (jobHash.get(EXTERNAL_ID)),
                    jsonUtils.deserialize((byte[]) (jobHash.get(PIPELINE)), TransientPipeline.class),
                    (Integer) (jobHash.get(TASK)),
                    (Integer) (jobHash.get(PRIORITY)),
                    (Boolean) (jobHash.get(OUTPUT_ENABLED)),
                    (Boolean) (jobHash.get(CANCELLED)),
                    (String) (jobHash.get(CALLBACK_URL)),
                    (String) (jobHash.get(CALLBACK_METHOD))
            );

            transientJob.getOverriddenJobProperties().putAll(jsonUtils.deserialize((byte[])jobHash.get(OVERRIDDEN_JOB_PROPERTIES), HashMap.class));
            transientJob.getOverriddenAlgorithmProperties().putAll(jsonUtils.deserialize((byte[])jobHash.get(OVERRIDDEN_ALGORITHM_PROPERTIES), HashMap.class));

            Long[] mediaToRetrieve;
            if ( mediaIds == null || mediaIds.length == 0 ) {
                List<Long> mediaIdList = (List<Long>) (jobHash.get(MEDIA));
                mediaToRetrieve = mediaIdList.toArray(new Long[mediaIdList.size()]);
            } else {
                mediaToRetrieve = mediaIds;
            }
            List<TransientMedia> transientMediaList = new ArrayList<>(1);
            for ( Long mediaId : mediaToRetrieve ) {
                TransientMedia media = jsonUtils.deserialize((byte[]) (redisTemplate.boundValueOps(key(BATCH_JOB, jobId, MEDIA, mediaId)).get()), TransientMedia.class);
                if ( media != null ) {
                    transientMediaList.add(media);
                } else {
                    log.warn("Specified media object with id {} not found for job {}.  Skipping.", mediaId, jobId);
                }
            }
            transientJob.getMedia().addAll(transientMediaList);
            return transientJob;
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't return the batch transient job (returning null).", jobId);
            return null;
        }

    }


    /**
     * Get the transient representation of the streaming job as specified by the streaming jobs unique jobId.
     * REDIS should only contain non-terminal jobs.
     * This method should not be called for batch jobs.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @return
     * @throws WfmProcessingException
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized TransientStreamingJob getStreamingJob(long jobId) throws WfmProcessingException {

        if (isJobTypeStreaming(jobId)) {
            // The streaming job is known to the system and should be retrievable.
            // Get the hash containing the job properties.
            Map<String, Object> jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId))
                .entries();

            TransientStreamingJob transientStreamingJob = new TransientStreamingJob(jobId,
                (String) (jobHash.get(EXTERNAL_ID)),
                jsonUtils.deserialize((byte[]) (jobHash.get(PIPELINE)), TransientPipeline.class),
                (Integer) (jobHash.get(PRIORITY)),
                (Long) (jobHash.get(STALL_TIMEOUT)),
                (Boolean) (jobHash.get(OUTPUT_ENABLED)),
                (String) (jobHash.get(OUTPUT_OBJECT_PATH)),
                (Boolean) (jobHash.get(CANCELLED)),
                (String) (jobHash.get(HEALTH_REPORT_CALLBACK_URI)),
                (String) (jobHash.get(SUMMARY_REPORT_CALLBACK_URI)));

            transientStreamingJob.getOverriddenJobProperties().putAll(jsonUtils
                .deserialize((byte[]) jobHash.get(OVERRIDDEN_JOB_PROPERTIES), HashMap.class));
            transientStreamingJob.getOverriddenAlgorithmProperties().putAll(jsonUtils
                .deserialize((byte[]) jobHash.get(OVERRIDDEN_ALGORITHM_PROPERTIES), HashMap.class));

            Long streamId = (Long) jobHash.get(STREAM);
            TransientStream transientStream = jsonUtils.deserialize(
                (byte[]) (redisTemplate.boundValueOps(key(STREAMING_JOB, jobId, STREAM, streamId))
                    .get()), TransientStream.class);

            transientStreamingJob.setStream(transientStream);
            return transientStreamingJob;

        } else if (isJobTypeBatch(jobId)) {
            // This method should not be called for a batch job.
            throw new WfmProcessingException("Error: This method should not be called for batch jobs. Rejected this call for batch job " + jobId);

        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't return the streaming transient job (returning null).", jobId);
            return null;

        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized long getNextSequenceValue() {
        Long id = (Long)(redisTemplate.boundValueOps(SEQUENCE).get());
        if ( id == null ) {
            id = 0L;
        }
        redisTemplate.boundValueOps(SEQUENCE).set(id+1);
        return id;
    }

    /**
     * Get the task count for the specified batch or streaming job
     * @param jobId The OpenMPF-assigned ID of the batch or the streaming job, must be unique.
     * @return
     * @throws WfmProcessingException
     */
    @Override
    @SuppressWarnings("unchecked")
    public int getTaskCountForJob(long jobId) throws WfmProcessingException {
        if ( isJobTypeBatch(jobId) ) {
            // confirmed that this is a batch job
            Map jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();
            return (int) (jobHash.get(TASK_COUNT));
        } else if ( isJobTypeStreaming(jobId) ) {
            // confirmed that this is a streaming job
            Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
            return (int) (jobHash.get(TASK_COUNT));
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning 0.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't return the task count (returning 0).", jobId);
            return 0;
        }
    }

    /** Get the tracks for the specified batch job
     * Note: to be consistent with legacy unit test processing, this method assumes that the job is a batch job.  This method should not be called for streaming jobs
     * This method will check to see if the specified jobId has been stored in REDIS and is associated with a streaming job.  If this is the case,
     * it will return a null
     * @param jobId The OpenMPF-assigned ID of the batch job, must be unique
     * @param mediaId The OpenMPF-assigned media ID.
     * @param taskIndex The index of the task which created the tracks in the job's pipeline.
     * @param actionIndex The index of the action in the job's pipeline's task which generated the tracks.
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized SortedSet<Track> getTracks(long jobId, long mediaId, int taskIndex, int actionIndex) throws WfmProcessingException {
        if ( isJobTypeStreaming(jobId) ) {
            // This method should not be called for a streaming job.
            throw new WfmProcessingException("Error: This method should not be called for streaming jobs. Rejected this call for streaming job " + jobId);
        } else if ( isJobTypeBatch(jobId) ) {
            // assume that this jobId is associated with a batch job
            final String key = key(BATCH_JOB, jobId, MEDIA, mediaId, TRACK, taskIndex, actionIndex);
            int length = (Integer) (redisTemplate.execute(new RedisCallback() {
                @Override
                public Object doInRedis(RedisConnection redisConnection) throws DataAccessException {
                    return Integer.valueOf(redisConnection.execute("llen", key.getBytes()).toString());
                }
            }));

            if ( length == 0 ) {
                log.debug("No tracks for BATCH_JOB:{}:MEDIA:{}:{}:{}.", jobId, mediaId, taskIndex, actionIndex);
                return new TreeSet<>();
            } else {
                log.debug("{} tracks for BATCH_JOB:{}:MEDIA:{}:{}:{}.", length, jobId, mediaId, taskIndex, actionIndex);
                SortedSet<Track> tracks = new TreeSet<>();
                for ( Object trackJson : redisTemplate.boundListOps(key).range(0, length) ) {
                    try {
                        tracks.add(jsonUtils.deserialize((byte[]) (trackJson), Track.class));
                    } catch ( Exception exception ) {
                        log.warn("Failed to deserialize '{}'.", trackJson);
                    }
                }
                return tracks;
            }
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't return the tracks (returning null).", jobId);
            return null;
        }
    }

    /**
     * Persist a batch job by storing it in the REDIS database.
     * REDIS should only contain non-terminal jobs.
     * @param transientJob The non-null instance to store.
     * @throws WfmProcessingException
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void persistJob(TransientJob transientJob) throws WfmProcessingException {
        // Redis cannot store complex objects, so it is necessary to store complex objects using
        // less complex data structures such as lists and hashes (maps). The jobHash variable
        // is used to store the simple properties of a job in a map.
        Map<String, Object> jobHash = new HashMap<>();

        // The collection of Redis-assigned IDs for the media elements associated with this job.
        List<Long> mediaIds = new ArrayList<>();

        for ( TransientMedia transientMedia : transientJob.getMedia() ) {
            // For each media element in the job, add a copy of the id to our mediaIds collection
            // and then store the serialized media element to the specified key.
            mediaIds.add(transientMedia.getId());
            redisTemplate
                    .boundValueOps(key(BATCH_JOB, transientJob.getId(), MEDIA, transientMedia.getId())) // e.g., BATCH_JOB:5:MEDIA:16
                    .set(jsonUtils.serialize(transientMedia));
        }

        // Associate the list of media ids with the job hash.
        jobHash.put(MEDIA, mediaIds);

        // Copy the remaining properties from the java object to the job hash...
        // Note: need to convert from ByteArray (using JsonUtils.serialize) from REDIS to Java
        jobHash.put(PIPELINE, jsonUtils.serialize(transientJob.getPipeline())); // Serialized to conserve space.
        jobHash.put(OVERRIDDEN_JOB_PROPERTIES, jsonUtils.serialize(transientJob.getOverriddenJobProperties()));
        jobHash.put(OVERRIDDEN_ALGORITHM_PROPERTIES, jsonUtils.serialize(transientJob.getOverriddenAlgorithmProperties()));
        jobHash.put(TASK, transientJob.getCurrentStage());
        jobHash.put(TASK_COUNT, transientJob.getPipeline() == null ? 0 : transientJob.getPipeline().getStages().size());
        jobHash.put(EXTERNAL_ID, transientJob.getExternalId());
        jobHash.put(PRIORITY, transientJob.getPriority());
        jobHash.put(CALLBACK_URL, transientJob.getCallbackURL());
        jobHash.put(CALLBACK_METHOD, transientJob.getCallbackMethod());
        jobHash.put(OUTPUT_ENABLED, transientJob.isOutputEnabled());
        jobHash.put(CANCELLED, transientJob.isCancelled());

        // Finally, persist the data to Redis.
        redisTemplate
                .boundHashOps(key(BATCH_JOB, transientJob.getId())) // e.g., BATCH_JOB:5
                .putAll(jobHash);

        // If this is the first time the job has been persisted, add the job's ID to the
        // collection of batch jobs known to the system so that we can assume that the key BATCH_JOB:N
        // exists in Redis provided that N exists in this set.
        // Note that in prior releases of OpenMPF, "BATCH_JOB" was represented as "JOB"
        redisTemplate
                .boundSetOps(BATCH_JOB) // e.g., BATCH_JOB
                .add(Long.toString(transientJob.getId()));
    }

    /**
     * Persist the media data for a batch job by storing it in the REDIS database
     * @param job The OpenMPF-assigned ID of the batch job, must be unique
     * @param transientMedia The non-null media instance to persist.
     * @throws WfmProcessingException
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void persistMedia(long job, TransientMedia transientMedia) throws WfmProcessingException {
        if ( transientMedia == null ) {
            throw new NullPointerException("transientMedia");
        }

        // Create or overwrite the value at the key for this medium with the new serialized version of this medium.
        redisTemplate
                .boundValueOps(key(BATCH_JOB, job, MEDIA, transientMedia.getId())) // e.g., BATCH_JOB:5:MEDIA:16
                .set(jsonUtils.serialize(transientMedia));
    }

    /**
     * Persist the stream data for a streaming job by storing it in the REDIS database
     * @param job The OpenMPF-assigned ID of the streaming job, must be unique
     * @param transientStream The non-null stream instance to persist.
     * @throws WfmProcessingException
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void persistStream(long job, TransientStream transientStream) throws WfmProcessingException {
        if ( transientStream == null ) {
            throw new NullPointerException("transientStream");
        }

        // Create or overwrite the value at the key for this medium with the new serialized version of this medium.
        redisTemplate
                .boundValueOps(key(STREAMING_JOB, job, STREAM, transientStream.getId())) // e.g., STREAMING_JOB:5:STREAM:16
                .set(jsonUtils.serialize(transientStream));
    }

    /**
     * Persist a streaming job by storing it in the REDIS database.
     * REDIS should only contain non-terminal jobs.
     * @param transientStreamingJob The non-null instance to store.
     * @throws WfmProcessingException
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void persistJob(TransientStreamingJob transientStreamingJob) throws WfmProcessingException {
        // Redis cannot store complex objects, so it is necessary to store complex objects using
        // less complex data structures such as lists and hashes (maps). The jobHash variable
        // is used to store the simple properties of a streaming job in a map.
        Map<String, Object> jobHash = new HashMap<>();

        // The Redis-assigned ID for the stream element associated with this job.
        TransientStream transientStream = transientStreamingJob.getStream();
        redisTemplate
                .boundValueOps(key(STREAMING_JOB, transientStreamingJob.getId(), STREAM, transientStream.getId())) // e.g., stream key example: STREAMING_JOB:5:STREAM:16
                .set(jsonUtils.serialize(transientStream));

        // Associate the stream id with the job hash.
        jobHash.put(STREAM, transientStream.getId());

        // Copy the remaining properties from the java object to the job hash...
        // Note: need to convert from ByteArray (using JsonUtils.serialize) from REDIS to Java
        jobHash.put(PIPELINE, jsonUtils.serialize(transientStreamingJob.getPipeline())); // Serialized to conserve space.
        jobHash.put(OVERRIDDEN_JOB_PROPERTIES, jsonUtils.serialize(transientStreamingJob.getOverriddenJobProperties()));
        jobHash.put(OVERRIDDEN_ALGORITHM_PROPERTIES, jsonUtils.serialize(transientStreamingJob.getOverriddenAlgorithmProperties()));
        jobHash.put(TASK_COUNT, transientStreamingJob.getPipeline() == null ? 0 : transientStreamingJob.getPipeline().getStages().size());
        jobHash.put(EXTERNAL_ID, transientStreamingJob.getExternalId());
        jobHash.put(PRIORITY, transientStreamingJob.getPriority());
        jobHash.put(STALL_TIMEOUT, transientStreamingJob.getStallTimeout());
        jobHash.put(HEALTH_REPORT_CALLBACK_URI, transientStreamingJob.getHealthReportCallbackURI());
        jobHash.put(SUMMARY_REPORT_CALLBACK_URI, transientStreamingJob.getSummaryReportCallbackURI());
        jobHash.put(OUTPUT_ENABLED, transientStreamingJob.isOutputEnabled());
        jobHash.put(OUTPUT_OBJECT_PATH, transientStreamingJob.getOutputObjectDirectory());
        jobHash.put(CANCELLED, transientStreamingJob.isCancelled());

        // Finally, persist the streaming job data to Redis.
        redisTemplate
                .boundHashOps(key(STREAMING_JOB, transientStreamingJob.getId())) // e.g., STREAMING_JOB:5
                .putAll(jobHash);

        // If this is the first time the streaming job has been persisted, add the streaming job's ID to the
        // collection of streaming jobs known to the system so that we can assume that the key STREAMING_JOB:N
        // exists in Redis provided that N exists in this set.
        redisTemplate
                .boundSetOps(STREAMING_JOB) // e.g., STREAMING_JOB
                .add(Long.toString(transientStreamingJob.getId()));
    }

    /**
     * Get the map of unique health report callback URIs associated with the specified streaming jobs.
     * @param jobIds unique job ids of active streaming jobs that are available in REDIS. May be empty.
     * @return Map of healthReportCallbackUri (keys), with each key value mapping to the List of jobIds that specified that healthReportCallbackUri. May be
     * empty if the jobIds List is empty.
     * @exception WfmProcessingException is thrown if one of the streaming jobs listed in jobIds isn't in REDIS (i.e. it is not an active job).
     */
    @Override
    public Map<String,List<Long>> getHealthReportCallbackURIAsMap(List<Long> jobIds) throws WfmProcessingException {
        Map<String, List<Long>> healthReportCallbackJobIdListMap = new HashMap<>();
        for (long jobId : jobIds) {
            TransientStreamingJob transientJob = getStreamingJob(jobId);
            if (transientJob == null) {
                // Throw an exception if any job that may be in the long term database, but isn't in REDIS (i.e. it is not an active job), is passed to this method.
                throw new WfmProcessingException("Error: jobId " + " is not the id of an active Streaming job");
            }

            String healthReportCallbackURI = transientJob.getHealthReportCallbackURI();
            if (healthReportCallbackURI == null)  {
                continue;
            }

            if (healthReportCallbackJobIdListMap.containsKey(healthReportCallbackURI)) {
                // some other streaming job has already registered this health report callback URI, add this job to the list
                List<Long> jobList = healthReportCallbackJobIdListMap.get(healthReportCallbackURI);
                jobList.add(jobId);
            } else {
                // This is the first streaming job to register this health report callback URI
                List<Long> jobList = new ArrayList<>();
                jobList.add(jobId);
                healthReportCallbackJobIdListMap.put(healthReportCallbackURI, jobList);
            }
        }

        return healthReportCallbackJobIdListMap;
    }

    /**
     * Set the current task index of the specified batch job.  Note: stage tracking is not supported for streaming jobs
     * @param jobId The OpenMPF-assigned ID of the batch job, must be unique.
     * @param taskIndex The index of the task which should be used as the "current" task.
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void setCurrentTaskIndex(long jobId, int taskIndex) throws WfmProcessingException {
        if ( isJobTypeBatch(jobId) ) {
            redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).put(TASK, taskIndex);
        } else if ( isJobTypeStreaming(jobId) ) {
            // This method should not be called for a streaming job.
            throw new WfmProcessingException("Error: This method should not be called for streaming jobs. Rejected this call for streaming job " + jobId);
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and ignoring the request.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't set the current task index", jobId);
        }
    }

    /**
     * Store the activity frame id and timestamp from the last activity alert that was sent for the specified streaming job.
     * Note that activity alerts are not sent for batch jobs, so calling this method for a batch job would be an error.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @param activityFrameId  The activity frame id to be stored for this streaming job
     * @param activityTimestamp The activity timestamp for this streaming job.
     * @exception WfmProcessingException will be thrown if the specified job is not a streaming job, or the
     * activityTimestamp is null. DateTimeException will be thrown if the activityTimestamp could not be stored in REDIS
     * because it could not be formatted as a String.
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void setStreamingActivity(long jobId, long activityFrameId, LocalDateTime activityTimestamp) throws WfmProcessingException {
        if ( isJobTypeStreaming(jobId) ) {
            if ( activityTimestamp == null ) {
                String errorMsg = "Illegal: Can't set streaming activity timestamp to null for streaming job #" + jobId + ".";
                log.error(errorMsg);
                throw new WfmProcessingException(errorMsg);
            } else {
                Map map = new HashMap<String, Object>();
                map.put(ACTIVITY_FRAME_ID, activityFrameId);
                map.put(ACTIVITY_TIMESTAMP, activityTimestamp);
                redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).putAll(map);
            }
        } else {
            String errorMsg = "Error: Job #" + jobId + " is not a streaming job. Can't set the streaming activity frame id.";
            log.error(errorMsg);
            throw new WfmProcessingException(errorMsg);
        }
    }

    /**
     * Return the last activity frame id that was stored for the specified streaming job.
     * Note that only streaming jobs report activity, so calling this method for a batch job would be an error.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @return The last activity frame id that was stored for this streaming job.
     * Returned value may be null if no activity has been detected yet for this streaming job.
     * @exception WfmProcessingException will be thrown if the specified job is not a streaming job
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized String getActivityFrameIdAsString(long jobId) throws WfmProcessingException {
        if ( isJobTypeStreaming(jobId) ) {
            // confirmed that the specified job is a streaming job
            Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
            Long frameId = (Long) jobHash.get(ACTIVITY_FRAME_ID);
            return frameId != null ? Long.toString(frameId) : null;
        } else {
            String errorMsg = "Error: Job #" + jobId + " is not a streaming job, so we can't set the activity frame id.";
            log.error(errorMsg);
            throw new WfmProcessingException(errorMsg + " Only streaming jobs report activity.");
        }
    }

    /** This method is the same as {@link #getActivityFrameIdAsString(long)}, it's just adapted for use with Lists.
     * @param jobIds List of jobIds for streaming jobs.
     * @return List of activity frame ids for the specified job ids.
     * @throws WfmProcessingException
     */
    @Override
    public synchronized List<String> getActivityFrameIdsAsStrings(List<Long> jobIds) throws WfmProcessingException {
        return jobIds.stream().map(this::getActivityFrameIdAsString).collect(Collectors.toList());
    }

    /**
     * Return the last activity timestamp that was stored for the specified streaming job.
     * Note that only streaming jobs report activity, so calling this method for a batch job would be an error.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @return The last activity timestamp that was stored for this streaming job.
     * Returned value may be null if no activity has been detected for this job yet.
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized LocalDateTime getActivityTimestamp(long jobId) throws WfmProcessingException {
        if ( isJobTypeStreaming(jobId) ) {
            Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
            return (LocalDateTime) jobHash.get(ACTIVITY_TIMESTAMP);
        } else {
            String errorMsg = "Error: Job #" + jobId + " is not a streaming job, so we can't get the activity timestamp.";
            log.error(errorMsg);
            throw new WfmProcessingException(errorMsg + " Only streaming jobs report activity.");
        }
    }

    /** This method is the same as {@link #getActivityTimestamp(long)}, it's just adapted to return a String.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @return The last activity timestamp that was stored for this streaming job as a String.
     * @throws WfmProcessingException
     */
    @Override
    public synchronized String getActivityTimestampAsString(long jobId) throws WfmProcessingException {
        LocalDateTime timestamp = getActivityTimestamp(jobId);
        return  timestamp != null ? TimeUtils.getLocalDateTimeAsString(timestamp) : null;
    }

    /** This method is the same as {@link #getActivityTimestamp(long)}, it's just adapted for use with Lists.
     * @param jobIds List of jobIds for streaming jobs.
     * @return List of activity timestamps. The list may contain null if no activity has been detected for a job yet.
     * @throws WfmProcessingException
     */
    @Override
    public synchronized List<LocalDateTime> getActivityTimestamps(List<Long> jobIds) throws WfmProcessingException {
        return jobIds.stream().map(this::getActivityTimestamp).collect(Collectors.toList());
    }

    /** This method is the same as {@link #getActivityTimestamp(long)}, it's just adapted for use with Lists and to return String objects.
     * @param jobIds List of jobIds for streaming jobs.
     * @return List of activity timestamps as Strings. The list may contain null if no activity has been detected for a job yet.
     * @throws WfmProcessingException
     */
    @Override
    public synchronized List<String> getActivityTimestampsAsStrings(List<Long> jobIds) throws WfmProcessingException {
        return jobIds.stream().map(this::getActivityTimestampAsString).collect(Collectors.toList());
    }

    /**
     * Set the tracks for the specified batch job
     * Note: to be consistent with legacy unit test processing, this method assumes that the job is a batch job.  This method should not be called for streaming jobs
     * This method will check to see if the specified jobId has been stored in REDIS and is associated with a streaming job.  If this is the case,
     * it will just log the warning
     * @param jobId The OpenMPF-assigned ID of the batch job, must be unique
     * @param mediaId The OpenMPF-assigned media ID.
     * @param taskIndex The index of the task which created the tracks in the job's pipeline.
     * @param actionIndex The index of the action in the job's pipeline's task which generated the tracks.
     * @param tracks The collection of tracks to associate with the (job, media, task, action) 4-ple.
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void setTracks(long jobId, long mediaId, int taskIndex, int actionIndex, Collection<Track> tracks) throws WfmProcessingException {
        if ( isJobTypeStreaming(jobId) ) {
            // This method should not be called for a streaming job.
            throw new WfmProcessingException("Error: This method should not be called for streaming jobs. Rejected this call for streaming job " + jobId);
        } else if ( isJobTypeBatch(jobId) ) {
            // confirmed that this is a batch job
            try {
                String key = key(BATCH_JOB, jobId, MEDIA, mediaId, TRACK, taskIndex, actionIndex);
                redisTemplate.delete(key);
                for (Track track : tracks) {
                    redisTemplate.boundListOps(key).rightPush(jsonUtils.serialize(track));
                }
            } catch (Exception exception) {
                log.warn("Failed to serialize tracks.", exception);
            }
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and ignoring the request.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't set tracks", jobId);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    /** Note: only batch jobs have CallbackURL defined */
    public String getCallbackURL(long jobId) throws WfmProcessingException {
        if ( isJobTypeStreaming(jobId) ) {
            // This method should not be called for a streaming job.
            throw new WfmProcessingException("Error: This method should not be called for streaming jobs. Rejected this call for streaming job " + jobId);
        } else if ( isJobTypeBatch(jobId) ) {
            Map jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();
            return (String)jobHash.get(CALLBACK_URL);
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't get the callback URI", jobId);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    /** Note: only streaming jobs have SummaryReportCallbackURI defined */
    public String getSummaryReportCallbackURI(long jobId) throws WfmProcessingException {
        if( isJobTypeBatch(jobId) ) {
            // This method should not be called for a batch job.
            throw new WfmProcessingException("Error: This method should not be called for batch jobs. Rejected this call for batch job " + jobId);
        } else if( isJobTypeStreaming(jobId) ) {
            Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
            return (String)jobHash.get(SUMMARY_REPORT_CALLBACK_URI);
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't get the summary report callback URI", jobId);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    /** Note: only streaming jobs have HealthReportCallbackURI defined */
    public String getHealthReportCallbackURI(long jobId) throws WfmProcessingException {
        if( isJobTypeBatch(jobId) ) {
            // This method should not be called for a batch job.
            throw new WfmProcessingException("Error: This method should not be called for batch jobs. Rejected this call for batch job " + jobId);
        } else if( isJobTypeStreaming(jobId) ) {
            Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
            return (String)jobHash.get(HEALTH_REPORT_CALLBACK_URI);
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't get the health report callback URI", jobId);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    /** Note: only batch jobs have callbackMethod defined. Streaming jobs only use HTTP POST method. */
    public String getCallbackMethod(long jobId) throws WfmProcessingException {
        if( isJobTypeBatch(jobId) ) {
            Map jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();
            return (String) jobHash.get(CALLBACK_METHOD);
        } else if( isJobTypeStreaming(jobId) ) {
            // Streaming jobs only support the HTTP POST method. Streaming jobs do not store callbackMethod in REDIS.
            throw new WfmProcessingException("Error, streaming jobs only support the HTTP POST method. Streaming jobs do not store callbackMethod in REDIS");
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't get the callback method", jobId);
            return null;
        }
    }

    /**
     * Returns the external id assigned to a job with JobId.
     * @param jobId The OpenMPF-assigned ID of the job.
     * @return returns the external id specified for that job or null if an external id was not specified for the job.
     * @throws WfmProcessingException
     */
    @Override
    @SuppressWarnings("unchecked")
    public String getExternalId(long jobId) throws WfmProcessingException {
        if( isJobTypeBatch(jobId) ) {
            Map jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();
            return (String)(jobHash.get(EXTERNAL_ID));
        } else if( isJobTypeStreaming(jobId) ) {
            Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
            return (String) (jobHash.get(EXTERNAL_ID));
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't get the external id", jobId);
            return null;
        }
    }

    /** This method is the same as {@link #getExternalId(long)}, it's just adapted for use with Lists.
     * @param jobIds List of jobIds
     * @return List of external ids for the specified jobs. The List may contain nulls for jobs that did not specify an external id.
     * @throws WfmProcessingException
     */
    @Override
    public List<String> getExternalIds(List<Long> jobIds) throws WfmProcessingException {
        return jobIds.stream().map(this::getExternalId).collect(Collectors.toList());
    }

    /**Method will return true if the specified jobId is a batch job stored in the transient data store
     * @param jobId The OpenMPF-assigned ID of the job
     * @return true if the specified jobId is a batch job stored in the transient data store, false otherwise
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean isJobTypeBatch(final long jobId) {
        return(redisTemplate.boundSetOps(BATCH_JOB).members().contains(Long.toString(jobId)));
    }

    /**Method will return true if the specified jobId is a streaming job stored in the transient data store
     * @param jobId The OpenMPF-assigned ID of the job
     * @return true if the specified jobId is a streaming job stored in the transient data store, false otherwise
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean isJobTypeStreaming(final long jobId) {
        return(redisTemplate.boundSetOps(STREAMING_JOB).members().contains(Long.toString(jobId)));
    }

    /**
     * Check with REDIS to see which of the specified jobs are currently listed in REDIS as
     * streaming jobs,
     *
     * @param jobIds List of job ids to check against REDIS.
     * @param isActive If true, then streaming jobs which have terminal JobStatus will be
     * filtered out. Otherwise, all current streaming jobs will be returned.
     * @return subset of jobIds that are listed as streaming jobs in REDIS, optionally reduced by
     *  JobStatus. List may be empty if there are no streaming jobs in REDIS.
     */
    @Override
    public List<Long> getCurrentStreamingJobs(List<Long> jobIds, boolean isActive ) {

        // While we are receiving the list of all job ids known to OpenMPF, some of these jobs may not be currently active in REDIS.
        // Reduce the List of jobIds to only include jobIds that are in REDIS.
        // If the isActive flag is true, then additionally reduce to only those streaming jobs that do not have terminal jobStatus.
        return jobIds.stream().filter(this::isJobTypeStreaming)
            .filter(jobId -> {
                if ( isActive ) {
                    // include only those streaming jobs that do not have terminal jobStatus.
                    return !getStreamingJobStatus(jobId).isTerminal();
                } else {
                    return true; // include all streaming jobs.
                }
            }).collect(Collectors.toList());
    }

    /**
     * Set the doCleanup flag of the specified streaming job
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @param doCleanup If true, delete the streaming job files from disk as part of cancelling the streaming job.
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void setDoCleanup(long jobId, boolean doCleanup) {
        if ( isJobTypeBatch(jobId) ) {
            // This method should not be called for a batch job.
            throw new WfmProcessingException("Error: This method should not be called for batch jobs. Rejected this call for batch job " + jobId);
        } else if ( isJobTypeStreaming(jobId) ) {
            redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).put(DO_CLEANUP, doCleanup);
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and ignoring the request.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't set the doCleanup flag", jobId);
        }
    }

    /**
     * Get the doCleanup flag for the specified streaming job
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @return true if cleanup should be performed; false otherwise
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean getDoCleanup(long jobId)  {
        if ( isJobTypeBatch(jobId) ) {
            // This method should not be called for a batch job.
            throw new WfmProcessingException("Error: This method should not be called for batch jobs. Rejected this call for batch job " + jobId);
        } else if ( isJobTypeStreaming(jobId) ) {
            // confirmed that the specified job is a streaming job
            Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
            return jobHash.containsKey(DO_CLEANUP) ? (Boolean)jobHash.get(DO_CLEANUP) : false;
        } else {
            // The specified jobId is not known to the system. This shouldn't happen, but if it does handle it gracefully by logging a warning and returning null.
            log.warn("Job #{} was not found as a batch or a streaming job so we can't return the doCleanup flag (returning false).", jobId);
            return false;
        }
    }
}
