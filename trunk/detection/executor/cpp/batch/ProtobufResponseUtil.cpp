/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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


#include "MPFMessageUtils.h"

#include "ProtobufResponseUtil.h"

namespace MPF::COMPONENT::ProtobufResponseUtil {

    std::vector<unsigned char> PackErrorResponse(
            const JobContext& context, MPFDetectionError error_code,
            std::string_view explanation) {

        auto detection_response = detail::InitDetectionResponse(context);
        detection_response.set_error(translateMPFDetectionError(error_code));
        detection_response.set_error_message(explanation.data(), explanation.size());
        switch (context.job_type) {
            case MPFDetectionDataType::VIDEO: {
                auto video_response = detection_response.add_video_responses();
                video_response->set_detection_type(context.detection_type);
                video_response->set_start_frame(context.GetVideoJob().start_frame);
                video_response->set_stop_frame(context.GetVideoJob().stop_frame);
                break;
            }
            case MPFDetectionDataType::IMAGE: {
                detection_response.add_image_responses()->set_detection_type(
                        context.detection_type);
                break;
            }
            case MPFDetectionDataType::AUDIO: {
                auto audio_response = detection_response.add_audio_responses();
                audio_response->set_detection_type(context.detection_type);
                audio_response->set_start_time(context.GetAudioJob().start_time);
                audio_response->set_stop_time(context.GetAudioJob().stop_time);
                break;
            }
            default: {
                detection_response.add_generic_responses()->set_detection_type(
                        context.detection_type);
                break;
            }
        }
        return detail::Serialize(detection_response);
    }
}

namespace MPF::COMPONENT::ProtobufResponseUtil::detail {

    mpf_buffers::DetectionResponse InitDetectionResponse(const JobContext& context) {
        mpf_buffers::DetectionResponse detection_response;
        detection_response.set_request_id(context.request_id);
        detection_response.set_media_id(context.media_id);
        detection_response.set_task_index(context.task_index);
        detection_response.set_task_name(context.task_name);
        detection_response.set_action_index(context.action_index);
        detection_response.set_action_name(context.action_name);
        detection_response.set_data_type(translateMPFDetectionDataType(context.job_type));
        return detection_response;
    }

    std::vector<unsigned char> Serialize(const mpf_buffers::DetectionResponse& detection_response) {
        int protobuf_size = detection_response.ByteSize();
        std::vector<unsigned char> proto_bytes(protobuf_size);
        detection_response.SerializeToArray(proto_bytes.data(), protobuf_size);
        return proto_bytes;
    }


    void AddToProtobuf(const MPFImageLocation& img_loc, mpf_buffers::ImageLocation& pb_img_loc) {
        pb_img_loc.set_x_left_upper(img_loc.x_left_upper);
        pb_img_loc.set_y_left_upper(img_loc.y_left_upper);
        pb_img_loc.set_width(img_loc.width);
        pb_img_loc.set_height(img_loc.height);
        pb_img_loc.set_confidence(img_loc.confidence);

        for (const auto& [key, value] : img_loc.detection_properties) {
            auto prop = pb_img_loc.add_detection_properties();
            prop->set_key(key);
            prop->set_value(value);
        }
    }


    void AddToProtobuf(
            const JobContext& context,
            const std::vector<MPFVideoTrack>& tracks,
            mpf_buffers::DetectionResponse& response) {
        auto video_response = response.add_video_responses();
        video_response->set_detection_type(context.detection_type);
        video_response->set_start_frame(context.GetVideoJob().start_frame);
        video_response->set_stop_frame(context.GetVideoJob().stop_frame);
        for (const auto &track : tracks) {
            auto pb_track = video_response->add_video_tracks();
            pb_track->set_start_frame(track.start_frame);
            pb_track->set_stop_frame(track.stop_frame);
            pb_track->set_confidence(track.confidence);
            for (const auto &[key, value] : track.detection_properties) {
                auto prop = pb_track->add_detection_properties();
                prop->set_key(key);
                prop->set_value(value);
            }
            for (const auto &[frame_idx, img_loc] : track.frame_locations) {
                auto frame_location = pb_track->add_frame_locations();
                frame_location->set_frame(frame_idx);
                AddToProtobuf(img_loc, *frame_location->mutable_image_location());
            }
        }
    }


    void AddToProtobuf(
            const JobContext& context,
            const std::vector<MPFImageLocation>& image_locations,
            mpf_buffers::DetectionResponse& response) {
        auto image_response = response.add_image_responses();
        image_response->set_detection_type(context.detection_type);
        for (const auto& img_loc : image_locations) {
            AddToProtobuf(img_loc, *image_response->add_image_locations());
        }
    }


    void AddToProtobuf(
            const JobContext& context,
            const std::vector<MPFAudioTrack>& tracks,
            mpf_buffers::DetectionResponse& response) {
        auto audio_response = response.add_audio_responses();
        audio_response->set_detection_type(context.detection_type);
        audio_response->set_start_time(context.GetAudioJob().start_time);
        audio_response->set_stop_time(context.GetAudioJob().stop_time);
        for (const auto &track : tracks) {
            auto pb_track = audio_response->add_audio_tracks();
            pb_track->set_start_time(track.start_time);
            pb_track->set_stop_time(track.stop_time);
            pb_track->set_confidence(track.confidence);
            for (const auto &[key, value] : track.detection_properties) {
                auto prop = pb_track->add_detection_properties();
                prop->set_key(key);
                prop->set_value(value);
            }
        }
    }

    void AddToProtobuf(
            const JobContext& context,
            const std::vector<MPFGenericTrack>& tracks,
            mpf_buffers::DetectionResponse& response) {
        auto generic_response = response.add_generic_responses();
        generic_response->set_detection_type(context.detection_type);
        for (const auto &track : tracks) {
            auto pb_track = generic_response->add_generic_tracks();
            pb_track->set_confidence(track.confidence);
            for (const auto &[key, value] : track.detection_properties) {
                auto prop = pb_track->add_detection_properties();
                prop->set_key(key);
                prop->set_value(value);
            }
        }
    }
}
