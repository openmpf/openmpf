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


package org.mitre.mpf.wfm.camel.routes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.protobuf.ProtobufDataFormat;
import org.mitre.mpf.interop.JsonSegmentSummaryReport;
import org.mitre.mpf.interop.JsonStreamingDetectionOutputObject;
import org.mitre.mpf.interop.JsonStreamingTrackOutputObject;
import org.mitre.mpf.wfm.WfmStartup;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.enums.StreamingEndpoints;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;


@Component
public class StreamingJobRoutesBuilder extends RouteBuilder {

    private final StreamingJobRequestBo _streamingJobRequestBo;

    // Used to determine of messages should be ignored if AMQ has not been purged yet
    private final WfmStartup _wfmStartup;

    @Autowired
    public StreamingJobRoutesBuilder(StreamingJobRequestBo streamingJobRequestBo, WfmStartup wfmStartup) {
        _streamingJobRequestBo = streamingJobRequestBo;
        _wfmStartup = wfmStartup;
    }

    @Override
    public void configure() {
        // TODO add JOB_STATUS_DETAIL to the streaming job status message
        from(StreamingEndpoints.WFM_STREAMING_JOB_STATUS.endpointName())
                .routeId("Streaming Job Status Route")
                .log(LoggingLevel.DEBUG, "Received job status message: ${headers}")
                .process(exchange -> {
                    if (_wfmStartup.isApplicationRefreshed()) {
                        Message msg = exchange.getIn();
                        _streamingJobRequestBo.handleJobStatusChange(
                                msg.getHeader("JOB_ID", long.class),
                                new StreamingJobStatus(msg.getHeader("JOB_STATUS", StreamingJobStatusType.class)),
                                msg.getHeader("STATUS_CHANGE_TIMESTAMP", long.class));
                    }
                 });


        from(StreamingEndpoints.WFM_STREAMING_JOB_ACTIVITY.endpointName())
                .routeId("Streaming Job Activity Route")
                .log(LoggingLevel.DEBUG, "Received activity alert message: ${headers}")
                .process(exchange -> {
                    if (_wfmStartup.isApplicationRefreshed()) {
                        Message msg = exchange.getIn();
                        _streamingJobRequestBo.handleNewActivityAlert(
                                msg.getHeader("JOB_ID", long.class),
                                msg.getHeader("FRAME_NUMBER", int.class),
                                msg.getHeader("ACTIVITY_DETECTION_TIMESTAMP", long.class));
                    }
                });


        from(StreamingEndpoints.WFM_STREAMING_JOB_SUMMARY_REPORTS.endpointName())
                .routeId("Streaming Job Summary Report Route")
                .log(LoggingLevel.DEBUG, "Received summary report message: ${headers}")
                .unmarshal(new ProtobufDataFormat(DetectionProtobuf.StreamingDetectionResponse.getDefaultInstance()))
                .process(exchange -> {
                    if (_wfmStartup.isApplicationRefreshed()) {
                        Message msg = exchange.getIn();
                        DetectionProtobuf.StreamingDetectionResponse protobuf
                                = msg.getBody(DetectionProtobuf.StreamingDetectionResponse.class);

                        JsonSegmentSummaryReport summaryReport
                                = convertProtobufResponse(msg.getHeader("JOB_ID", long.class), protobuf);

                        _streamingJobRequestBo.handleNewSummaryReport(summaryReport);
                    }
                });
    }



    private static JsonSegmentSummaryReport convertProtobufResponse(
            long jobId, DetectionProtobuf.StreamingDetectionResponse protobuf) {

        List<JsonStreamingTrackOutputObject> tracks =
                IntStream.range(0, protobuf.getVideoTracksList().size())
                .mapToObj(i -> StreamingJobRoutesBuilder.convertProtobufTrack(i,
                        protobuf.getDetectionType(), protobuf.getVideoTracksList().get(i)) )
                .collect(toList());

        return new JsonSegmentSummaryReport(
                Instant.now(),
                jobId,
                protobuf.getSegmentNumber(),
                protobuf.getSegmentStartFrame(),
                protobuf.getSegmentStopFrame(),
                protobuf.getDetectionType(),
                tracks,
                protobuf.getError());
    }


    private static JsonStreamingTrackOutputObject convertProtobufTrack(
            int id, String detectionType, DetectionProtobuf.StreamingVideoTrack protobuf) {

        SortedSet<JsonStreamingDetectionOutputObject> detections = protobuf.getDetectionsList().stream()
                .map(StreamingJobRoutesBuilder::convertDetection)
                .collect(toCollection(TreeSet::new));

        JsonStreamingDetectionOutputObject exemplar = detections.stream()
		        .max(Comparator.comparingDouble(JsonStreamingDetectionOutputObject::getConfidence))
		        .orElse(null);

        return new JsonStreamingTrackOutputObject(
                Integer.toString(id),
                protobuf.getStartFrame(),
                protobuf.getStopFrame(),
                Instant.ofEpochMilli(protobuf.getStartTime()),
                Instant.ofEpochMilli(protobuf.getStopTime()),
                detectionType,
                /* source, */ // TODO: Populate with component name ("componentName" in .ini file -> JobSettings -> BasicAmqMessageSender::SendSummaryReport)
                protobuf.getConfidence(),
                convertProperties(protobuf.getDetectionPropertiesList()),
                exemplar,
                detections);
    }


    private static JsonStreamingDetectionOutputObject convertDetection(
            DetectionProtobuf.StreamingVideoDetection protobuf) {
        return new JsonStreamingDetectionOutputObject(
                protobuf.getXLeftUpper(),
                protobuf.getYLeftUpper(),
                protobuf.getWidth(),
                protobuf.getHeight(),
                protobuf.getConfidence(),
                convertProperties(protobuf.getDetectionPropertiesList()),
                protobuf.getFrameNumber(),
                Instant.ofEpochMilli(protobuf.getTime()));
    }


    private static SortedMap<String, String> convertProperties(List<DetectionProtobuf.PropertyMap> properties) {
        return properties.stream().collect(toMap(
                DetectionProtobuf.PropertyMap::getKey,
                DetectionProtobuf.PropertyMap::getValue,
                (u,v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); },
                TreeMap::new));
    }
}
