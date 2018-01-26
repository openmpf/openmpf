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


package org.mitre.mpf.wfm.camel.routes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.enums.StreamingEndpoints;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class StreamingJobRoutesBuilder extends RouteBuilder {

	private final StreamingJobRequestBo _streamingJobRequestBo;

	@Autowired
	public StreamingJobRoutesBuilder(StreamingJobRequestBo streamingJobRequestBo) {
		_streamingJobRequestBo = streamingJobRequestBo;

	}

	@Override
	public void configure() {
		from(StreamingEndpoints.WFM_STREAMING_JOB_STATUS.endpointName())
				.routeId("Streaming Job Status Route")
				.log(LoggingLevel.INFO, "Received job status message: ${headers}")
				.process(exchange -> {
					Message msg = exchange.getIn();
					_streamingJobRequestBo.handleJobStatusChange(
							msg.getHeader("JOB_ID", long.class),
							msg.getHeader("JOB_STATUS", StreamingJobStatusType.class),
							msg.getHeader("STATUS_CHANGE_TIMESTAMP", long.class));
				 });


		from(StreamingEndpoints.WFM_STREAMING_JOB_ACTIVITY.endpointName())
				.routeId("Streaming Job Activity Route")
				.log(LoggingLevel.INFO, "Received activity alert message: ${headers}")
				.process(exchange -> {
					Message msg = exchange.getIn();
					_streamingJobRequestBo.handleNewActivityAlert(
							msg.getHeader("JOB_ID", long.class),
							msg.getHeader("FRAME_INDEX", long.class),
							msg.getHeader("ACTIVITY_DETECT_TIME", long.class));
				});


		from(StreamingEndpoints.WFM_STREAMING_JOB_SUMMARY_REPORTS.endpointName())
				.routeId("Streaming Job Summary Report Route")
				// TODO: unmarshal protobuf body
//				.unmarshal(new ProtobufDataFormat(DetectionProtobuf.StreamingDetectionResponse.getDefaultInstance()))
				.log(LoggingLevel.INFO, "Received summary report message: ${headers}")
				.process(exchange -> {
					Message msg = exchange.getIn();
					_streamingJobRequestBo.handleNewSummaryReport(
							msg.getHeader("JOB_ID", long.class),
							msg.getBody(Object.class)
					);
				});
	}
}
