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

package org.mitre.mpf.wfm.service;

import org.mitre.mpf.nms.MasterNode;
import org.mitre.mpf.nms.streaming.messages.StopStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StreamingJobLaunchMessage;
import org.mitre.mpf.rest.api.node.EnvironmentVariableModel;
import org.mitre.mpf.wfm.data.entities.transients.TransientAction;
import org.mitre.mpf.wfm.data.entities.transients.TransientStage;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJob;
import org.mitre.mpf.wfm.enums.StreamingEndpoints;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@Service
public class StreamingJobMessageSenderImpl implements StreamingJobMessageSender {

	private final PropertiesUtil _properties;

	private final MasterNode _masterNode;

	private final PipelineService _pipelineService;

	private final StreamingServiceManager _streamingServiceManager;

	@Autowired
	public StreamingJobMessageSenderImpl(
			PropertiesUtil properties,
			MasterNode masterNode,
			PipelineService pipelineService,
			StreamingServiceManager streamingServiceManager) {
		_properties = properties;
		_masterNode = masterNode;
		_pipelineService = pipelineService;
		_streamingServiceManager = streamingServiceManager;
	}


	@Override
	public void launchJob(TransientStreamingJob job) {
		StreamingJobLaunchMessage launchMessage = createStreamingJobLaunchMessage(job);
		_masterNode.startStreamingJob(launchMessage);
	}


	@Override
	public void stopJob(long jobId) {
		_masterNode.stopStreamingJob(new StopStreamingJobMessage(jobId));
	}



	private StreamingJobLaunchMessage createStreamingJobLaunchMessage(TransientStreamingJob job) {
		TransientAction action = getAction(job);
		StreamingServiceModel streamingService = _streamingServiceManager.getServices().stream()
				.filter(sm -> sm.getAlgorithmName().equals(action.getAlgorithm()))
				.findAny()
				.orElseThrow(() -> new IllegalStateException(String.format(
						"Could not start streaming job because there is no streaming service for the %s algorithm.",
						action.getAlgorithm())));

		Map<String, String> environmentVariables = streamingService.getEnvironmentVariables().stream()
				.collect(toMap(EnvironmentVariableModel::getName, EnvironmentVariableModel::getValue));

		Map<String, String> jobProperties = getCombinedJobProperties(job, action);

		return new StreamingJobLaunchMessage(
				job.getId(),
				job.getStream().getUri(),
				job.getStream().getSegmentSize(),
				job.getStallTimeout(),
				_properties.getStreamingJobStallAlertThreshold(),
				streamingService.getServiceName(),
				streamingService.getLibraryPath(),
				environmentVariables,
				jobProperties,
				job.getStream().getMetadata(),
				_properties.getActiveMqUri(),
				StreamingEndpoints.WFM_STREAMING_JOB_STATUS.queueName(),
				StreamingEndpoints.WFM_STREAMING_JOB_ACTIVITY.queueName(),
				StreamingEndpoints.WFM_STREAMING_JOB_SUMMARY_REPORTS.queueName());
	}


	private static TransientAction getAction(TransientStreamingJob job) {
		List<TransientStage> stages = job.getPipeline().getStages();
		if (stages.size() > 1) {
			throw new IllegalStateException(String.format(
					"Streaming job %s uses the %s pipeline which has multiple stages, but streaming pipelines only support one stage.",
					job.getId(), job.getPipeline().getName()));
		}

		TransientStage stage = stages.get(0);
		if (stage.getActions().size() > 1) {
			throw new IllegalStateException(String.format(
					"Streaming job %s uses the %s pipeline which contains a stage with multiple actions, but streaming pipelines only support a single action.",
					job.getId(), job.getPipeline().getName()));
		}
		return stage.getActions().get(0);
	}


	private Map<String, String> getCombinedJobProperties(TransientStreamingJob job, TransientAction action) {
		Map<String, String> modifiedMap = getAlgorithmProperties(action.getAlgorithm());
		modifiedMap.putAll(action.getProperties());
		modifiedMap.putAll(job.getOverriddenJobProperties());

		Map<String, String> overriddenAlgoProps = job.getOverriddenAlgorithmProperties().get(action.getAlgorithm());
		if (overriddenAlgoProps != null) {
			modifiedMap.putAll(overriddenAlgoProps);
		}

		modifiedMap.putAll(job.getStream().getMediaProperties());

		return modifiedMap;
	}


	private Map<String, String> getAlgorithmProperties(String algorithmName) {
		AlgorithmDefinition algorithm = _pipelineService.getAlgorithm(algorithmName);
		return algorithm.getProvidesCollection().getAlgorithmProperties().stream()
				.collect(toMap(PropertyDefinition::getName, PropertyDefinition::getDefaultValue,
				               (x, y) -> y, HashMap::new));
	}


	//TODO: For future use. Untested.
//	private StreamingJobLaunchMessage createStreamingJobLaunchMessage(TransientStreamingJob job) {
//		FrameReaderLaunchMessage frameReaderMessage = createFrameReaderMessage(job);
//		VideoWriterLaunchMessage videoWriterMessage = createVideoWriterMessage(job);
//		List<ComponentLaunchMessage> componentMessages = createComponentLaunchMessages(job);
//
//		return new StreamingJobLaunchMessage(
//				job.getId(), frameReaderMessage, videoWriterMessage , componentMessages);
//	}


//	private FrameReaderLaunchMessage createFrameReaderMessage(TransientStreamingJob job) {
//		long jobId = job.getId();
//
//		String segmentOutputQueue = getAllSegmentQueues(jobId, job.getPipeline().getStages().size());
//		String componentFrameQueue = getFramesQueueName(jobId, 0);
//		String videoWriterFrameQueue = getVideoWriterFrameInput(jobId);
//		String releaseFrameQueue = getReleaseFrameQueueName(jobId);
//
//		return new FrameReaderLaunchMessage(
//				jobId,
//				job.getStream().getUri(),
//				job.getStream().getSegmentSize(),
//				job.getStream().getFrameDataBufferSize(),
//				job.getStallTimeout(),
//				_properties.getActiveMqUri(),
//				segmentOutputQueue,
//				componentFrameQueue,
//				videoWriterFrameQueue,
//				releaseFrameQueue,
//				StreamingEndpoints.WFM_STREAMING_JOB_STALLED.queueName());
//	}


//	private static VideoWriterLaunchMessage createVideoWriterMessage(TransientStreamingJob job) {
//		long jobId = job.getId();
//		String frameInputQueue = getVideoWriterFrameInput(jobId);
//		String frameOutputQueue = StreamingEndpoints.DONE_WITH_FRAME.queueName();
//		String segmentOutputQueue = StreamingEndpoints.WFM_SUMMARY_REPORTS.queueName();
//
//		return new VideoWriterLaunchMessage(
//				jobId,
//				job.getOutputObjectDirectory(),
//				frameInputQueue,
//				frameOutputQueue,
//				segmentOutputQueue);
//	}



//	private List<ComponentLaunchMessage> createComponentLaunchMessages(TransientStreamingJob job) {
//		List<TransientStage> stages = job.getPipeline().getStages();
//		List<ComponentLaunchMessage> launchMessages = new ArrayList<>();
//		for (int i = 0; i < stages.size(); i++) {
//			ComponentLaunchMessage message = createComponentLaunchMessage(stages.get(i), i + 1, job);
//			launchMessages.add(message);
//		}
//		return launchMessages;
//	}


//	private ComponentLaunchMessage createComponentLaunchMessage(
//			TransientStage stage, int stageNumber, TransientStreamingJob job) {
//
//		long jobId = job.getId();
//		String segmentInputQueue = getSegmentQueueName(jobId, stageNumber - 1);
//		String frameInputQueue = getFramesQueueName(jobId, stageNumber - 1);
//
//		TransientAction action = stage.getActions().get(0);
//		if (stage.getActions().size() != 1) {
//			LOG.error("TransientStage {} contains multiple actions, but streaming pipelines only support "
//					          + "one action per stage. Only the first action: {} will be used.",
//			          stage.getName(), action.getName());
//		}
//
//		Map<String, String> jobProperties = getCombinedJobProperties(job, action);
//
//		StreamingServiceModel streamingService = _streamingServiceManager.getServices().stream()
//				.filter(sm -> sm.getAlgorithmName().equals(action.getAlgorithm()))
//				.findAny()
//				.orElseThrow(() -> new IllegalStateException(String.format(
//						"Could not start streaming job because there is no streaming service for the %s algorithm.",
//						action.getAlgorithm())));
//
//		String serviceName = streamingService.getServiceName();
//		String libraryPath = streamingService.getLibraryPath();
//		Map<String, String> environmentVariables = streamingService.getEnvironmentVariables().stream()
//				.collect(toMap(EnvironmentVariableModel::getName, EnvironmentVariableModel::getValue));
//
//
//		boolean isFinalStage = stageNumber == job.getPipeline().getStages().size();
//		if (isFinalStage) {
//			String frameOutputQueue = StreamingEndpoints.DONE_WITH_FRAME.queueName();
//			String newTrackAlertQueue =	StreamingEndpoints.WFM_NEW_TRACK_ALERTS.queueName();
//			String summaryReportQueue = StreamingEndpoints.WFM_SUMMARY_REPORTS.queueName();
//			return new LastStageComponentLaunchMessage(
//					jobId,
//					serviceName,
//					stageNumber,
//					libraryPath,
//					environmentVariables,
//					1,
//					jobProperties,
//					segmentInputQueue,
//					frameInputQueue,
//					frameOutputQueue,
//					newTrackAlertQueue,
//					summaryReportQueue);
//		}
//		else {
//			String frameOutputQueue =  getFramesQueueName(jobId, stageNumber);
//			return new ComponentLaunchMessage(
//					jobId,
//					serviceName,
//					stageNumber,
//					libraryPath,
//					environmentVariables,
//					1,
//					jobProperties,
//					segmentInputQueue,
//					frameInputQueue,
//					frameOutputQueue);
//		}
//	}


//	private static String getFramesQueueName(long job, int stage) {
//		return generateQueueName("Job_%s__Frames_Stage_%s", job, stage);
//	}
//
//
//	private static String getAllSegmentQueues(long jobId, int numStages) {
//		return IntStream.range(0, numStages)
//				.mapToObj(stage -> getSegmentQueueName(jobId, stage))
//				.collect(joining(","));
//	}
//
//
//	private static String getSegmentQueueName(long job, int stage) {
//		return generateQueueName("Job_%s__Segments_Stage_%s", job, stage);
//	}
//
//
//	private static String getVideoWriterFrameInput(long job) {
//		return generateQueueName("Job_%s__VideoWriter_Frame_Input", job);
//	}
//
//
//	private static String getReleaseFrameQueueName(long job) {
//		return generateQueueName("Job_%s__RELEASE_FRAME", job);
//	}
//
//	private static String generateQueueName(String format, Object... args) {
//		return "MPF." + String.format(format, args);
//	}
}
