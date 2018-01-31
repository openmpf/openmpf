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
import org.apache.camel.dataformat.protobuf.ProtobufDataFormat;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.interop.JsonSegmentSummaryReport;
import org.mitre.mpf.interop.JsonTrackOutputObject;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.enums.ArtifactExtractionStatus;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.enums.StreamingEndpoints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;


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
				.log(LoggingLevel.DEBUG, "Received job status message: ${headers}")
				.process(exchange -> {
					Message msg = exchange.getIn();
					_streamingJobRequestBo.handleJobStatusChange(
							msg.getHeader("JOB_ID", long.class),
							msg.getHeader("JOB_STATUS", JobStatus.class));
							// msg.getHeader("STATUS_CHANGE_TIMESTAMP", long.class)); // TODO: Remove this property?
				 });


		from(StreamingEndpoints.WFM_STREAMING_JOB_ACTIVITY.endpointName())
				.routeId("Streaming Job Activity Route")
				.log(LoggingLevel.DEBUG, "Received activity alert message: ${headers}")
				.process(exchange -> {
					Message msg = exchange.getIn();
					_streamingJobRequestBo.handleNewActivityAlert(
							msg.getHeader("JOB_ID", long.class),
							msg.getHeader("FRAME_NUMBER", int.class),
							msg.getHeader("ACTIVITY_DETECTION_TIMESTAMP", long.class));
				});


		from(StreamingEndpoints.WFM_STREAMING_JOB_SUMMARY_REPORTS.endpointName())
				.routeId("Streaming Job Summary Report Route")
				.log(LoggingLevel.DEBUG, "Received summary report message: ${headers}")
				.unmarshal(new ProtobufDataFormat(DetectionProtobuf.StreamingDetectionResponse.getDefaultInstance()))
				.process(exchange -> {
					Message msg = exchange.getIn();
					DetectionProtobuf.StreamingDetectionResponse protobuf
							= msg.getBody(DetectionProtobuf.StreamingDetectionResponse.class);

					JsonSegmentSummaryReport summaryReport
							= convertProtobufResponse(msg.getHeader("JOB_ID", long.class), protobuf);

					_streamingJobRequestBo.handleNewSummaryReport(summaryReport);
				});
	}



	private static JsonSegmentSummaryReport convertProtobufResponse(
			long jobId, DetectionProtobuf.StreamingDetectionResponse protobuf) {

	    /*
		List<JsonTrackOutputObject> tracks = protobuf.getVideoTracksList().stream()
				.map(StreamingJobRoutesBuilder::convertProtobufTrack)
				.collect(toList());
		*/

        List<JsonTrackOutputObject> tracks =
                IntStream.range(0, protobuf.getVideoTracksList().size())
                .mapToObj(i -> StreamingJobRoutesBuilder.convertProtobufTrack(i,
                        protobuf.getDetectionType(), protobuf.getVideoTracksList().get(i)) )
                .collect(toList());

		return new JsonSegmentSummaryReport(
				jobId,
				protobuf.getSegmentNumber(),
				protobuf.getSegmentStartFrame(),
				protobuf.getSegmentStopFrame(),
				protobuf.getDetectionType(),
				tracks,
				protobuf.getError());
	}


	private static JsonTrackOutputObject convertProtobufTrack(int id, String detectionType, DetectionProtobuf.StreamingVideoTrack protobuf) {

		List<JsonDetectionOutputObject> detections = protobuf.getDetectionsList().stream()
				.map(StreamingJobRoutesBuilder::convertDetection)
				.collect(toList());

        JsonTrackOutputObject track = new JsonTrackOutputObject(
                Integer.toString(id),
                protobuf.getStartFrame(),
                protobuf.getStopFrame(),
                protobuf.getStartTime(),
                protobuf.getStopTime(),
                detectionType,
                null); // TODO: Populate with component name ("componentName" in .ini file -> JobSettings -> BasicAmqMessageSender::SendSummaryReport)

        track.getDetections().addAll(detections);

        // TODO: Find and set exemplar
        JsonDetectionOutputObject exemplar = detections.stream()
                .max((d1, d2) -> Float.compare(d1.getConfidence(), d2.getConfidence())).get();

        track.setExemplar(exemplar);

        return track;

		/*
		return new JsonTrackOutputObject(
				protobuf.getStartFrame(),
				protobuf.getStartTime(),
				protobuf.getStopFrame(),
				protobuf.getStopTime(),
				protobuf.getConfidence(),
				detections,
				convertProperties(protobuf.getDetectionPropertiesList()));
		*/
	}


	private static JsonDetectionOutputObject convertDetection(
			DetectionProtobuf.StreamingVideoDetection protobuf) {
		return new JsonDetectionOutputObject(
				protobuf.getXLeftUpper(),
				protobuf.getYLeftUpper(),
				protobuf.getWidth(),
				protobuf.getHeight(),
				protobuf.getConfidence(),
				convertProperties(protobuf.getDetectionPropertiesList()),
				protobuf.getFrameNumber(),
				protobuf.getTime(),
                ArtifactExtractionStatus.NOT_ATTEMPTED.toString(),
				null);
	}


	private static SortedMap<String, String> convertProperties(List<DetectionProtobuf.PropertyMap> properties) {
		return properties.stream().collect(toMap(
				DetectionProtobuf.PropertyMap::getKey,
				DetectionProtobuf.PropertyMap::getValue,
				(u,v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); },
				TreeMap::new));
	}
}
