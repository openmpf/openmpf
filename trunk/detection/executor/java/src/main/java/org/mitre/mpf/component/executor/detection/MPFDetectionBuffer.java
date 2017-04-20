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

package org.mitre.mpf.component.executor.detection;

import java.util.*;

import com.google.common.base.Joiner;
import org.apache.commons.io.FilenameUtils;
import org.mitre.mpf.component.api.detection.*;
import org.mitre.mpf.component.api.messaging.MPFMessageMetadata;
import org.mitre.mpf.component.api.messaging.detection.MPFDetectionAudioRequest;
import org.mitre.mpf.component.api.messaging.detection.MPFDetectionImageRequest;
import org.mitre.mpf.component.api.messaging.detection.MPFDetectionVideoRequest;
import org.mitre.mpf.wfm.buffers.AlgorithmPropertyProtocolBuffer.AlgorithmProperty;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionRequest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.InvalidProtocolBufferException;

public class MPFDetectionBuffer {
	
    private static final Logger LOG = LoggerFactory.getLogger(MPFDetectionBuffer.class);

    private DetectionRequest detectionRequest = null;

    public MPFDetectionBuffer(final byte[] requestContents) {
        try {
            detectionRequest = DetectionRequest.parseFrom(requestContents);
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse the request protocol buffer due to Exception ", e);
            e.printStackTrace();
        }
    }

    public MPFMessageMetadata getMessageMetadata(final byte[] requestContents) {

        MPFMessageMetadata inputs = null;

        String dataUri = detectionRequest.getDataUri();
        MPFDataType dataType = translateProtobufDataType(detectionRequest.getDataType());
        long requestId = detectionRequest.getRequestId();

        Map<String, String> algorithmProperties = new HashMap<String, String>();
        for (int i = 0; i < detectionRequest.getAlgorithmPropertyList().size(); i++) {
            AlgorithmProperty algProp = detectionRequest.getAlgorithmProperty(i);
            algorithmProperties.put(algProp.getPropertyName(), algProp.getPropertyValue());
        }

        Map<String, String> mediaProperties = new HashMap<String, String>();
        for (int i = 0; i < detectionRequest.getMediaMetadataList().size(); i++) {
            DetectionProtobuf.PropertyMap mediaMetaProp = detectionRequest.getMediaMetadata(i);
            mediaProperties.put(mediaMetaProp.getKey(), mediaMetaProp.getValue());
        }

        String jobName = "Job " + detectionRequest.getRequestId() + ":" + FilenameUtils.getName(dataUri);
        inputs = new MPFMessageMetadata(dataUri, dataType,
                detectionRequest.getMediaId(),
                detectionRequest.getStageName(), detectionRequest.getStageIndex(),
                detectionRequest.getActionName(), detectionRequest.getActionIndex(),
                algorithmProperties, mediaProperties, requestId, jobName);

        return inputs;
    }

    public MPFDetectionAudioRequest getAudioRequest() {
        return new MPFDetectionAudioRequest(
            detectionRequest.getAudioRequest().getStartTime(),
            detectionRequest.getAudioRequest().getStopTime());
    }

    public MPFDetectionVideoRequest getVideoRequest() {
        return new MPFDetectionVideoRequest(
                detectionRequest.getVideoRequest().getStartFrame(),
                detectionRequest.getVideoRequest().getStopFrame());
    }

    public MPFDetectionImageRequest getImageRequest() {
        return new MPFDetectionImageRequest();
    }


    private DetectionResponse.Builder packCommonFields(
            final MPFMessageMetadata msgMetadata,
            final MPFDetectionError msgError) {

        DetectionProtobuf.DetectionResponse.Builder detectionResponse = DetectionProtobuf.DetectionResponse.newBuilder();
        detectionResponse.setMediaId(msgMetadata.getMediaId());

        // TODO: share the same dataType enum between both the DetectionRequest and DetectionResponse protobuf
        detectionResponse.setDataType(translateMPFDetectionDataType(msgMetadata.getDataType()));

        detectionResponse.setStageName(msgMetadata.getStageName());
        detectionResponse.setStageIndex(msgMetadata.getStageIndex());
        detectionResponse.setActionName(msgMetadata.getActionName());
        detectionResponse.setActionIndex(msgMetadata.getActionIndex());
        detectionResponse.setError(translateMPFDetectionError(msgError));

        return detectionResponse;
    }

    public byte[] createAudioResponseMessage(final MPFMessageMetadata msgMetadata,
                                             final String detectionType,
                                             final List<MPFAudioTrack> tracks,
                                             final MPFDetectionError msgError) {
        byte[] responseContents = null;

        DetectionProtobuf.DetectionResponse.Builder detectionResponseBuilder = packCommonFields(msgMetadata, msgError);

        DetectionProtobuf.DetectionResponse.AudioResponse.Builder audioResponseBuilder = detectionResponseBuilder.addAudioResponsesBuilder();
        audioResponseBuilder.setDetectionType(detectionType);

        if (!tracks.isEmpty()) {
            LOG.debug("Number of audio tracks in detection response for request ID " +
                    msgMetadata.getRequestId() + " = " + tracks.size());

            for (int i = 0; i < tracks.size(); i++) {

                DetectionProtobuf.DetectionResponse.AudioResponse.AudioTrack audioTrack = audioResponseBuilder.addAudioTracksBuilder()
                        .setStartTime(tracks.get(i).getStartTime())
                        .setStopTime(tracks.get(i).getStopTime())
                        .setConfidence(tracks.get(i).getConfidence())
                        .addAllDetectionProperties(convertProperties(tracks.get(i).getDetectionProperties()))
                        .build();
            }
        }

        responseContents = detectionResponseBuilder
                .setRequestId(msgMetadata.getRequestId())
                .setDataType(translateMPFDetectionDataType(msgMetadata.getDataType()))
                .build()
                .toByteArray();

        return responseContents;
    }

    public byte[] createVideoResponseMessage(final MPFMessageMetadata msgMetadata,
                                             final String detectionType,
                                             final List<MPFVideoTrack> tracks,
                                             final MPFDetectionError msgError) {
        byte[] responseContents = null;

        DetectionProtobuf.DetectionResponse.Builder detectionResponseBuilder = packCommonFields(msgMetadata, msgError);

        DetectionProtobuf.DetectionResponse.VideoResponse.Builder videoResponseBuilder =
                detectionResponseBuilder.addVideoResponsesBuilder();
        videoResponseBuilder.setDetectionType(detectionType);

        if (!tracks.isEmpty()) {
            LOG.info("Number of video tracks in detection response for request ID " +
                    msgMetadata.getRequestId() + " = " + tracks.size());

            for (int i = 0; i < tracks.size(); i++) {
                Set<DetectionResponse.VideoResponse.VideoTrack.FrameLocationMap> frameLocationMapSet = new HashSet<>();
                for (Map.Entry<Integer,MPFImageLocation> entry : tracks.get(i).getFrameLocations().entrySet()) {
                    MPFImageLocation mpfImageLocation = entry.getValue();

                    DetectionResponse.VideoResponse.VideoTrack.FrameLocationMap frameLocationMap = DetectionResponse.VideoResponse.VideoTrack.FrameLocationMap.newBuilder()
                            .setFrame(entry.getKey())
                            .setImageLocation(DetectionResponse.ImageLocation.newBuilder()
                                    .setHeight(mpfImageLocation.getHeight())
                                    .setWidth(mpfImageLocation.getWidth())
                                    .setConfidence(mpfImageLocation.getConfidence())
                                    .setXLeftUpper(mpfImageLocation.getXLeftUpper())
                                    .setYLeftUpper(mpfImageLocation.getYLeftUpper())
                                    .addAllDetectionProperties(convertProperties(mpfImageLocation.getDetectionProperties()))).build();
                    frameLocationMapSet.add(frameLocationMap);

                }
                DetectionProtobuf.DetectionResponse.VideoResponse.VideoTrack videoTrack = videoResponseBuilder.addVideoTracksBuilder()
                        .setStartFrame(tracks.get(i).getStartFrame())
                        .setStopFrame(tracks.get(i).getStopFrame())
                        .setConfidence(tracks.get(i).getConfidence())
                        .addAllDetectionProperties(convertProperties(tracks.get(i).getDetectionProperties()))
                        .addAllFrameLocations(frameLocationMapSet)
                        .build();

                LOG.info("Detection properties: {}", Joiner.on(";").withKeyValueSeparator("=").join(tracks.get(i).getDetectionProperties()));
            }
        }

        responseContents = detectionResponseBuilder
                .setRequestId(msgMetadata.getRequestId())
                .setDataType(translateMPFDetectionDataType(msgMetadata.getDataType()))
                .build()
                .toByteArray();

        return responseContents;
    }

    public byte[] createImageResponseMessage(final MPFMessageMetadata msgMetadata,
                                             final String detectionType,
                                             final List<MPFImageLocation> locations,
                                             final MPFDetectionError msgError) {
        byte[] responseContents = null;

        DetectionProtobuf.DetectionResponse.Builder detectionResponseBuilder = packCommonFields(msgMetadata, msgError);

        DetectionProtobuf.DetectionResponse.ImageResponse.Builder imageResponseBuilder = detectionResponseBuilder.addImageResponsesBuilder();
        imageResponseBuilder.setDetectionType(detectionType);

        if (!locations.isEmpty()) {
            LOG.debug("Number of image locations in detection response for request ID " +
                    msgMetadata.getRequestId() + " = " + locations.size());

            for (int i = 0; i < locations.size(); i++) {
                imageResponseBuilder.addImageLocations(DetectionResponse.ImageLocation.newBuilder()
                        .setHeight(locations.get(i).getHeight())
                        .setWidth(locations.get(i).getWidth())
                        .setConfidence(locations.get(i).getConfidence())
                        .setXLeftUpper(locations.get(i).getXLeftUpper())
                        .setYLeftUpper(locations.get(i).getYLeftUpper())
                        .addAllDetectionProperties(convertProperties(locations.get(i).getDetectionProperties())));

            }

        }

        responseContents = detectionResponseBuilder
                .setRequestId(msgMetadata.getRequestId())
                .setDataType(translateMPFDetectionDataType(msgMetadata.getDataType()))
                .build()
                .toByteArray();

        return responseContents;
    }

    public static MPFDataType translateProtobufDataType(final DetectionRequest.DataType dataType)  {

        switch (dataType) {
            case UNKNOWN:
                return MPFDataType.UNKNOWN;
            case VIDEO:
                return MPFDataType.VIDEO;
            case IMAGE:
                return MPFDataType.IMAGE;
            case AUDIO:
                return MPFDataType.AUDIO;
            default:
                return MPFDataType.UNKNOWN;
        }
    }

    public static DetectionResponse.DataType translateMPFDetectionDataType(final MPFDataType dataType) {

        switch (dataType) {
            case UNKNOWN:
                return DetectionResponse.DataType.UNKNOWN;
            case VIDEO:
                return DetectionResponse.DataType.VIDEO;
            case IMAGE:
                return DetectionResponse.DataType.IMAGE;
            case AUDIO:
                return DetectionResponse.DataType.AUDIO;
            default:
                return DetectionResponse.DataType.UNKNOWN;
        }
    }

    public static org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError translateMPFDetectionError(final MPFDetectionError err) {

        switch (err) {

            case MPF_DETECTION_SUCCESS:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.NO_DETECTION_ERROR;
            case MPF_OTHER_DETECTION_ERROR_TYPE:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.UNRECOGNIZED_DETECTION_ERROR;
            case MPF_DETECTION_NOT_INITIALIZED:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.DETECTION_NOT_INITIALIZED;
            case MPF_UNRECOGNIZED_DATA_TYPE:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.UNRECOGNIZED_DATA_TYPE;
            case MPF_UNSUPPORTED_DATA_TYPE:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.UNSUPPORTED_DATA_TYPE;
            case MPF_INVALID_DATAFILE_URI:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.INVALID_DATAFILE_URI;
            case MPF_COULD_NOT_OPEN_DATAFILE:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.COULD_NOT_OPEN_DATAFILE;
            case MPF_COULD_NOT_READ_DATAFILE:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.COULD_NOT_READ_DATAFILE;
            case MPF_FILE_WRITE_ERROR:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.FILE_WRITE_ERROR;
            case MPF_IMAGE_READ_ERROR:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.IMAGE_READ_ERROR;
            case MPF_BAD_FRAME_SIZE:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.BAD_FRAME_SIZE;
            case MPF_BOUNDING_BOX_SIZE_ERROR:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.BOUNDING_BOX_SIZE_ERROR;
            case MPF_INVALID_FRAME_INTERVAL:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.INVALID_FRAME_INTERVAL;
            case MPF_INVALID_START_FRAME:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.INVALID_START_FRAME;
            case MPF_INVALID_STOP_FRAME:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.INVALID_STOP_FRAME;
            case MPF_DETECTION_FAILED:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.DETECTION_FAILED;
            case MPF_DETECTION_TRACKING_FAILED:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.DETECTION_TRACKING_FAILED;
            case MPF_INVALID_PROPERTY:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.INVALID_PROPERTY;
            case MPF_MISSING_PROPERTY:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.MISSING_PROPERTY;
            case MPF_PROPERTY_IS_NOT_INT:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.PROPERTY_IS_NOT_INT;
            case MPF_PROPERTY_IS_NOT_FLOAT:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.PROPERTY_IS_NOT_FLOAT;
            case MPF_INVALID_ROTATION:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.INVALID_ROTATION;
            case MPF_MEMORY_ALLOCATION_FAILED:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.MEMORY_ALLOCATION_FAILED;
            default:
                return org.mitre.mpf.wfm.buffers.DetectionProtobuf.DetectionError.UNRECOGNIZED_DETECTION_ERROR;
        }
    }

    private List<DetectionProtobuf.PropertyMap> convertProperties(Map<String,String> propMap) {

        LinkedList<DetectionProtobuf.PropertyMap> output = new LinkedList<>();
        if (propMap!=null && !propMap.isEmpty()) {
            for (Map.Entry<String, String> entry : propMap.entrySet()) {
                output.add(DetectionProtobuf.PropertyMap.newBuilder()
                        .setKey(entry.getKey())
                        .setValue(entry.getValue()).build());
            }
        }
        return output;
    }
}
