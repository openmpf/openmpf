/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

#include <limits>
#include <stdexcept>
#include <variant>

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
                auto video_response = detection_response.mutable_video_response();
                if (std::holds_alternative<MPFVideoJob>(context.job)) {
                    const auto& video_job = std::get<MPFVideoJob>(context.job);
                    video_response->set_start_frame(video_job.start_frame);
                    video_response->set_stop_frame(video_job.stop_frame);
                } else {
                    const auto& video_job = std::get<MPFAllVideoTracksJob>(context.job);
                    video_response->set_start_frame(video_job.start_frame);
                    video_response->set_stop_frame(video_job.stop_frame);
                }
                break;
            }
            case MPFDetectionDataType::IMAGE: {
                detection_response.mutable_image_response();
                break;
            }
            case MPFDetectionDataType::AUDIO: {
                auto audio_response = detection_response.mutable_audio_response();
                if (std::holds_alternative<MPFAudioJob>(context.job)) {
                    const auto& audio_job = std::get<MPFAudioJob>(context.job);
                    audio_response->set_start_time(audio_job.start_time);
                    audio_response->set_stop_time(audio_job.stop_time);
                } else {
                    const auto& audio_job = std::get<MPFAllAudioTracksJob>(context.job);
                    audio_response->set_start_time(audio_job.start_time);
                    audio_response->set_stop_time(audio_job.stop_time);
                }
                break;
            }
            default: {
                detection_response.mutable_generic_response();
                break;
            }
        }
        return detail::Serialize(detection_response);
    }
}

namespace MPF::COMPONENT::ProtobufResponseUtil::detail {

    mpf_buffers::DetectionResponse InitDetectionResponse(const JobContext& context) {
        mpf_buffers::DetectionResponse detection_response;
        const auto& pb_meta = context.protobuf_metadata;
        detection_response.set_media_id(pb_meta.media_id);
        detection_response.set_task_index(pb_meta.task_index);
        detection_response.set_action_index(pb_meta.action_index);
        return detection_response;
    }

    std::vector<unsigned char> Serialize(const mpf_buffers::DetectionResponse& detection_response) {
        auto protobuf_size = detection_response.ByteSizeLong();
        if (protobuf_size > std::numeric_limits<int>::max()) {
            throw std::length_error(
                "Could not send response because the response protobuf was "
                + std::to_string(protobuf_size)
                + " bytes, but ActiveMQ only accepts messages up to "
                + std::to_string(std::numeric_limits<int>::max())
                + " bytes.");
        }
        std::vector<unsigned char> proto_bytes(protobuf_size);
        detection_response.SerializeWithCachedSizesToArray(proto_bytes.data());
        return proto_bytes;
    }


    void AddToProtobuf(const MPFImageLocation& img_loc, mpf_buffers::ImageLocation& pb_img_loc) {
        pb_img_loc.set_x_left_upper(img_loc.x_left_upper);
        pb_img_loc.set_y_left_upper(img_loc.y_left_upper);
        pb_img_loc.set_width(img_loc.width);
        pb_img_loc.set_height(img_loc.height);
        pb_img_loc.set_confidence(img_loc.confidence);
        pb_img_loc.mutable_detection_properties()->insert(
                img_loc.detection_properties.begin(),
                img_loc.detection_properties.end());
    }


    void AddToProtobuf(
            const JobContext& context,
            const std::vector<MPFVideoTrack>& tracks,
            mpf_buffers::DetectionResponse& response) {
        auto video_response = response.mutable_video_response();

        if (std::holds_alternative<MPFVideoJob>(context.job)) {
            const auto& video_job = std::get<MPFVideoJob>(context.job);
            video_response->set_start_frame(video_job.start_frame);
            video_response->set_stop_frame(video_job.stop_frame);
        } else {
            const auto& video_job = std::get<MPFAllVideoTracksJob>(context.job);
            video_response->set_start_frame(video_job.start_frame);
            video_response->set_stop_frame(video_job.stop_frame);
        }

        for (const auto &track : tracks) {
            auto pb_track = video_response->add_video_tracks();
            pb_track->set_start_frame(track.start_frame);
            pb_track->set_stop_frame(track.stop_frame);
            pb_track->set_confidence(track.confidence);
            pb_track->mutable_detection_properties()->insert(
                    track.detection_properties.begin(),
                    track.detection_properties.end());

            auto& pb_frame_locations = *pb_track->mutable_frame_locations();
            for (const auto& [frame_idx, img_loc] : track.frame_locations) {
                mpf_buffers::ImageLocation frame_location;
                AddToProtobuf(img_loc, frame_location);
                pb_frame_locations[frame_idx] = frame_location;
            }
        }
    }


    void AddToProtobuf(
            const JobContext& context,
            const std::vector<MPFImageLocation>& image_locations,
            mpf_buffers::DetectionResponse& response) {
        auto image_response = response.mutable_image_response();
        for (const auto& img_loc : image_locations) {
            AddToProtobuf(img_loc, *image_response->add_image_locations());
        }
    }


    void AddToProtobuf(
            const JobContext& context,
            const std::vector<MPFAudioTrack>& tracks,
            mpf_buffers::DetectionResponse& response) {
        auto audio_response = response.mutable_audio_response();
        if (std::holds_alternative<MPFAudioJob>(context.job)) {
            const auto& audio_job = std::get<MPFAudioJob>(context.job);
            audio_response->set_start_time(audio_job.start_time);
            audio_response->set_stop_time(audio_job.stop_time);
        } else {
            const auto& audio_job = std::get<MPFAllAudioTracksJob>(context.job);
            audio_response->set_start_time(audio_job.start_time);
            audio_response->set_stop_time(audio_job.stop_time);
        }
        for (const auto &track : tracks) {
            auto pb_track = audio_response->add_audio_tracks();
            pb_track->set_start_time(track.start_time);
            pb_track->set_stop_time(track.stop_time);
            pb_track->set_confidence(track.confidence);
            pb_track->mutable_detection_properties()->insert(
                    track.detection_properties.begin(),
                    track.detection_properties.end());
        }
    }

    void AddToProtobuf(
            const JobContext&,
            const std::vector<MPFGenericTrack>& tracks,
            mpf_buffers::DetectionResponse& response) {
        auto generic_response = response.mutable_generic_response();
        for (const auto &track : tracks) {
            auto pb_track = generic_response->add_generic_tracks();
            pb_track->set_confidence(track.confidence);
            pb_track->mutable_detection_properties()->insert(
                    track.detection_properties.begin(),
                    track.detection_properties.end());
        }
    }
}
