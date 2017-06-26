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

package org.mitre.mpf.wfm.camel;

import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.mitre.mpf.interop.*;
import org.mitre.mpf.mvc.controller.AtmosphereController;
import org.mitre.mpf.mvc.model.JobStatusMessage;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.access.MarkupResultDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateStreamingJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateMarkupResultDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.event.JobCompleteNotification;
import org.mitre.mpf.wfm.event.JobProgress;
import org.mitre.mpf.wfm.event.NotificationConsumer;
import org.mitre.mpf.wfm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

@Component(StreamingJobCompleteProcessorImpl.REF)
public class StreamingJobCompleteProcessorImpl extends WfmProcessor implements StreamingJobCompleteProcessor {
	private static final Logger log = LoggerFactory.getLogger(StreamingJobCompleteProcessor.class);
	public static final String REF = "streamingJobCompleteProcessorImpl";

	private Set<NotificationConsumer<JobCompleteNotification>> consumers = new ConcurrentSkipListSet<>();

	@Autowired
	@Qualifier(HibernateStreamingJobRequestDaoImpl.REF)
	private HibernateDao<StreamingJobRequest> streamingJobRequestDao;

	@Autowired
	@Qualifier(HibernateMarkupResultDaoImpl.REF)
	private MarkupResultDao markupResultDao;

	@Autowired
	private IoUtils ioUtils;

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Autowired
	private JsonUtils jsonUtils;

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Autowired
	private JobProgress jobProgressStore;

	@Override
	public void wfmProcess(Exchange exchange) throws WfmProcessingException {
		Long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
		assert jobId != null : String.format("The header '%s' (value=%s) was not set or is not a Long.", MpfHeaders.JOB_ID, exchange.getIn().getHeader(MpfHeaders.JOB_ID));

		if(jobId == Long.MIN_VALUE) {
			// If we receive a very large negative Job ID, it means an exception was encountered during processing of a job,
			// and none of the provided error handling logic could fix it. Further processing should not be performed.
			log.warn("[Streaming Job {}:*:*] An error prevents a streaming job from completing successfully. Please review the logs for additional information.", jobId);
		} else {
			String statusString = exchange.getIn().getHeader(MpfHeaders.JOB_STATUS, String.class);
			Mutable<JobStatus> jobStatus = new MutableObject<>(JobStatus.parse(statusString, JobStatus.UNKNOWN));

			markJobStatus(jobId, jobStatus.getValue());

			try {
				markJobStatus(jobId, JobStatus.BUILDING_OUTPUT_OBJECT);

				// NOTE: jobStatus is mutable - it __may__ be modified in createOutputObject!
				createOutputObject(jobId, jobStatus);
			} catch (Exception exception) {
				log.warn("Failed to create the output object for Streaming Job {} due to an exception.", jobId, exception);
				jobStatus.setValue(JobStatus.ERROR);
			}

			markJobStatus(jobId, jobStatus.getValue());

			try {
				summaryReportCallback(jobId);
			} catch (Exception exception) {
				log.warn("Failed to make callback (if appropriate) for Streaming Job #{}.", jobId);
			}

			// Tear down the streaming job.
			try {
				destroy(jobId);
			} catch (Exception exception) {
				log.warn("Failed to clean up Streaming Job {} due to an exception. Data for this streaming job will remain in the transient data store, but the status of the streaming job has not been affected by this failure.", jobId, exception);
			}

			JobCompleteNotification jobCompleteNotification = new JobCompleteNotification(exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class));

			for (NotificationConsumer<JobCompleteNotification> consumer : consumers) {
				if (!jobCompleteNotification.isConsumed()) {
					try {
						consumer.onNotification(this, new JobCompleteNotification(exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class)));
					} catch (Exception exception) {
						log.warn("Completion consumer '{}' threw an exception.", consumer, exception);
					}
				}
			}

			AtmosphereController.broadcast(new JobStatusMessage(jobId, 100, jobStatus.getValue(), new Date()));
			jobProgressStore.setJobProgress(jobId, 100.0f);
			log.info("[Streaming Job {}:*:*] Streaming Job complete!", jobId);
		}
	}

	private void summaryReportCallback(long jobId) throws WfmProcessingException {
		final String jsonSummaryReportCallbackURL = redis.getSummaryReportCallbackURI(jobId);
		final String jsonCallbackMethod = redis.getCallbackMethod(jobId);
		if(jsonSummaryReportCallbackURL != null && jsonCallbackMethod != null && (jsonCallbackMethod.equals("POST") || jsonCallbackMethod.equals("GET"))) {
			log.info("Starting "+jsonCallbackMethod+" summary report callback to "+jsonSummaryReportCallbackURL);
			try {
				JsonCallbackBody jsonBody =new JsonCallbackBody(jobId, redis.getExternalId(jobId));
				new Thread(new CallbackThread(jsonSummaryReportCallbackURL, jsonCallbackMethod, jsonBody)).start();
			} catch (IOException ioe) {
				log.warn("Failed to issue {} callback to '{}' due to an I/O exception.", jsonCallbackMethod, jsonSummaryReportCallbackURL, ioe);
			}
		}
	}

	private void markJobStatus(long jobId, JobStatus jobStatus) {
		log.debug("Marking Streaming Job {} as '{}'.", jobId, jobStatus);

		StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);
		assert streamingJobRequest != null : String.format("A job request entity must exist with the ID %d", jobId);

		streamingJobRequest.setTimeCompleted(new Date());
		streamingJobRequest.setStatus(jobStatus);
		streamingJobRequestDao.persist(streamingJobRequest);
	}

	public void createOutputObject(long jobId, Mutable<JobStatus> jobStatus) throws WfmProcessingException {
		TransientStreamingJob transientStreamingJob = redis.getStreamingJob(jobId);
		StreamingJobRequest streamingJobRequest = streamingJobRequestDao.findById(jobId);

		if(transientStreamingJob.isCancelled()) {
			jobStatus.setValue(JobStatus.CANCELLED);
		}

		JsonOutputObject jsonOutputObject = new JsonOutputObject(streamingJobRequest.getId(),
				UUID.randomUUID().toString(),
				transientStreamingJob == null ? null : jsonUtils.convert(transientStreamingJob.getPipeline()),
				transientStreamingJob.getPriority(),
				propertiesUtil.getSiteId(),
				transientStreamingJob.getExternalId(),
				streamingJobRequest.getTimeReceived().toString(),
				streamingJobRequest.getTimeCompleted().toString(),
				jobStatus.getValue().toString());

		if (transientStreamingJob.getOverriddenJobProperties() != null) {
			jsonOutputObject.getJobProperties().putAll(transientStreamingJob.getOverriddenJobProperties());
		}

		if (transientStreamingJob.getOverriddenAlgorithmProperties() != null) {
			jsonOutputObject.getAlgorithmProperties().putAll(transientStreamingJob.getOverriddenAlgorithmProperties());
		}

		TransientStream transientStream = transientStreamingJob.getStream();

		log.debug("StreamingJobCompleteProcessorImpl.createOutputObject: setting of stream data NYI for Streaming Job {} ", jobId);

//
//		int mediaIndex = 0;
//		for(TransientMedia transientMedia : transientJob.getMedia()) {
//			StringBuilder stateKeyBuilder = new StringBuilder("+");
//
//			JsonMediaOutputObject mediaOutputObject = new JsonMediaOutputObject(transientMedia.getId(), transientMedia.getUri(), transientMedia.getType(),
//					transientMedia.getLength(), transientMedia.getSha256(), transientMedia.getMessage(),
//					transientMedia.isFailed() ? "ERROR" : "COMPLETE");
//
//			mediaOutputObject.getMediaMetadata().putAll(transientMedia.getMetadata());
//			mediaOutputObject.getMediaProperties().putAll(transientMedia.getMediaSpecificProperties());
//
//			MarkupResult markupResult = markupResultDao.findByJobIdAndMediaIndex(jobId, mediaIndex);
//			if(markupResult != null) {
//				mediaOutputObject.setMarkupResult(new JsonMarkupOutputObject(markupResult.getId(), markupResult.getMarkupUri(), markupResult.getMarkupStatus().name(), markupResult.getMessage()));
//			}
//
//			if(transientJob.getPipeline() != null) {
//				for (int stageIndex = 0; stageIndex < transientJob.getPipeline().getStages().size(); stageIndex++) {
//					TransientStage transientStage = transientJob.getPipeline().getStages().get(stageIndex);
//					for (int actionIndex = 0; actionIndex < transientStage.getActions().size(); actionIndex++) {
//						TransientAction transientAction = transientStage.getActions().get(actionIndex);
//						String stateKey = String.format("%s#%s", stateKeyBuilder.toString(), transientAction.getName());
//
//						for (DetectionProcessingError detectionProcessingError : redis.getDetectionProcessingErrors(jobId, transientMedia.getId(), stageIndex, actionIndex)) {
//							JsonDetectionProcessingError jsonDetectionProcessingError = new JsonDetectionProcessingError(detectionProcessingError.getStartOffset(), detectionProcessingError.getEndOffset(), detectionProcessingError.getError());
//							if (!mediaOutputObject.getDetectionProcessingErrors().containsKey(stateKey)) {
//								mediaOutputObject.getDetectionProcessingErrors().put(stateKey, new TreeSet<JsonDetectionProcessingError>());
//							}
//							mediaOutputObject.getDetectionProcessingErrors().get(stateKey).add(jsonDetectionProcessingError);
//							if (!StringUtils.equalsIgnoreCase(mediaOutputObject.getStatus(), "COMPLETE")) {
//								mediaOutputObject.setStatus("INCOMPLETE");
//								if (StringUtils.equalsIgnoreCase(jsonOutputObject.getStatus(), "COMPLETE")) {
//									jsonOutputObject.setStatus("INCOMPLETE");
//								}
//							}
//						}
//
//						Collection<Track> tracks = redis.getTracks(jobId, transientMedia.getId(), stageIndex, actionIndex);
//						if(tracks.size() == 0) {
//							// Always include detection actions in the output object, even if they do not generate any results.
//							if (!mediaOutputObject.getTypes().containsKey(JsonActionOutputObject.NO_TRACKS_TYPE)) {
//								mediaOutputObject.getTypes().put(JsonActionOutputObject.NO_TRACKS_TYPE, new TreeSet<>());
//							}
//
//							SortedSet<JsonActionOutputObject> trackSet = mediaOutputObject.getTypes().get(JsonActionOutputObject.NO_TRACKS_TYPE);
//							boolean stateFound = false;
//							for (JsonActionOutputObject action : trackSet) {
//								if (stateKey.equals(action.getSource())) {
//									stateFound = true;
//									break;
//								}
//							}
//							if (!stateFound) {
//								trackSet.add(new JsonActionOutputObject(stateKey));
//							}
//						} else {
//							for (Track track : tracks) {
//								JsonTrackOutputObject jsonTrackOutputObject = new JsonTrackOutputObject(
//										TextUtils.getTrackUuid(transientMedia.getSha256(), track.getExemplar().getMediaOffsetFrame(), track.getExemplar().getX(), track.getExemplar().getY(), track.getExemplar().getWidth(), track.getExemplar().getHeight(), track.getType()),
//										track.getStartOffsetFrameInclusive(), track.getEndOffsetFrameInclusive(),
//										track.getStartOffsetTimeInclusive(), track.getEndOffsetTimeInclusive(), track.getType(), stateKey);
//
//
//								jsonTrackOutputObject.setExemplar(new JsonDetectionOutputObject(track.getExemplar().getX(), track.getExemplar().getY(), track.getExemplar().getWidth(), track.getExemplar().getHeight(), track.getExemplar().getConfidence(), track.getExemplar().getDetectionProperties(), track.getExemplar().getMediaOffsetFrame(), track.getExemplar().getMediaOffsetTime(), track.getExemplar().getArtifactExtractionStatus().name(), track.getExemplar().getArtifactPath()));
//								for (Detection detection : track.getDetections()) {
//									jsonTrackOutputObject.getDetections().add(new JsonDetectionOutputObject(detection.getX(), detection.getY(), detection.getWidth(), detection.getHeight(), detection.getConfidence(), detection.getDetectionProperties(), detection.getMediaOffsetFrame(), detection.getMediaOffsetTime(), detection.getArtifactExtractionStatus().name(), detection.getArtifactPath()));
//								}
//
//								String type = jsonTrackOutputObject.getType();
//								if (!mediaOutputObject.getTypes().containsKey(type)) {
//									mediaOutputObject.getTypes().put(type, new TreeSet<JsonActionOutputObject>());
//								}
//
//								SortedSet<JsonActionOutputObject> actionSet = mediaOutputObject.getTypes().get(type);
//								boolean stateFound = false;
//								for (JsonActionOutputObject action : actionSet) {
//									if (stateKey.equals(action.getSource())) {
//										stateFound = true;
//										action.getTracks().add(jsonTrackOutputObject);
//										break;
//									}
//								}
//								if (!stateFound) {
//									JsonActionOutputObject action = new JsonActionOutputObject(stateKey);
//									actionSet.add(action);
//									action.getTracks().add(jsonTrackOutputObject);
//								}
//							}
//						}
//
//						if (actionIndex == transientStage.getActions().size() - 1) {
//							stateKeyBuilder.append("#").append(transientAction.getName());
//						}
//					}
//				}
//			}
//			jsonOutputObject.getMedia().add(mediaOutputObject);
//			mediaIndex++;
//		}

		try {
			File outputFile = propertiesUtil.createDetectionOutputObjectFile(jobId);
			jsonUtils.serialize(jsonOutputObject, outputFile);
			streamingJobRequest.setOutputObjectPath(outputFile.getAbsolutePath());
			streamingJobRequest.setOutputObjectVersion(propertiesUtil.getOutputObjectVersion());
			streamingJobRequestDao.persist(streamingJobRequest);
		} catch(IOException | WfmProcessingException wpe) {
			log.error("Failed to create the JSON detection output object for streaming job '{}' due to an exception.", jobId, wpe);
		}

		try {
			jmsUtils.destroyCancellationRoutes(jobId);
		} catch (Exception exception) {
			log.warn("Failed to destroy the cancellation routes associated with streaming job {}. If this job is resubmitted, it will likely not complete again!", jobId, exception);
		}

	}

	@Autowired
	private JmsUtils jmsUtils;

	private void destroy(long jobId) throws WfmProcessingException {
		TransientStreamingJob transientStreamingJob = redis.getStreamingJob(jobId);

		log.debug("StreamingJobCompleteProcessorImpl.destroy: destruction of stream data NYI for Streaming Job {} ", jobId);

//		for(TransientMedia transientMedia : transientStreamingJob.getMedia()) {
//			if(transientMedia.getUriScheme().isRemote() && transientMedia.getLocalPath() != null) {
//				try {
//					Files.deleteIfExists(Paths.get(transientMedia.getLocalPath()));
//				} catch(Exception exception) {
//					log.warn("[{}|*|*] Failed to delete locally cached file '{}' due to an exception. This file must be manually deleted.", transientJob.getId(), transientMedia.getLocalPath());
//				}
//			}
//		}
		redis.clearJob(jobId);
	}

	@Override
	public void subscribe(NotificationConsumer<JobCompleteNotification> consumer) {
		consumers.add(consumer);
	}

	@Override
	public void unsubscribe(NotificationConsumer<JobCompleteNotification> consumer) {
		consumers.remove(consumer);
	}

	/**
	 * Thread to handle the Callback to a URL given a HTTP method
	 */
	public class CallbackThread implements Runnable {
		private String callbackURL;
		private String callbackMethod;
		private HttpUriRequest req;

		public CallbackThread(String callbackURL,String callbackMethod,JsonCallbackBody body) throws UnsupportedEncodingException {
			this.callbackURL = callbackURL;
			this.callbackMethod = callbackMethod;

			if(callbackMethod.equals("GET")) {
				String jsonCallbackURL2 = callbackURL;
				if(jsonCallbackURL2.contains("?")){
					jsonCallbackURL2 +="&";
				}else{
					jsonCallbackURL2 +="?";
				}
				jsonCallbackURL2 +="jobid="+body.getJobId();
				if(body.getExternalId() != null){
					jsonCallbackURL2 += "&externalid="+body.getExternalId();
				}
				req = new HttpGet(jsonCallbackURL2);
			}else { // this is for a POST
				HttpPost post = new HttpPost(callbackURL);
				post.addHeader("Content-Type", "application/json");
				try {
					post.setEntity(new StringEntity(jsonUtils.serializeAsTextString(body)));
					req = post;
				} catch (WfmProcessingException e) {
					log.error("Cannont serialize CallbackBody");
				}
			}
		}

		@Override
		public void run() {
			final HttpClient httpClient = HttpClientBuilder.create().build();
			try {
				HttpResponse response = httpClient.execute(req);
				log.info("{} Callback issued to '{}' (Response={}).", callbackMethod, callbackURL,response);
			} catch (Exception exception) {
				log.warn("Failed to issue {} callback to '{}' due to an I/O exception.", callbackMethod, callbackURL, exception);
			}
		}
	}
}
