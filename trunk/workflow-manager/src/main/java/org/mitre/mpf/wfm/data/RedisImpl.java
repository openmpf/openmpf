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

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;


@Component
public class RedisImpl implements Redis {

    private static final Logger log = LoggerFactory.getLogger(RedisImpl.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JsonUtils jsonUtils;

    @PostConstruct
    private synchronized void init() {
        log.info("Flushing Redis.");
        redisTemplate.execute((RedisCallback<Void>) redisConnection -> {
            redisConnection.flushAll();
            return null;
        });
    }

    //
    // KEY CREATION
    //

    // The following constants are provided to avoid making typographical errors when formulating keys.
    // Note: BATCH_JOB represents a batch job, while STREAMING_JOB represents a streaming job.
    private static final String
            DETECTION_SYSTEM_PROPERTIES_SNAPSHOT = "DETECTION_SYSTEM_PROPERTIES_SNAPSHOT",
            CANCELLED = "CANCELLED",
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
            DO_CLEANUP = "DO_CLEANUP",
            WARNINGS = "WARNINGS";

    /**
     * Creates a "key" from one or more components. This is a convenience method for creating
     * keys like BATCH_JOB:1:MEDIA:15:DETECTION_ERRORS or STREAMING_JOB:1:STREAM:15
     * @param component The required, non-null and non-empty root of the key.
     * @param components The optional collection of additional components in the key.
     * @return A single string built from the concatenation of all of the specified parameters and joined by a delimiter.
     */
    private static String key(Object component, Object... components) {
        return Stream.concat(Stream.of(component), Stream.of(components))
                .map(Object::toString)
                .collect(joining(":"));
    }

    //
    // INTERFACE IMPLEMENTATION (See interface for documentation)
    //

    @Override
    public synchronized void addDetectionProcessingError(DetectionProcessingError detectionProcessingError) throws WfmProcessingException {
        if (isJobTypeBatch(detectionProcessingError.getJobId())) {
            String key = key(BATCH_JOB, detectionProcessingError.getJobId(),
                             MEDIA, detectionProcessingError.getMediaId(),
                             ERRORS, detectionProcessingError.getStageIndex(),
                             detectionProcessingError .getActionIndex());
            redisTemplate.boundListOps(key)
                    .rightPush(jsonUtils.serialize(detectionProcessingError));
            return;
        }
        throwIfStreamingJob(detectionProcessingError.getJobId());
        throw new WfmProcessingException(String.format(
                "Warning, jobId %s is not known to the system. Failed to add the detection processing error.",
                detectionProcessingError.getJobId()));
    }


    @Override
    public void addTrack(Track track) throws WfmProcessingException {
        if (isJobTypeBatch(track.getJobId())) {
            String key = key(BATCH_JOB, track.getJobId(),
                             MEDIA, track.getMediaId(),
                             TRACK, track.getStageIndex(),
                             track.getActionIndex());
            redisTemplate.boundListOps(key)
                    .rightPush(jsonUtils.serialize(track));
            return;
        }
        throwIfStreamingJob(track.getJobId());
        throw new WfmProcessingException(String.format(
                "Warning, jobId %s is not known to the system. Failed to add the track.", track.getJobId()));
    }


    @Override
    public synchronized boolean cancel(long jobId) {
        if (isJobTypeBatch(jobId)) {
            redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).put(CANCELLED, true);
            return true;
        }
        if (isJobTypeStreaming(jobId)) {
            redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).put(CANCELLED, true);
            return true;
        }
        log.warn("Job #{} was not found as a batch or a streaming job so it cannot be cancelled in REDIS.", jobId);
        return false;
    }


    @Override
    public synchronized void clearJob(long jobId) throws WfmProcessingException {
        log.info("Deleting job {} from Redis.", jobId);
        if (isJobTypeBatch(jobId)) {
            TransientJob transientJob = getJob(jobId);
            redisTemplate.boundSetOps(BATCH_JOB).remove(Long.toString(jobId));
            redisTemplate.delete(Arrays.asList(key(BATCH_JOB, jobId),
                                               jobWarningsKey(jobId),
                                               jobErrorsKey(jobId)));
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
        } else if (isJobTypeStreaming(jobId)) {
            // confirmed that this is a streaming job that was requested to be cleared
            TransientStreamingJob transientStreamingJob = getStreamingJob(jobId);
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


    @Override
    public synchronized boolean containsJob(long jobId) {
        return isJobTypeBatch(jobId) || isJobTypeStreaming(jobId);
    }


    /**
     * Get the task index for the specified batch job. Note that stage tracking is not supported for streaming jobs
     * @param jobId The OpenMPF-assigned ID of the batch job to look up, must be unique.
     * @return
     */
    @Override
    public int getCurrentTaskIndexForJob(long jobId)  {
        Integer taskIdx = getHashValue(key(BATCH_JOB, jobId), TASK);
        if (taskIdx != null) {
            return taskIdx;
        }
        // WFM does not keep track of the stage for streaming jobs.
        throwIfStreamingJob(jobId);
        throw new WfmProcessingException(
                "JobId " + jobId + " is not known to the system. Failed to get the current task index for this job.");
    }


    @Override
    public synchronized void setJobStatus(long jobId, BatchJobStatusType batchJobStatusType) throws WfmProcessingException {
        if (isJobTypeBatch(jobId)) {
            redisTemplate.boundHashOps(key(BATCH_JOB, jobId))
                    .put(BATCH_JOB_STATUS, batchJobStatusType);
            return;
        }
        throwIfStreamingJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't set the job status", jobId));
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
    public synchronized void setJobStatus(
            long jobId, StreamingJobStatusType streamingJobStatusType, String streamingJobStatusDetail)
                    throws WfmProcessingException {
        if (isJobTypeStreaming(jobId)) {
            BoundHashOperations<String, Object, Object> jobHash
                    = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId));
            jobHash.put(STREAMING_JOB_STATUS, streamingJobStatusType);
            if (streamingJobStatusDetail == null) {
                jobHash.delete(STREAMING_JOB_STATUS_DETAIL);
            }
            else {
                jobHash.put(STREAMING_JOB_STATUS_DETAIL, streamingJobStatusDetail);
            }
            return;
        }
        throwIfBatchJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't set the job status", jobId));
    }


    @Override
    public BatchJobStatusType getBatchJobStatus(long jobId) throws WfmProcessingException {
        BatchJobStatusType status = getHashValue(key(BATCH_JOB, jobId), BATCH_JOB_STATUS);
        if (status != null) {
            return status;
        }

        throwIfStreamingJob(jobId);
        throw new WfmProcessingException(String.format("Status for job #%s is missing from Redis.", jobId));
    }


    @Override
    public StreamingJobStatus getStreamingJobStatus(long jobId) throws WfmProcessingException {
        List<Object> hashValues = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId))
                .multiGet(Arrays.asList(STREAMING_JOB_STATUS, STREAMING_JOB_STATUS_DETAIL));

        StreamingJobStatusType jobStatusType = (StreamingJobStatusType) hashValues.get(0);
        if (jobStatusType != null) {
            String jobStatusDetail = (String) hashValues.get(1);
            // jobStatusDetail may be null, but StreamingJobStatus constructor handles it properly.
            return new StreamingJobStatus(jobStatusType, jobStatusDetail);
        }

        throwIfBatchJob(jobId);
        throw new WfmProcessingException(String.format("Status for job #%s is missing from Redis.", jobId));
    }



    /** This method is the same as {@link #getStreamingJobStatus(long)}, it's just adapted for use with Lists.
     * @param jobIds List of streaming jobIds
     * @return List of StreamingJobStatus for the specified jobs. The List may contain nulls for invalid jobIds.
     */
    @Override
    public List<StreamingJobStatus> getStreamingJobStatuses(List<Long> jobIds) throws WfmProcessingException {
        return jobIds.stream().map(this::getStreamingJobStatus).collect(toList());
    }


    @Override
    public synchronized SortedSet<DetectionProcessingError> getDetectionProcessingErrors(
            long jobId, long mediaId, int taskIndex, int actionIndex) {
        String key = key(BATCH_JOB, jobId, MEDIA, mediaId, ERRORS, taskIndex, actionIndex);
        SortedSet<DetectionProcessingError> errors = redisTemplate
                .boundListOps(key)
                .range(0, -1)
                .stream()
                .map(obj -> jsonUtils.deserialize((byte[]) obj, DetectionProcessingError.class))
                .collect(toCollection(TreeSet::new));
        if (!errors.isEmpty() || isJobTypeBatch(jobId)) {
            return errors;
        }

        throwIfStreamingJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't return the detection processing errors.",
                jobId));
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
        if (isJobTypeBatch(jobId)) {
            // The batch job is known to the system and should be retrievable.
            // Get the hash containing the job properties.
            Map<Object, Object> jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();

            TransientJob transientJob = new TransientJob(jobId,
                    (String) (jobHash.get(EXTERNAL_ID)),
                    jsonUtils.deserialize((byte[]) (jobHash.get(DETECTION_SYSTEM_PROPERTIES_SNAPSHOT)), TransientDetectionSystemProperties.class),
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
        }
        throwIfStreamingJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't return the batch transient job.",
                jobId));
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
            Map<Object, Object> jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId))
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

        }
        throwIfBatchJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't return the streaming transient job.",
                jobId));
    }

    @Override
    public synchronized long getNextSequenceValue() {
        // Increments the value in Redis and returns the new value.
        // If the key is not in Redis, Redis will initialize it to 0, then increment the value.
        return redisTemplate.boundValueOps(SEQUENCE).increment(1);
    }

    /**
     * Get the task count for the specified batch or streaming job
     * @param jobId The OpenMPF-assigned ID of the batch or the streaming job, must be unique.
     * @return
     * @throws WfmProcessingException
     */
    @Override
    public int getTaskCountForJob(long jobId) throws WfmProcessingException {
        Integer batchTaskCount = getHashValue(key(BATCH_JOB, jobId), TASK_COUNT);
        if (batchTaskCount != null) {
            return batchTaskCount;
        }

        Integer streamingTaskCount = getHashValue(key(STREAMING_JOB, jobId), TASK_COUNT);
        if (streamingTaskCount != null) {
            return streamingTaskCount;
        }

        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't return the task count.", jobId));
    }


    @Override
    public synchronized SortedSet<Track> getTracks(long jobId, long mediaId, int taskIndex, int actionIndex) throws WfmProcessingException {
        String key = key(BATCH_JOB, jobId, MEDIA, mediaId, TRACK, taskIndex, actionIndex);
        SortedSet<Track> tracks = redisTemplate
                .boundListOps(key)
                .range(0, -1)
                .stream()
                .map(o -> jsonUtils.deserialize((byte[]) o, Track.class))
                .collect(toCollection(TreeSet::new));
        if (!tracks.isEmpty() || isJobTypeBatch(jobId)) {
            return tracks;
        }

        throwIfStreamingJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't return the tracks.", jobId));
    }


    /**
     * Persist a batch job by storing it in the REDIS database.
     * REDIS should only contain non-terminal jobs.
     * @param transientJob The non-null instance to store.
     * @throws WfmProcessingException
     */
    @Override
    public synchronized void persistJob(TransientJob transientJob) throws WfmProcessingException {
        log.info("Storing batch job {} in Redis.", transientJob.getId());
        // Redis cannot store complex objects, so it is necessary to store complex objects using
        // less complex data structures such as lists and hashes (maps). The jobHash variable
        // is used to store the simple properties of a job in a map.
        Map<String, Object> jobHash = new HashMap<>();

        if ( transientJob.getDetectionSystemPropertiesSnapshot() == null ) {
            throw new WfmProcessingException("Error: transientDetectionSystemProperties found to be null for jobId " + transientJob.getId() + ".");
        }

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
        jobHash.put(DETECTION_SYSTEM_PROPERTIES_SNAPSHOT, jsonUtils.serialize(transientJob.getDetectionSystemPropertiesSnapshot())); // Serialized to conserve space.
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
    public synchronized void persistMedia(long job, TransientMedia transientMedia) throws WfmProcessingException {
        if ( transientMedia == null ) {
            throw new NullPointerException("transientMedia");
        }

        // Create or overwrite the value at the key for this medium with the new serialized version of this medium.
        redisTemplate
                .boundValueOps(key(BATCH_JOB, job, MEDIA, transientMedia.getId())) // e.g., BATCH_JOB:5:MEDIA:16
                .set(jsonUtils.serialize(transientMedia));
    }



    @Override
    public synchronized void persistJob(TransientStreamingJob transientStreamingJob) throws WfmProcessingException {
        log.info("Storing streaming job {} in Redis.", transientStreamingJob.getId());
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
    public Map<String, List<Long>> getHealthReportCallbackURIAsMap(List<Long> jobIds) throws WfmProcessingException {
        Map<String, List<Long>> healthReportCallbackJobIdListMap = new HashMap<>();
        for (long jobId : jobIds) {
            String healthReportCallbackURI = getHealthReportCallbackURI(jobId);
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
    public synchronized void setStreamingActivity(long jobId, long activityFrameId, Instant activityTimestamp) throws WfmProcessingException {
        if ( isJobTypeStreaming(jobId) ) {
            if ( activityTimestamp == null ) {
                String errorMsg = "Illegal: Can't set streaming activity timestamp to null for streaming job #" + jobId + ".";
                log.error(errorMsg);
                throw new WfmProcessingException(errorMsg);
            } else {
                Map<String, Object> map = new HashMap<>();
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
    public synchronized String getActivityFrameIdAsString(long jobId) throws WfmProcessingException {
        Long frameId = getHashValue(key(STREAMING_JOB, jobId), ACTIVITY_FRAME_ID);
        if (frameId != null) {
            return String.valueOf(frameId);
        }
        if (isJobTypeStreaming(jobId)) {
            // No activity yet.
            return null;
        }
        String errorMsg = "Error: Job #" + jobId + " is not a streaming job, so we can't set the activity frame id.";
        log.error(errorMsg);
        throw new WfmProcessingException(errorMsg + " Only streaming jobs report activity.");
    }


    /** This method is the same as {@link #getActivityFrameIdAsString(long)}, it's just adapted for use with Lists.
     * @param jobIds List of jobIds for streaming jobs.
     * @return List of activity frame ids for the specified job ids.
     * @throws WfmProcessingException
     */
    @Override
    public synchronized List<String> getActivityFrameIdsAsStrings(List<Long> jobIds) throws WfmProcessingException {
        return jobIds.stream().map(this::getActivityFrameIdAsString).collect(toList());
    }

    /**
     * Return the last activity timestamp that was stored for the specified streaming job.
     * Note that only streaming jobs report activity, so calling this method for a batch job would be an error.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @return The last activity timestamp that was stored for this streaming job.
     * Returned value may be null if no activity has been detected for this job yet.
     */
    @Override
    public synchronized Instant getActivityTimestamp(long jobId) throws WfmProcessingException {
        Instant lastActivity = getHashValue(key(STREAMING_JOB, jobId), ACTIVITY_TIMESTAMP);
        if (lastActivity == null || isJobTypeStreaming(jobId)) {
            return lastActivity;
        }
        String errorMsg = "Error: Job #" + jobId + " is not a streaming job, so we can't get the activity timestamp.";
        log.error(errorMsg);
        throw new WfmProcessingException(errorMsg + " Only streaming jobs report activity.");
    }


    /** This method is the same as {@link #getActivityTimestamp(long)}, it's just adapted for use with Lists.
     * @param jobIds List of jobIds for streaming jobs.
     * @return List of activity timestamps. The list may contain null if no activity has been detected for a job yet.
     * @throws WfmProcessingException
     */
    @Override
    public synchronized List<Instant> getActivityTimestamps(List<Long> jobIds) throws WfmProcessingException {
        return jobIds.stream().map(this::getActivityTimestamp).collect(toList());
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
    public synchronized void setTracks(long jobId, long mediaId, int taskIndex, int actionIndex,
                                       Collection<Track> tracks) throws WfmProcessingException {
        if (isJobTypeBatch(jobId)) {
            String key = key(BATCH_JOB, jobId, MEDIA, mediaId, TRACK, taskIndex, actionIndex);
            redisTemplate.delete(key);

            BoundListOperations<String, Object> redisTracks = redisTemplate.boundListOps(key);
            for (Track track : tracks) {
                redisTracks.rightPush(jsonUtils.serialize(track));
            }
            return;
        }

        throwIfStreamingJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't set tracks.", jobId));
    }


    @Override
    /** Note: only batch jobs have CallbackURL defined */
    public String getCallbackURL(long jobId) throws WfmProcessingException {
        String callbackUrl = getHashValue(key(BATCH_JOB, jobId), CALLBACK_URL);
        if (callbackUrl != null || isJobTypeBatch(jobId)) {
            return callbackUrl;
        }

        throwIfStreamingJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't get the callback URI", jobId));
    }


    @Override
    /** Note: only streaming jobs have SummaryReportCallbackURI defined */
    public String getSummaryReportCallbackURI(long jobId) throws WfmProcessingException {
        String callbackUri = getHashValue(key(STREAMING_JOB, jobId), SUMMARY_REPORT_CALLBACK_URI);
        if (callbackUri != null || isJobTypeStreaming(jobId)) {
            return callbackUri;
        }

        throwIfBatchJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't get the summary report callback URI",
                jobId));
    }


    @Override
    /** Note: only streaming jobs have HealthReportCallbackURI defined */
    public String getHealthReportCallbackURI(long jobId) throws WfmProcessingException {
        String uri = getHashValue(key(STREAMING_JOB, jobId), HEALTH_REPORT_CALLBACK_URI);
        if (uri != null || isJobTypeStreaming(jobId)) {
            return uri;
        }

        throwIfBatchJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't get the health report callback URI",
                jobId));
    }


    @Override
    /** Note: only batch jobs have callbackMethod defined. Streaming jobs only use HTTP POST method. */
    public String getCallbackMethod(long jobId) throws WfmProcessingException {
        String callbackMethod = getHashValue(key(BATCH_JOB, jobId), CALLBACK_METHOD);
        if (callbackMethod != null || isJobTypeBatch(jobId)) {
            return callbackMethod;
        }

        throwIfStreamingJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't get the callback method", jobId));
    }


    /**
     * Returns the external id assigned to a job with JobId.
     * @param jobId The OpenMPF-assigned ID of the job.
     * @return returns the external id specified for that job or null if an external id was not specified for the job.
     * @throws WfmProcessingException
     */
    @Override
    public String getExternalId(long jobId) throws WfmProcessingException {
        String batchExternalId = getHashValue(key(BATCH_JOB, jobId), EXTERNAL_ID);
        if (batchExternalId != null || isJobTypeBatch(jobId)) {
            return batchExternalId;
        }

        String streamingExternalId = getHashValue(key(STREAMING_JOB, jobId), EXTERNAL_ID);
        if (streamingExternalId != null || isJobTypeStreaming(jobId)) {
            return streamingExternalId;
        }

        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't get the external id", jobId));
    }


    /** This method is the same as {@link #getExternalId(long)}, it's just adapted for use with Lists.
     * @param jobIds List of jobIds
     * @return List of external ids for the specified jobs. The List may contain nulls for jobs that did not specify an external id.
     * @throws WfmProcessingException
     */
    @Override
    public List<String> getExternalIds(List<Long> jobIds) {
        return jobIds.stream().map(this::getExternalId).collect(toList());
    }


    @Override
    public boolean isJobTypeBatch(final long jobId) {
        return redisTemplate.boundSetOps(BATCH_JOB).isMember(Long.toString(jobId));
    }


    @Override
    public boolean isJobTypeStreaming(final long jobId) {
        return redisTemplate.boundSetOps(STREAMING_JOB).isMember(Long.toString(jobId));
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
        if (isActive) {
            return jobIds.stream()
                    .filter(this::isJobTypeStreaming)
                    .filter(id -> !getStreamingJobStatus(id).isTerminal())
                    .collect(toList());
        }
        else {
            return jobIds.stream()
                    .filter(this::isJobTypeStreaming)
                    .collect(toList());
        }
    }

    /**
     * Set the doCleanup flag of the specified streaming job
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @param doCleanup If true, delete the streaming job files from disk as part of cancelling the streaming job.
     */
    @Override
    public synchronized void setDoCleanup(long jobId, boolean doCleanup) {
        if (isJobTypeStreaming(jobId)) {
            redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).put(DO_CLEANUP, doCleanup);
            return;
        }
        throwIfBatchJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't set the doCleanup flag.", jobId));
    }


    @Override
    public boolean getDoCleanup(long jobId)  {
        Boolean doCleanup = getHashValue(key(STREAMING_JOB, jobId), DO_CLEANUP);
        if (doCleanup != null) {
            return doCleanup;
        }
        if (isJobTypeStreaming(jobId)) {
            return false;
        }

        throwIfBatchJob(jobId);
        throw new WfmProcessingException(String.format(
                "Job #%s was not found as a batch or a streaming job so we can't return the doCleanup flag.", jobId));
    }


    @Override
    public void addJobWarning(long jobId, String message) {
        redisTemplate.boundSetOps(jobWarningsKey(jobId))
                .add(message);
    }

    @Override
    public Set<String> getJobWarnings(long jobId) {
        return getStringSet(jobWarningsKey(jobId));
    }

    private static String jobWarningsKey(long jobId) {
        return key(BATCH_JOB, jobId, WARNINGS);
    }

    @Override
    public void addJobError(long jobId, String message) {
        redisTemplate.boundSetOps(jobErrorsKey(jobId))
                .add(message);
    }

    @Override
    public Set<String> getJobErrors(long jobId) {
        return getStringSet(jobErrorsKey(jobId));
    }

    private static String jobErrorsKey(long jobId) {
        return key(BATCH_JOB, jobId, ERRORS);
    }

    private Set<String> getStringSet(String key) {
        return redisTemplate.boundSetOps(key)
                .members()
                .stream()
                .map(Object::toString)
                .collect(toSet());

    }


    @Override
    public TransientDetectionSystemProperties getPropertiesSnapshot(long jobId) {
        byte[] snapshotBytes = getHashValue(key(BATCH_JOB, jobId), DETECTION_SYSTEM_PROPERTIES_SNAPSHOT);
        return jsonUtils.deserialize(snapshotBytes, TransientDetectionSystemProperties.class);
    }


    private <T> T getHashValue(String hashKey, String hashElement) {
        return (T) redisTemplate.boundHashOps(hashKey).get(hashElement);
    }

    private void throwIfStreamingJob(long jobId) {
        // Index 0 is getStackTrace(), 1 is throwIfStreamingJob(), 2 is caller of this method.
        String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        if (isJobTypeStreaming(jobId)) {
            throw new WfmProcessingException(String.format(
                    "Error: This RedisImpl.%s should not be called for streaming jobs. Rejected this call for streaming job %s",
                    methodName, jobId));
        }
    }

    private void throwIfBatchJob(long jobId) {
        // Index 0 is getStackTrace(), 1 is throwIfBatchJob(), 2 is caller of this method.
        String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        if (isJobTypeBatch(jobId)) {
            throw new WfmProcessingException(String.format(
                    "Error: This RedisImpl.%s should not be called for batch jobs. Rejected this call for batch job %s",
                    methodName, jobId));
        }
    }
}
