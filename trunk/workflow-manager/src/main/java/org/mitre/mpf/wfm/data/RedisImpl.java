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

import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.JobStatus;
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
import java.util.*;

@Component(RedisImpl.REF)
public class RedisImpl implements Redis {

    /** All LocalDateTime objects are stored internally in REDIS by converting the object to a String formatted using the REDIS_TIMESTAMP_PATTERN,
     * which is currently defined as {@value #REDIS_TIMESTAMP_PATTERN}.
     */
	public static final String REDIS_TIMESTAMP_PATTERN = "yyyy-MM-dd kk:mm:ss.S";
	private DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern(REDIS_TIMESTAMP_PATTERN);

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
			STATUS = "STATUS",
			JOB_STATUS = "JOB_STATUS",
			STREAMING_JOB = "STREAMING_JOB",
			STREAM = "STREAM",
			STALL_TIMEOUT = "STALL_TIMEOUT",
			HEALTH_REPORT_CALLBACK_URI = "HEALTH_REPORT_CALLBACK_URI",
			LAST_HEALTH_REPORT_ACTIVITY_FRAME_ID = "LAST_HEALTH_REPORT_ACTIVITY_FRAME_ID",
			LAST_HEALTH_REPORT_ACTIVITY_TIMESTAMP = "LAST_HEALTH_REPORT_ACTIVITY_TIMESTAMP",
			SUMMARY_REPORT_CALLBACK_URI = "SUMMARY_REPORT_CALLBACK_URI";

	/**
	 * Creates a "key" from one or more components. This is a convenience method for creating
	 * keys like BATCH_JOB:1:MEDIA:15:DETECTION_ERRORS or STREAMING_JOB:1:STREAM:15
	 * @param component The required, non-null and non-empty root of the key.
	 * @param components The optional collection of additional components in the key.
	 * @return A single string built from the concatenation of all of the specified parameters and joined by a delimiter.
	 */
	public String key(Object component, Object... components) {
        // Return a key of the format FOO, FOO:BAR, FOO:BAR:BUZZ, etc...
        return component + ( (components == null || components.length == 0) ? "" : ":" + StringUtils.join(components, ":") );
    }

	//
	// INTERFACE IMPLEMENTATION (See interface for documentation)
	//

	@SuppressWarnings("unchecked")
	public synchronized boolean addDetectionProcessingError(DetectionProcessingError detectionProcessingError) {
		if ( !isJobTypeBatch(detectionProcessingError.getJobId()) ) {
			log.error("Failed to persist the detection processing error {} in the transient data store, only supported for batch jobs",detectionProcessingError);
			return false;
		} else {
			if ( detectionProcessingError != null ) {
				try {
					redisTemplate
							.boundListOps(
									key(BATCH_JOB, detectionProcessingError.getJobId(),
											MEDIA, detectionProcessingError.getMediaId(),
											ERRORS, detectionProcessingError.getStageIndex(), detectionProcessingError.getActionIndex())) // e.g., BATCH_JOB:5:MEDIA:3:ERRORS:0:1
							.rightPush(jsonUtils.serialize(detectionProcessingError));
					return true;
				} catch ( Exception exception ) {
					log.error("Failed to persist the detection processing error {} in the transient data store due to an exception.", detectionProcessingError, exception);
					return false;
				}
			} else {
				// Receiving a null parameter may be a symptom of a much larger issue.
				return false;
			}
		}
	}


	@SuppressWarnings("unchecked")
	public boolean addTrack(Track track) {
		if( !isJobTypeBatch(track.getJobId()) ) {
			log.error("Failed to store the track instance '{}', only supported for batch jobs", track);
			return false;
		} else {
			try {
				redisTemplate
						.boundListOps(key(BATCH_JOB, track.getJobId(), MEDIA, track.getMediaId(), TRACK, track.getStageIndex(), track.getActionIndex())) // e.g., BATCH_JOB:5:MEDIA:10:0:0
						.rightPush(jsonUtils.serialize(track));
				return true;
			} catch ( Exception exception ) {
				log.error("Failed to serialize or store the track instance '{}' due to an exception.", track, exception);
				return false;
			}
		}
	}

	/**
	 * marked the batch or streaming job as CANCELLED in REDIS
	 * @param jobId The OpenMPF-assigned ID of the batch or streaming job (must be unique)
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public synchronized boolean cancel(long jobId) {
		if ( isJobTypeBatch(Long.valueOf(jobId)) ) {
			// confirmed that this is a batch job
			if ( redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).size() > 0 ) {
				redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).put(CANCELLED, true);
				return true;
			} else {
				log.warn("Batch Job #{} is not running and cannot be cancelled.", jobId);
				return false;
			}
		} else if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			// confirmed that this is a streaming job
			if ( redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).size() > 0 ) {
				redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).put(CANCELLED, true);
				return true;
			} else {
				log.warn("Streaming Job #{} is not running and cannot be cancelled.", jobId);
				return false;
			}
		} else {
			log.warn("Job #{} was not found as a batch or a streaming job so it cannot be cancelled.", jobId);
			return false;
		}

	}

	/** Removes everything in the Redis datastore. */
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
    @SuppressWarnings("unchecked")
	public synchronized void clearJob(long jobId) throws WfmProcessingException {
		if ( isJobTypeBatch(Long.valueOf(jobId)) ) {
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
		} else if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
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

			// TODO delete this commented block after streaming job cleanup is implemented
			// leave this commented block here for reference until streaming video processing is finalized
//			if (transientStream.getUriScheme().isRemote()) {
//				if (transientStream.getLocalPath() != null) {
//					try {
//						File file = new File(transientStream.getLocalPath());
//						if (file.exists()) {
//							if (!file.delete()) {
//								log.warn("Failed to delete the file '{}'. It has been leaked and must be removed manually.", file);
//							}
//						}
//					} catch (Exception exception) {
//						log.warn("Failed to delete the local file '{}' which was created retrieved from a remote location - it must be manually deleted.", transientMedia.getLocalPath(), exception);
//					}
//				}
//			}
			// end of leave this commented block here for reference until streaming video processing is finalized

		} else {
			log.warn("Job #{} was not found as a batch or a streaming job so it cannot be cancelled.", jobId);
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
		if ( isJobTypeBatch(Long.valueOf(jobId)) ) {
			// confirmed that the specified job is a batch job
			return redisTemplate.boundSetOps(BATCH_JOB).members().contains(Long.toString(jobId));
		} else if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			// confirmed that the specified job is a streaming job
			return redisTemplate.boundSetOps(STREAMING_JOB).members().contains(Long.toString(jobId));
		} else {
			log.warn("Job #{} was not found as a batch or a streaming job so we don't know if this job was persisted.", jobId);
			return false;
		}
	}

	/**
	 * Get the task index for the specified batch job. Note that stage tracking is not supported for streaming jobs
	 * @param jobId The OpenMPF-assigned ID of the batch job to look up, must be unique.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public int getCurrentTaskIndexForJob(long jobId)  {
		if ( isJobTypeBatch(Long.valueOf(jobId)) ) {
			// confirmed that the specified job is a batch job
			if ( !redisTemplate.boundSetOps(BATCH_JOB).members().contains(Long.toString(jobId)) ) {
				return 0;
			} else {
				Map jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();
				return (int)(jobHash.get(TASK));
			}
		} else if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			// confirmed that the specified job is a streaming job.  This is an error, WFM does not track stage of streaming jobs
			log.warn("Job #{} is streaming job, stage tracking is not supported for streaming jobs so we can't return the task number (returning -1).", jobId);
			return -1;
		} else {
			log.warn("Job #{} was not found as a batch or a streaming job so we can't return the task number (returning -1).", jobId);
			return -1;
		}
	}

	/**
	 * Get the job status for the specified job
	 * @param jobId The OpenMPF-assigned ID of the batch or streaming job, must be unique.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public JobStatus getJobStatus(long jobId)  {
		if ( isJobTypeBatch(Long.valueOf(jobId)) ) {
			// confirmed that the specified job is a batch job
			if( !redisTemplate.boundSetOps(BATCH_JOB).members().contains(Long.toString(jobId)) ) {
				return null;
			} else {
				Map jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();
				return (JobStatus)jobHash.get(JOB_STATUS);
			}
		} else if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			// confirmed that the specified job is a streaming job
			if ( !redisTemplate.boundSetOps(STREAMING_JOB).members().contains(Long.toString(jobId)) ) {
				return null;
			} else {
				Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
				return (JobStatus)jobHash.get(JOB_STATUS);
			}
		} else {
			log.warn("Job #{} was not found as a batch or a streaming job so we can't return the job status (returning null).", jobId);
			return null;
		}
	}

    /** This method is the same as {@link #getJobStatus(long)}, it's just adapted for use with Lists.
     * @param jobIds List of jobIds
     * @return List of JobStatus for the specified jobs. The List may contain nulls for invalid jobIds.
     */
    @SuppressWarnings("unchecked")
    public List<JobStatus> getJobStatuses(List<Long> jobIds) {
        return jobIds.stream().map(jobId->getJobStatus(jobId.longValue())).collect(Collectors.toList());
    }

    /** This method is similar to {@link #getJobStatuses(List<Long>)}, it just returns a List of Strings instead of a List of JobStatus.
     * @param jobIds List of jobIds
     * @return List of JobStatus as Strings for the specified jobs. The List may contain nulls for invalid jobIds.
     */
    @SuppressWarnings("unchecked")
    public List<String> getJobStatusesAsString(List<Long> jobIds) {
        return getJobStatuses(jobIds).stream().map(jobStatus->jobStatus.toString()).collect(Collectors.toList());
    }

    /**
	 * Get the detection errors for the batch job
	 * @param jobId The OpenMPF-assigned ID of the batch job.
	 * @param mediaId The OpenMPF-assigned media ID.
	 * @param taskIndex The index of the task in the job's pipeline which generated these errors.
	 * @param actionIndex The index of the action in the job's pipeline's task which generated these errors.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public synchronized SortedSet<DetectionProcessingError> getDetectionProcessingErrors(long jobId, long mediaId, int taskIndex, int actionIndex) {
		if ( isJobTypeBatch(Long.valueOf(jobId)) ) {
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
		} else {
			log.warn("Error, detection error processing is not supported for streaming job '{}', returning null.", jobId);
			return null;
		}
	}

	/**
	 * Get the transient representation of the batch job as specified by the batch jobs unique jobId.
	 * This method should not be called for streaming jobs.
	 * @param jobId The OpenMPF-assigned ID of the batch job, must be unique
	 * @param mediaIds Optionally, a list of mediaIds to load.  If values are provided for media ids, the returned
	 *                 TransientJob will contain only the TransientMedia objects which relate to those ids. If no
	 *                 values are provided, the TransientJob will include all associated TransientMedia.
	 * @return
	 * @throws WfmProcessingException
	 */
	@SuppressWarnings("unchecked")
	public synchronized TransientJob getJob(long jobId, Long... mediaIds) throws WfmProcessingException {
		if ( !isJobTypeBatch(Long.valueOf(jobId)) ) {
			// The batch job is not known to the system.
			return null;
		} else {
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
		}
	}


	/**
	 * Get the transient representation of the streaming job as specified by the streaming jobs unique jobId.
	 * This method should not be called for batch jobs.
	 * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
	 * @return
	 * @throws WfmProcessingException
	 */
	@SuppressWarnings("unchecked")
	public synchronized TransientStreamingJob getStreamingJob(long jobId) throws WfmProcessingException {
		if ( !isJobTypeStreaming(Long.valueOf(jobId)) ) {
			// The streaming job is not known to the system.
			return null;
		} else {
			// The streaming job is known to the system and should be retrievable.
			// Get the hash containing the job properties.
			Map<String, Object> jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();

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

			transientStreamingJob.getOverriddenJobProperties().putAll(jsonUtils.deserialize((byte[])jobHash.get(OVERRIDDEN_JOB_PROPERTIES), HashMap.class));
			transientStreamingJob.getOverriddenAlgorithmProperties().putAll(jsonUtils.deserialize((byte[])jobHash.get(OVERRIDDEN_ALGORITHM_PROPERTIES), HashMap.class));

			Long streamId = (Long)jobHash.get(STREAM);
			TransientStream transientStream = jsonUtils.deserialize((byte[]) (redisTemplate.boundValueOps(key(STREAMING_JOB, jobId, STREAM, streamId)).get()), TransientStream.class);

			transientStreamingJob.setStream(transientStream);
			return transientStreamingJob;
		}
	}

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
	@SuppressWarnings("unchecked")
	public int getTaskCountForJob(long jobId) throws WfmProcessingException {
		if ( isJobTypeBatch(Long.valueOf(jobId)) ) {
			// confirmed that this is a batch job
			Map jobHash = redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).entries();
			return (int) (jobHash.get(TASK_COUNT));
		} else if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			// confirmed that this is a streaming job
			Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
			return (int) (jobHash.get(TASK_COUNT));
		} else {
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
    @SuppressWarnings("unchecked")
	public synchronized SortedSet<Track> getTracks(long jobId, long mediaId, int taskIndex, int actionIndex) {
		if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			// this jobId is associated with a streaming job, this is an error - return a null
			return null;
		} else {
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
		}
	}

	/**
	 * Persist a batch job by storing it in the REDIS database
	 * @param transientJob The non-null instance to store.
	 * @throws WfmProcessingException
	 */
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
	 * Persist a streaming job by storing it in the REDIS database
	 * @param transientStreamingJob The non-null instance to store.
	 * @throws WfmProcessingException
	 */
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
    public Map<String,List<Long>> getHealthReportCallbackURIAsMap(List<Long> jobIds) throws WfmProcessingException{
        Map<String,List<Long>> healthReportCallbackJobIdListMap = new HashMap<>();
        for ( final Long jobId : jobIds ) {
            TransientStreamingJob transientJob = getStreamingJob(jobId);
            if ( transientJob == null ) {
                // Throw an exception if any job that may be in the long term database, but isn't in REDIS (i.e. it is not an active job), is passed to this method.
                throw new WfmProcessingException("Error: jobId " + " is not the id of an active Streaming job");
            } else {
                // Check the health report callback for this active, streaming job.
                String healthReportCallbackURI = transientJob.getHealthReportCallbackURI();
                if (healthReportCallbackJobIdListMap.containsKey(healthReportCallbackURI)) {
                    // some other streaming job has already registered this health report callback URI, add this job to the list
                    List<Long> jobList = healthReportCallbackJobIdListMap.get(healthReportCallbackURI);
                    jobList.add(Long.valueOf(jobId));
                } else {
                    // This is the first streaming job to register this health report callback URI
                    List<Long> jobList = new ArrayList<>();
                    jobList.add(Long.valueOf(jobId));
                    healthReportCallbackJobIdListMap.put(healthReportCallbackURI, jobList);
                }
            }
        }
        return healthReportCallbackJobIdListMap;
    }

    /**
	 * Set the current task index of the specified batch job.  Note: stage tracking is not supported for streaming jobs
	 * @param jobId The OpenMPF-assigned ID of the batch job, must be unique.
	 * @param taskIndex The index of the task which should be used as the "current" task.
	 */
    @SuppressWarnings("unchecked")
	public synchronized  void setCurrentTaskIndex(long jobId, int taskIndex) {
		if ( isJobTypeBatch(Long.valueOf(jobId)) ) {
			redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).put(TASK, taskIndex);
		} else if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			log.warn("Job #{} is a streaming job which doesn't support stage tracking, so we can't set the current task index", jobId);
		} else {
			log.warn("Job #{} was not found as a batch or a streaming job so we can't set the current task index", jobId);
		}
	}

	/**
	 * Set the job status of the specified batch or streaming job
	 * @param jobId The OpenMPF-assigned ID of the batch or streaming job, must be unique.
	 * @param jobStatus The new status of the specified job.
	 */
	@SuppressWarnings("unchecked")
	public synchronized  void setJobStatus(long jobId, JobStatus jobStatus) {
		if ( isJobTypeBatch(Long.valueOf(jobId)) ) {
			redisTemplate.boundHashOps(key(BATCH_JOB, jobId)).put(JOB_STATUS, jobStatus);
		} else if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).put(JOB_STATUS, jobStatus);
		} else {
			log.warn("Job #{} was not found as a batch or a streaming job so we can't set the job status", jobId);
		}
	}

    /**
     * Store the last new activity frame id from the last health report that was sent for the specified streaming job.
     * Note that health reports are not sent for batch jobs, so calling this method for a batch job would be an error.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @param lastNewActivityAlertFrameId  The last new activity frame id to be stored for this streaming job
     * @exception WfmProcessingException will be thrown if the specified job is not a streaming job, or if the passed
     * lastHealthReportTimestamp is null.
     */
    public synchronized void setHealthReportLastActivityFrameId(long jobId, String lastNewActivityAlertFrameId ) throws WfmProcessingException {
        if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
            if ( lastNewActivityAlertFrameId != null ) {
                redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).put(LAST_HEALTH_REPORT_ACTIVITY_FRAME_ID, lastNewActivityAlertFrameId );
            } else {
            	String errorMsg = "Illegal: can't set health report last activity frame id to null for streaming job #" + jobId + ".";
                log.error(errorMsg);
                throw new WfmProcessingException(errorMsg);
            }
        } else {
            String errorMsg = "Error: Job #" + jobId + " is not a streaming job, so we can't set the health report last activity frame id.";
            log.error(errorMsg);
            throw new WfmProcessingException(errorMsg + " Only streaming jobs send health reports.");
        }
    }

    /**
     * Return the last New Activity Alert frame id that was stored in the health report for the specified streaming job.
     * Note that health reports are not sent for batch jobs, so calling this method for a batch job would be an error.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @return The last new activity frame id that was stored for this streaming job.
     * Returned value may be null if a health report or a New Activity Alert for this streaming job has not yet been sent.
     * @exception WfmProcessingException will be thrown if the specified job is not a streaming job
     */
    public synchronized String getHealthReportLastActivityFrameId(long jobId) throws WfmProcessingException {
        if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
            // confirmed that the specified job is a streaming job
            Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
            String frameId = (String) jobHash.get(LAST_HEALTH_REPORT_ACTIVITY_FRAME_ID);
			return frameId;
        } else {
            String errorMsg = "Error: Job #" + jobId + " is not a streaming job, so we can't set the health report last activity frame id.";
            log.error(errorMsg);
            throw new WfmProcessingException(errorMsg + " Only streaming jobs send health reports.");        }
    }

    /** This method is the same as {@link #getHealthReportLastActivityFrameId(long)}, it's just adapted for use with Lists.
     * @param jobIds List of jobIds for streaming jobs
     * @return List of last new activity alert frame ids
     * @throws WfmProcessingException
     */
    public synchronized List<String> getHealthReportLastActivityFrameIds(List<Long> jobIds) throws WfmProcessingException {
        return jobIds.stream().map(jobId->getHealthReportLastActivityFrameId(jobId.longValue())).collect(Collectors.toList());
    }

    /**
     * Store the last New Activity Alert timestamp that was sent in a health report for the specified streaming job.
     * Note that health reports are not sent for batch jobs, so calling this method for a batch job would be an error.
     * Note that internally, health report timestamps are stored in REDIS by converting the object to a string
     * formatted using the REDIS_TIMESTAMP_PATTERN, which is currently defined as {@value #REDIS_TIMESTAMP_PATTERN}.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @param lastNewActivityAlertTimestamp The last health report New Activity Alert timestamp for this streaming job.
     * @exception WfmProcessingException will be thrown if the specified job is not a streaming job, or if the passed
     * lastNewActivityAlertTimestamp is null. DateTimeException will be thrown if the lastNewActivityAlertTimestamp could not be stored
     * in REDIS because it could not be formatted as a String.
     */
    public synchronized void setHealthReportLastActivityTimestamp(long jobId, LocalDateTime lastNewActivityAlertTimestamp) throws WfmProcessingException, DateTimeException {
        if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
            if ( lastNewActivityAlertTimestamp != null ) {
                // Internally, health report timestamps are being stored in REDIS using ISO-8601 format.
                String timestamp = timestampFormatter.format(lastNewActivityAlertTimestamp);
                redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).put(LAST_HEALTH_REPORT_ACTIVITY_TIMESTAMP, timestamp);
            } else {
                String errorMsg = "Illegal: can't set the health report last activity timestamp to null for streaming job #" + jobId + ".";
                log.error(errorMsg);
                throw new WfmProcessingException(errorMsg);
            }
        } else {
            String errorMsg = "Error: Job #" + jobId + " is not a streaming job, so we can't set the health report last activity timestamp.";
            log.error(errorMsg);
            throw new WfmProcessingException(errorMsg + " Only streaming jobs send health reports.");
        }
    }

    /**
     * Return the last New Activity Alert timestamp that was sent in a health report for the specified streaming job.
     * Note that health reports are not sent for batch jobs, so calling this method for a batch job would be an error.
     * Note that internally, health report timestamps are stored in REDIS by converting the object to a string
     * formatted using the REDIS_TIMESTAMP_PATTERN, which is currently defined as {@value #REDIS_TIMESTAMP_PATTERN}.
     * @param jobId The OpenMPF-assigned ID of the streaming job, must be unique.
     * @return The last health report New Activity Alert timestamp for this streaming job.
     * Returned value may be null if a health report or a New Activity Alert for this streaming job has not yet been sent.
     * @exception WfmProcessingException will be thrown if the specified job is not a streaming job.
     * DateTimeException will be thrown if the last New Activity Alert timestamp could not be pulled
     * from REDIS because it could not be parsed as a String.
     */
    public synchronized LocalDateTime getHealthReportLastActivityTimestamp(long jobId) throws WfmProcessingException, DateTimeException {
        if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
            // Confirmed that the specified job is a streaming job.
            Map jobHash = redisTemplate.boundHashOps(key(STREAMING_JOB, jobId)).entries();
            String timestamp = (String) jobHash.get(LAST_HEALTH_REPORT_ACTIVITY_TIMESTAMP);
            if ( timestamp != null ) {
                return (LocalDateTime) timestampFormatter.parse(timestamp);
            } else {
                // Return null to indicate that a health report for this streaming job has not yet been sent.
                return (LocalDateTime) null;
            }
        } else {
            String errorMsg = "Error: Job #" + jobId + " is not a streaming job, so we can't get the health report last activity timestamp.";
            log.error(errorMsg);
            throw new WfmProcessingException(errorMsg + " Only streaming jobs send health reports.");
        }
    }

    /** This method is the same as {@link #getHealthReportLastActivityTimestamp(long)}, it's just adapted for use with Lists.
     * @param jobIds List of jobIds for streaming jobs.
     * @return List of last new activity alert timestamps. The list may contain null if a health report or a New Activity Alert for a streaming job has not yet been sent.
     * @throws WfmProcessingException
     * @throws DateTimeException
     */
    public synchronized List<LocalDateTime> getHealthReportLastActivityTimestamps(List<Long> jobIds) throws WfmProcessingException, DateTimeException {
        return jobIds.stream().map(jobId->getHealthReportLastActivityTimestamp(jobId.longValue())).collect(Collectors.toList());
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
    @SuppressWarnings("unchecked")
	public synchronized void setTracks(long jobId, long mediaId, int taskIndex, int actionIndex, Collection<Track> tracks) {
		if ( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			log.warn("Job #{} streaming jobs are not supported in setTracks", jobId);
		} else {
			// confirmed that this is not a streaming job
			try {
				String key = key(BATCH_JOB, jobId, MEDIA, mediaId, TRACK, taskIndex, actionIndex);
				redisTemplate.delete(key);
				for (Track track : tracks) {
					redisTemplate.boundListOps(key).rightPush(jsonUtils.serialize(track));
				}
			} catch (Exception exception) {
				log.warn("Failed to serialize tracks.", exception);
			}
		}
	}

	@Override
	/** Note: only batch jobs have CallbackURL defined */
	public String getCallbackURL(long jobId) throws WfmProcessingException {
		if ( !isJobTypeBatch(Long.valueOf(jobId)) ) {
			return null;
		} else {
			Map jobHash = redisTemplate.boundHashOps(key("BATCH_JOB", jobId)).entries();
			return (String)jobHash.get(CALLBACK_URL);
		}
	}

	@Override
	/** Note: only streaming jobs have SummaryReportCallbackURI defined */
	public String getSummaryReportCallbackURI(long jobId) throws WfmProcessingException {
		if( !isJobTypeStreaming(Long.valueOf(jobId)) ) {
			return null;
		} else {
			Map jobHash = redisTemplate.boundHashOps(key("STREAMING_JOB", jobId)).entries();
			return (String)jobHash.get(SUMMARY_REPORT_CALLBACK_URI);
		}
	}

	@Override
	/** Note: only streaming jobs have HealthReportCallbackURI defined */
	public String getHealthReportCallbackURI(long jobId) throws WfmProcessingException {
		if( !isJobTypeStreaming(Long.valueOf(jobId)) ) {
			return null;
		} else {
			Map jobHash = redisTemplate.boundHashOps(key("STREAMING_JOB", jobId)).entries();
			return (String)jobHash.get(HEALTH_REPORT_CALLBACK_URI);
		}
	}

	@Override
	/** Note: only batch jobs have callbackMethod defined. Streaming jobs only use HTTP POST method. */
	public String getCallbackMethod(long jobId) throws WfmProcessingException {
		if( isJobTypeBatch(Long.valueOf(jobId)) ) {
			Map jobHash = redisTemplate.boundHashOps(key("BATCH_JOB", jobId)).entries();
			return (String) jobHash.get(CALLBACK_METHOD);
		} else if( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			// Streaming jobs only support the HTTP POST method. Streaming jobs do not store callbackMethod in REDIS.
            throw new WfmProcessingException("Error, streaming jobs only support the HTTP POST method. Streaming jobs do not store callbackMethod in REDIS");
		} else {
			log.warn("Job #{} was not found as a batch or a streaming job so we can't get the callback method", jobId);
			return null;
		}
	}

    private String getCallbackMethod(Long jobId) throws WfmProcessingException {
	    return getCallbackMethod(jobId.longValue());
    }

    /**
     * Returns the external id assigned to a job with JobId.
     * @param jobId The OpenMPF-assigned ID of the job.
     * @return returns the external id specified for that job or null if an external id was not specified for the job.
     * @throws WfmProcessingException
     */
    @Override
	public String getExternalId(long jobId) throws WfmProcessingException {
		if( isJobTypeBatch(Long.valueOf(jobId)) ) {
			Map jobHash = redisTemplate.boundHashOps(key("BATCH_JOB", jobId)).entries();
			return (String)(jobHash.get(EXTERNAL_ID));
		} else if( isJobTypeStreaming(Long.valueOf(jobId)) ) {
			Map jobHash = redisTemplate.boundHashOps(key("STREAMING_JOB", jobId)).entries();
			return (String) (jobHash.get(EXTERNAL_ID));
		} else {
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
        return jobIds.stream().map(jobId->getExternalId(jobId.longValue())).collect(Collectors.toList());
    }

    /**Method will return true if the specified jobId is a batch job stored in the transient data store
	 * @param jobId The OpenMPF-assigned ID of the job
	 * @return true if the specified jobId is a batch job stored in the transient data store, false otherwise
	 */
	public boolean isJobTypeBatch(final Long jobId) {
		return(redisTemplate.boundSetOps(BATCH_JOB).members().contains(jobId.toString()));
	}

	/**Method will return true if the specified jobId is a streaming job stored in the transient data store
	 * @param jobId The OpenMPF-assigned ID of the job
	 * @return true if the specified jobId is a streaming job stored in the transient data store, false otherwise
	 */
	public boolean isJobTypeStreaming(final Long jobId) {
		return(redisTemplate.boundSetOps(STREAMING_JOB).members().contains(jobId.toString()));
	}

    /**
     * Check with REDIS to see which of the specified jobs are currently listed in REDIS as
     * streaming jobs,
     *
     * @param jobIds List of job ids to check against REDIS.
     * @param isActive If true, then streaming jobs which have JobStatus of TERMINATED will be
     * filtered out. Otherwise, all current streaming jobs will be returned.
     * @return subset of jobIds that are listed as streaming jobs in REDIS, optionally reduced by
     * TERMINATED JobStatus. List may be empty if there are no streaming jobs in REDIS.
     */
    public List<Long> getCurrentStreamingJobs(List<Long> jobIds, boolean isActive ) {

        // While we are receiving the list of all job ids known to OpenMPF, some of these jobs may not be currently active in REDIS.
        // Reduce the List of jobIds to only include jobIds that are in REDIS.
        // If the isActive flag is true, then additionally reduce to only those streaming jobs that do not have jobStatus TERMINATED.
        List<Long> currentStreamingJobIds = null;
        if ( isActive ) {
            currentStreamingJobIds = jobIds.stream().filter(jobId -> isJobTypeStreaming(jobId))
                .filter(jobId -> getJobStatus(jobId) != JobStatus.TERMINATED)
                .collect(Collectors.toList());
        } else {
            currentStreamingJobIds = jobIds.stream().filter(jobId -> isJobTypeStreaming(jobId))
                .collect(Collectors.toList());
        }
        return currentStreamingJobIds;
    }

}
