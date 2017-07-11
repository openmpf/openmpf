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

#include "detection.pb.h"
#include "MPFDetectionBuffer.h"

using std::exception;
using std::vector;
using std::map;
using std::pair;
using std::string;

MPFDetectionBuffer::~MPFDetectionBuffer() { }

bool MPFDetectionBuffer::UnpackRequest(const unsigned char *const request_contents, const int request_body_length) {
    try {
        detection_request.ParseFromArray(static_cast<const void *> (request_contents), request_body_length);

        LOG4CXX_DEBUG(logger, "Detection request_id: " << detection_request.request_id() << " data_uri: " << detection_request.data_uri() <<
                      " data_type: " << detection_request.data_type());
    } catch (exception &e) {
        // When thrown, this will be caught and logged by the main program
        throw;
    } catch (...) {
        LOG4CXX_ERROR(logger, "Unknown Exception occurred");
        throw;
    }
    return true;
}

void MPFDetectionBuffer::GetMessageMetadata(MPFMessageMetadata* msg_metadata) {
    msg_metadata->request_id   = detection_request.request_id();
    msg_metadata->media_id     = detection_request.media_id();
    msg_metadata->stage_index  = detection_request.stage_index();
    msg_metadata->stage_name   = detection_request.stage_name();
    msg_metadata->action_index = detection_request.action_index();
    msg_metadata->action_name  = detection_request.action_name();
}

MPFDetectionDataType MPFDetectionBuffer::GetDataType() {
    return translateProtobufDataType(detection_request.data_type());
}

string MPFDetectionBuffer::GetDataUri() {
    return detection_request.data_uri();
}

void MPFDetectionBuffer::GetAlgorithmProperties(map<string, string> &algorithm_properties) {
    for (int i = 0; i < detection_request.algorithm_property_size(); i++) {
        algorithm_properties.insert(
                pair<string, string>(
                        detection_request.algorithm_property(i).property_name(),
                        detection_request.algorithm_property(i).property_value()));
    }
}


void MPFDetectionBuffer::GetMediaProperties(map<string, string> &media_properties) {
    for (const auto &prop : detection_request.media_metadata()) {
        media_properties[prop.key()] = prop.value();
    }
}

void MPFDetectionBuffer::GetVideoRequest(MPFDetectionVideoRequest &video_request) {
    video_request.start_frame = detection_request.video_request().start_frame();
    video_request.stop_frame = detection_request.video_request().stop_frame();
}

void MPFDetectionBuffer::GetAudioRequest(MPFDetectionAudioRequest &audio_request) {
    audio_request.start_time = detection_request.audio_request().start_time();
    audio_request.stop_time = detection_request.audio_request().stop_time();
}

void MPFDetectionBuffer::GetImageRequest(MPFDetectionImageRequest &image_request) {
    // TODO: set image-request-specific properties here
}

void MPFDetectionBuffer::PackCommonFields(
        const MPFMessageMetadata *const msg_metadata,
        const MPFDetectionDataType data_type,
        const MPFDetectionError error,
        DetectionResponse &detection_response) const {
    // Caller has to delete returned data

    detection_response.set_request_id(msg_metadata->request_id);
    detection_response.set_data_type(translateMPFDetectionDataType(data_type));
    detection_response.set_media_id(msg_metadata->media_id);
    detection_response.set_stage_index(msg_metadata->stage_index);
    detection_response.set_stage_name(msg_metadata->stage_name);
    detection_response.set_action_index(msg_metadata->action_index);
    detection_response.set_action_name(msg_metadata->action_name);
    detection_response.set_error(translateMPFDetectionError(error));
}

unsigned char *MPFDetectionBuffer::FinalizeDetectionResponse(
        const DetectionResponse &detection_response,
        int *packed_length) const {

    unsigned char *response_contents = NULL;

    try {
        response_contents = new unsigned char[detection_response.ByteSize()];
        detection_response.SerializeToArray(response_contents, detection_response.ByteSize());
        *packed_length = detection_response.ByteSize();
    } catch (exception &e) {
        // When thrown, this will be caught and logged by the main program
        if (response_contents) delete[] response_contents;
        throw;
    } catch (...) {
        if (response_contents) delete[] response_contents;
        LOG4CXX_ERROR(logger, "Unknown Exception occurred");
        throw;
    }

    return response_contents;
}

unsigned char *MPFDetectionBuffer::PackErrorResponse(
        const MPFMessageMetadata *const msg_metadata,
        const MPFDetectionDataType data_type,
        int *packed_length,
        const MPFDetectionError error) const {
    // Caller has to delete returned data

    DetectionResponse detection_response;
    PackCommonFields(msg_metadata, data_type, error, detection_response);
    return FinalizeDetectionResponse(detection_response, packed_length);
}

unsigned char *MPFDetectionBuffer::PackVideoResponse(
        const vector<MPFVideoTrack> &tracks,
        const MPFMessageMetadata *const msg_metadata,
        const MPFDetectionDataType data_type,
        const string detection_type,
        int *packed_length,
        const MPFDetectionError error) const {
    // Caller has to delete returned data

    DetectionResponse detection_response;
    PackCommonFields(msg_metadata, data_type, error, detection_response);

    DetectionResponse_VideoResponse *video_response = detection_response.add_video_responses();
    video_response->set_detection_type(detection_type);

    for (vector<MPFVideoTrack>::const_iterator tracks_iter = tracks.begin(); tracks_iter != tracks.end(); tracks_iter++) {
        MPFVideoTrack track = *tracks_iter;
        DetectionResponse_VideoResponse_VideoTrack *new_track = video_response->add_video_tracks();
        new_track->set_start_frame(track.start_frame);
        new_track->set_stop_frame(track.stop_frame);
        new_track->set_confidence(track.confidence);

        for (auto const &prop : track.detection_properties) {
            org::mitre::mpf::wfm::buffers::PropertyMap *detection_prop = new_track->add_detection_properties();
            detection_prop->set_key(prop.first);
            detection_prop->set_value(prop.second);
        }

        for (map<int, MPFImageLocation>::const_iterator locations_iter = track.frame_locations.begin(); locations_iter != track.frame_locations.end(); locations_iter++) {
            MPFImageLocation detection = locations_iter->second;

            DetectionResponse_VideoResponse_VideoTrack_FrameLocationMap *new_frame_location = new_track->add_frame_locations();

            new_frame_location->set_frame(locations_iter->first);

            DetectionResponse_ImageLocation *new_detection = new_frame_location->mutable_image_location();
            new_detection->set_x_left_upper(detection.x_left_upper);
            new_detection->set_y_left_upper(detection.y_left_upper);
            new_detection->set_width(detection.width);
            new_detection->set_height(detection.height);
            new_detection->set_confidence(detection.confidence);

            for (auto const &prop : detection.detection_properties) {
                org::mitre::mpf::wfm::buffers::PropertyMap *detection_prop = new_detection->add_detection_properties();
                detection_prop->set_key(prop.first);
                detection_prop->set_value(prop.second);
            }
        }
    }

    return FinalizeDetectionResponse(detection_response, packed_length);
}

unsigned char *MPFDetectionBuffer::PackAudioResponse(
        const vector<MPFAudioTrack> &tracks,
        const MPFMessageMetadata *const msg_metadata,
        const MPFDetectionDataType data_type,
        const string detection_type,
        int *packed_length,
        const MPFDetectionError error) const {
    // Caller has to delete returned data

    DetectionResponse detection_response;
    PackCommonFields(msg_metadata, data_type, error, detection_response);

    DetectionResponse_AudioResponse *audio_response = detection_response.add_audio_responses();
    audio_response->set_detection_type(detection_type);

    for (vector<MPFAudioTrack>::const_iterator tracks_iter = tracks.begin(); tracks_iter != tracks.end(); tracks_iter++) {
        MPFAudioTrack track = *tracks_iter;
        DetectionResponse_AudioResponse_AudioTrack *new_track = audio_response->add_audio_tracks();
        new_track->set_start_time(track.start_time);
        new_track->set_stop_time(track.stop_time);
        new_track->set_confidence(track.confidence);
        for (auto const &prop : track.detection_properties) {
            org::mitre::mpf::wfm::buffers::PropertyMap *detection_prop = new_track->add_detection_properties();
            detection_prop->set_key(prop.first);
            detection_prop->set_value(prop.second);
        }
    }

    return FinalizeDetectionResponse(detection_response, packed_length);
}

unsigned char *MPFDetectionBuffer::PackImageResponse(
        const vector<MPFImageLocation> &locations,
        const MPFMessageMetadata *const msg_metadata,
        const MPFDetectionDataType data_type,
        const string detection_type,
        int *packed_length,
        const MPFDetectionError error) const {
    // Caller has to delete returned data

    DetectionResponse detection_response;
    PackCommonFields(msg_metadata, data_type, error, detection_response);

    DetectionResponse_ImageResponse *image_response = detection_response.add_image_responses();
    image_response->set_detection_type(detection_type);

    for (vector<MPFImageLocation>::const_iterator locations_iter = locations.begin(); locations_iter != locations.end(); locations_iter++) {
        MPFImageLocation detection = *(locations_iter);
        DetectionResponse_ImageLocation *new_detection = image_response->add_image_locations();
        new_detection->set_x_left_upper(detection.x_left_upper);
        new_detection->set_y_left_upper(detection.y_left_upper);
        new_detection->set_width(detection.width);
        new_detection->set_height(detection.height);
        new_detection->set_confidence(detection.confidence);

        for (auto const &prop : detection.detection_properties) {
            org::mitre::mpf::wfm::buffers::PropertyMap *detection_prop = new_detection->add_detection_properties();
            detection_prop->set_key(prop.first);
            detection_prop->set_value(prop.second);
        }
    }

    return FinalizeDetectionResponse(detection_response, packed_length);
}

//-----------------------------------------------------------------------------
MPFDetectionDataType MPFDetectionBuffer::translateProtobufDataType(
        const DetectionRequest_DataType &dataType) const {

    switch (dataType) {
        case DetectionRequest_DataType::DetectionRequest_DataType_UNKNOWN:
            return MPFDetectionDataType::UNKNOWN;
        case DetectionRequest_DataType::DetectionRequest_DataType_VIDEO:
            return MPFDetectionDataType::VIDEO;
        case DetectionRequest_DataType::DetectionRequest_DataType_IMAGE:
            return MPFDetectionDataType::IMAGE;
        case DetectionRequest_DataType::DetectionRequest_DataType_AUDIO:
            return MPFDetectionDataType::AUDIO;
        default:
            return MPFDetectionDataType::UNKNOWN;
    }
}

DetectionResponse_DataType MPFDetectionBuffer::translateMPFDetectionDataType(
        const MPFDetectionDataType &dataType) const {

    switch (dataType) {
        case UNKNOWN:
            return DetectionResponse_DataType::DetectionResponse_DataType_UNKNOWN;
        case VIDEO:
            return DetectionResponse_DataType::DetectionResponse_DataType_VIDEO;
        case IMAGE:
            return DetectionResponse_DataType::DetectionResponse_DataType_IMAGE;
        case AUDIO:
            return DetectionResponse_DataType::DetectionResponse_DataType_AUDIO;
        default:
            return DetectionResponse_DataType::DetectionResponse_DataType_UNKNOWN;
    }
}

DetectionError MPFDetectionBuffer::translateMPFDetectionError(
        const MPFDetectionError &err) const {

    switch (err) {
        case MPF_DETECTION_SUCCESS:
            return DetectionError::NO_DETECTION_ERROR;
        case MPF_DETECTION_NOT_INITIALIZED:
            return DetectionError::DETECTION_NOT_INITIALIZED;
        case MPF_UNRECOGNIZED_DATA_TYPE:
            return DetectionError::UNRECOGNIZED_DATA_TYPE;
        case MPF_UNSUPPORTED_DATA_TYPE:
            return DetectionError::UNSUPPORTED_DATA_TYPE;
        case MPF_INVALID_DATAFILE_URI:
            return DetectionError::INVALID_DATAFILE_URI;
        case MPF_COULD_NOT_OPEN_DATAFILE:
            return DetectionError::COULD_NOT_OPEN_DATAFILE;
        case MPF_COULD_NOT_READ_DATAFILE:
            return DetectionError::COULD_NOT_READ_DATAFILE;
        case MPF_FILE_WRITE_ERROR:
            return DetectionError::FILE_WRITE_ERROR;
        case MPF_IMAGE_READ_ERROR:
            return DetectionError::IMAGE_READ_ERROR;
        case MPF_BAD_FRAME_SIZE:
            return DetectionError::BAD_FRAME_SIZE;
        case MPF_BOUNDING_BOX_SIZE_ERROR:
            return DetectionError::BOUNDING_BOX_SIZE_ERROR;
        case MPF_INVALID_FRAME_INTERVAL:
            return DetectionError::INVALID_FRAME_INTERVAL;
        case MPF_INVALID_START_FRAME:
            return DetectionError::INVALID_START_FRAME;
        case MPF_INVALID_STOP_FRAME:
            return DetectionError::INVALID_STOP_FRAME;
        case MPF_DETECTION_FAILED:
            return DetectionError::DETECTION_FAILED;
        case MPF_DETECTION_TRACKING_FAILED:
            return DetectionError::DETECTION_TRACKING_FAILED;
        case MPF_MISSING_PROPERTY:
            return DetectionError::MISSING_PROPERTY;
        case MPF_INVALID_PROPERTY:
            return DetectionError::INVALID_PROPERTY;
        case MPF_PROPERTY_IS_NOT_INT:
            return DetectionError::PROPERTY_IS_NOT_INT;
        case MPF_PROPERTY_IS_NOT_FLOAT:
            return DetectionError::PROPERTY_IS_NOT_FLOAT;
        case MPF_INVALID_ROTATION:
            return DetectionError::INVALID_ROTATION;
        case MPF_MEMORY_ALLOCATION_FAILED:
            return DetectionError::MEMORY_ALLOCATION_FAILED;
        case MPF_OTHER_DETECTION_ERROR_TYPE:
            return DetectionError::UNRECOGNIZED_DETECTION_ERROR;
        default:
            return DetectionError::UNRECOGNIZED_DETECTION_ERROR;
    }
}
