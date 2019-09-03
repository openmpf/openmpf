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

package org.mitre.mpf.wfm.mock;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.Metrics;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(MockDetectionComponent.REF)
public class MockDetectionComponent implements Processor {
	public static final String REF = "mockDetectionComponent";
	private static final Logger log = LoggerFactory.getLogger(MockDetectionComponent.class);

	private boolean reportError(DetectionProtobuf.DetectionRequest detectionRequest) {
		for(AlgorithmPropertyProtocolBuffer.AlgorithmProperty algorithmProperty : detectionRequest.getAlgorithmPropertyList()) {
			if(MpfConstants.REPORT_ERROR.equalsIgnoreCase(algorithmProperty.getPropertyName())) {
				return Boolean.valueOf(algorithmProperty.getPropertyValue());
			}
		}
		return false;
	}

	@Override
	public void process(Exchange exchange) throws Exception {
		log.info("Processing mock detection request...");

		// Copy all of our headers from the incoming exchange to the outgoing exchange.
		exchange.getOut().getHeaders().put(MpfHeaders.JOB_ID, exchange.getIn().getHeader(MpfHeaders.JOB_ID));
		exchange.getOut().getHeaders().put(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));
		exchange.getOut().getHeaders().put(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
		exchange.getOut().getHeaders().put(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));

		DetectionProtobuf.DetectionRequest extractionRequest = exchange.getIn().getBody(DetectionProtobuf.DetectionRequest.class);

		DetectionProtobuf.DetectionResponse.Builder response = DetectionProtobuf.DetectionResponse.newBuilder()
				.setActionIndex(extractionRequest.getActionIndex())
				.setActionName(extractionRequest.getActionName())
				.setDataType(DetectionProtobuf.DetectionResponse.DataType.valueOf(extractionRequest.getDataType().name()))
				.setError(reportError(extractionRequest) ? DetectionProtobuf.DetectionError.DETECTION_TRACKING_FAILED : DetectionProtobuf.DetectionError.NO_DETECTION_ERROR)
				.setMediaId(extractionRequest.getMediaId())
				.setMetrics(
						Metrics.MetricsMessage.newBuilder()
								.setNodeId("MOCK DETECTION COMPONENT")
								.setProcessingTime(1000))
				.setRequestId(extractionRequest.getRequestId())
				.setTaskIndex(extractionRequest.getTaskIndex())
				.setTaskName(extractionRequest.getTaskName());

		if (extractionRequest.getDataType()== DetectionProtobuf.DetectionRequest.DataType.AUDIO) {
			response.setStartIndex(extractionRequest.getAudioRequest().getStartTime())
					.setStopIndex(extractionRequest.getAudioRequest().getStopTime());
		} else if (extractionRequest.getDataType()== DetectionProtobuf.DetectionRequest.DataType.IMAGE) {
			response.setStartIndex(0).setStopIndex(1);
		} else if (extractionRequest.getDataType()== DetectionProtobuf.DetectionRequest.DataType.VIDEO) {
			response.setStartIndex(extractionRequest.getVideoRequest().getStartFrame())
					.setStopIndex(extractionRequest.getVideoRequest().getStopFrame());
		}

		exchange.getOut().setBody(
				// Build the response...
				response.build()

				// ...then convert it to a byte array.
				.toByteArray());

		log.info("Sent mock response for mock request {}.", extractionRequest.getRequestId());
	}
}
