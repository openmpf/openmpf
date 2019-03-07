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
    video_request.has_feed_forward_track = false;
    //If there is a feed-forward track in the request, copy it into an
    //MPFVideoTrack
    if (detection_request.video_request().has_feed_forward_track()) {
        video_request.has_feed_forward_track = true;
        video_request.feed_forward_track.start_frame =
                detection_request.video_request().feed_forward_track().start_frame();
        video_request.feed_forward_track.stop_frame =
                detection_request.video_request().feed_forward_track().stop_frame();
        video_request.feed_forward_track.confidence =
                detection_request.video_request().feed_forward_track().confidence();
        // Copy the track properties
        for (auto prop : detection_request.video_request().feed_forward_track().detection_properties()) {
            video_request.feed_forward_track.detection_properties[prop.key()] = prop.value();
        }
        for (auto loc : detection_request.video_request().feed_forward_track().frame_locations()) {
            Properties tmp_props;
            for (auto prop : loc.image_location().detection_properties()) {
                tmp_props[prop.key()] = prop.value();
            }
            MPFImageLocation tmp_loc(loc.image_location().x_left_upper(),
                                     loc.image_location().y_left_upper(),
                                     loc.image_location().width(),
                                     loc.image_location().height(),
                                     loc.image_location().confidence(),
                                     tmp_props);
            video_request.feed_forward_track.frame_locations[loc.frame()] = tmp_loc;
        }
    }
}

void MPFDetectionBuffer::GetAudioRequest(MPFDetectionAudioRequest &audio_request) {
    audio_request.start_time = detection_request.audio_request().start_time();
    audio_request.stop_time = detection_request.audio_request().stop_time();
    audio_request.has_feed_forward_track = false;
    // If there is a feed-forward track in the request, copy it into an MPFAudioTrack
    if (detection_request.audio_request().has_feed_forward_track()) {
        audio_request.has_feed_forward_track = true;
        audio_request.feed_forward_track.start_time =
                detection_request.audio_request().feed_forward_track().start_time();
        audio_request.feed_forward_track.stop_time =
                detection_request.audio_request().feed_forward_track().stop_time();
        audio_request.feed_forward_track.confidence =
                detection_request.audio_request().feed_forward_track().confidence();
        // Copy the track properties
        for (auto prop : detection_request.audio_request().feed_forward_track().detection_properties()) {
            audio_request.feed_forward_track.detection_properties[prop.key()] = prop.value();
        }
    }
}

void MPFDetectionBuffer::GetImageRequest(MPFDetectionImageRequest &image_request) {
    image_request.has_feed_forward_location = false;
    // If there is a feed-forward location in the request, copy it into an MPFImageLocation
    if (detection_request.image_request().has_feed_forward_location()) {
        image_request.has_feed_forward_location = true;
        image_request.feed_forward_location.x_left_upper =
                detection_request.image_request().feed_forward_location().x_left_upper();
        image_request.feed_forward_location.y_left_upper =
                detection_request.image_request().feed_forward_location().y_left_upper();
        image_request.feed_forward_location.width =
                detection_request.image_request().feed_forward_location().width();
        image_request.feed_forward_location.height =
                detection_request.image_request().feed_forward_location().height();
        image_request.feed_forward_location.confidence =
                detection_request.image_request().feed_forward_location().confidence();
    }
    // Copy the image location properties
    for (auto prop : detection_request.image_request().feed_forward_location().detection_properties()) {
        image_request.feed_forward_location.detection_properties[prop.key()] = prop.value();
    }
}

void MPFDetectionBuffer::GetGenericRequest(MPFDetectionGenericRequest &generic_request) {
    generic_request.has_feed_forward_track = false;
    // If there is a feed-forward track in the request, copy it into an MPFGenericTrack
    if (detection_request.generic_request().has_feed_forward_track()) {
        generic_request.has_feed_forward_track = true;
        generic_request.feed_forward_track.confidence =
                detection_request.audio_request().feed_forward_track().confidence();
        // Copy the track properties
        for (auto prop : detection_request.generic_request().feed_forward_track().detection_properties()) {
            generic_request.feed_forward_track.detection_properties[prop.key()] = prop.value();
        }
    }
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
        const int start_frame,
        const int stop_frame,
        const string detection_type,
        int *packed_length,
        const MPFDetectionError error) const {
    // Caller has to delete returned data

    DetectionResponse detection_response;
    PackCommonFields(msg_metadata, data_type, error, detection_response);

    DetectionResponse_VideoResponse *video_response = detection_response.add_video_responses();
    video_response->set_start_frame(start_frame);
    video_response->set_stop_frame(stop_frame);
    video_response->set_detection_type(detection_type);

    for (vector<MPFVideoTrack>::const_iterator tracks_iter = tracks.begin(); tracks_iter != tracks.end(); tracks_iter++) {
        MPFVideoTrack track = *tracks_iter;
        VideoTrack *new_track = video_response->add_video_tracks();
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

            VideoTrack_FrameLocationMap *new_frame_location = new_track->add_frame_locations();

            new_frame_location->set_frame(locations_iter->first);

            ImageLocation *new_detection = new_frame_location->mutable_image_location();
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
        const int start_time,
        const int stop_time,
        const string detection_type,
        int *packed_length,
        const MPFDetectionError error) const {
    // Caller has to delete returned data

    DetectionResponse detection_response;
    PackCommonFields(msg_metadata, data_type, error, detection_response);

    DetectionResponse_AudioResponse *audio_response = detection_response.add_audio_responses();
    audio_response->set_start_time(start_time);
    audio_response->set_stop_time(stop_time);
    audio_response->set_detection_type(detection_type);

    for (vector<MPFAudioTrack>::const_iterator tracks_iter = tracks.begin(); tracks_iter != tracks.end(); tracks_iter++) {
        MPFAudioTrack track = *tracks_iter;
        AudioTrack *new_track = audio_response->add_audio_tracks();
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
        ImageLocation *new_detection = image_response->add_image_locations();
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

unsigned char *MPFDetectionBuffer::PackGenericResponse(
        const vector<MPFGenericTrack> &tracks,
        const MPFMessageMetadata *const msg_metadata,
        const MPFDetectionDataType data_type,
        const string detection_type,
        int *packed_length,
        const MPFDetectionError error) const {
    // Caller has to delete returned data

    DetectionResponse detection_response;
    PackCommonFields(msg_metadata, data_type, error, detection_response);

    DetectionResponse_GenericResponse *generic_response = detection_response.add_generic_responses();
    generic_response->set_detection_type(detection_type);

    for (vector<MPFGenericTrack>::const_iterator tracks_iter = tracks.begin(); tracks_iter != tracks.end(); tracks_iter++) {
        MPFGenericTrack track = *tracks_iter;
        GenericTrack *new_track = generic_response->add_generic_tracks();
        new_track->set_confidence(track.confidence);
        for (auto const &prop : track.detection_properties) {
            org::mitre::mpf::wfm::buffers::PropertyMap *detection_prop = new_track->add_detection_properties();
            detection_prop->set_key(prop.first);
            detection_prop->set_value(prop.second);
        }
    }

    return FinalizeDetectionResponse(detection_response, packed_length);
}

