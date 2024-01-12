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

#include <filesystem>
#include <utility>

#include <MPFDetectionObjects.h>

#include "ProtobufRequestUtil.h"

namespace MPF::COMPONENT::ProtobufRequestUtil {
    namespace {
        Properties GetProperties(
                const google::protobuf::RepeatedPtrField<mpf_buffers::PropertyMap>& protobuf_props) {
            Properties detection_properties;
            for (const auto& prop : protobuf_props) {
                detection_properties.emplace(prop.key(), prop.value());
            }
            return detection_properties;
        }

        Properties GetMediaProperties(
                const mpf_buffers::DetectionRequest& detection_request) {
            return GetProperties(detection_request.media_metadata());
        }

        Properties GetJobProperties(
                const mpf_buffers::DetectionRequest& detection_request,
                const Properties& environment_job_properties) {
            auto job_properties = environment_job_properties;
            for (const auto& prop : detection_request.algorithm_property()) {
                job_properties.emplace(prop.property_name(), prop.property_value());
            }
            return job_properties;
        }

        MPFImageLocation ConvertFeedForwardLocation(
                const mpf_buffers::ImageLocation& ff_location) {
            return {
                ff_location.x_left_upper(),
                ff_location.y_left_upper(),
                ff_location.width(),
                ff_location.height(),
                ff_location.confidence(),
                GetProperties(ff_location.detection_properties())
            };
        }


        MPFVideoJob CreateVideoJob(
                const mpf_buffers::DetectionRequest& detection_request,
                std::string_view job_name,
                const Properties& environment_properties) {
            const auto& video_request = detection_request.video_request();
            if (video_request.has_feed_forward_track()) {
                const auto& pb_ff_track = video_request.feed_forward_track();
                MPFVideoTrack ff_track{
                    pb_ff_track.start_frame(),
                    pb_ff_track.stop_frame(),
                    pb_ff_track.confidence(),
                    GetProperties(pb_ff_track.detection_properties())
                };
                auto& ff_frame_locations = ff_track.frame_locations;
                for (const auto& entry : pb_ff_track.frame_locations()) {
                    ff_frame_locations.try_emplace(
                        entry.frame(), ConvertFeedForwardLocation(entry.image_location()));
                }
                return {
                    std::string{job_name},
                    detection_request.data_uri(),
                    video_request.start_frame(),
                    video_request.stop_frame(),
                    std::move(ff_track),
                    GetJobProperties(detection_request, environment_properties),
                    GetMediaProperties(detection_request)
                };
            }
            else {
                return {
                    std::string{job_name},
                    detection_request.data_uri(),
                    video_request.start_frame(),
                    video_request.stop_frame(),
                    GetJobProperties(detection_request, environment_properties),
                    GetMediaProperties(detection_request)
                };
            }
        }


        MPFImageJob CreateImageJob(
                const mpf_buffers::DetectionRequest& detection_request,
                std::string_view job_name,
                const Properties& environment_properties) {
            if (detection_request.image_request().has_feed_forward_location()) {
                return {
                    std::string{job_name},
                    detection_request.data_uri(),
                    ConvertFeedForwardLocation(
                        detection_request.image_request().feed_forward_location()),
                    GetJobProperties(detection_request, environment_properties),
                    GetMediaProperties(detection_request)
                };
            }
            else {
                return {
                    std::string{job_name},
                    detection_request.data_uri(),
                    GetJobProperties(detection_request, environment_properties),
                    GetMediaProperties(detection_request)
                };
            }
        }


        MPFAudioJob CreateAudioJob(
                const mpf_buffers::DetectionRequest& detection_request,
                std::string_view job_name,
                const Properties& environment_properties) {
            const auto& audio_request = detection_request.audio_request();
            if (audio_request.has_feed_forward_track()) {
                const auto& pb_ff_track = audio_request.feed_forward_track();
                MPFAudioTrack ff_track {
                    pb_ff_track.start_time(),
                    pb_ff_track.stop_time(),
                    pb_ff_track.confidence(),
                    GetProperties(pb_ff_track.detection_properties())
                };
                return {
                    std::string{job_name},
                    detection_request.data_uri(),
                    audio_request.start_time(),
                    audio_request.stop_time(),
                    std::move(ff_track),
                    GetJobProperties(detection_request, environment_properties),
                    GetMediaProperties(detection_request)
                };
            }
            else {
                return {
                    std::string{job_name},
                    detection_request.data_uri(),
                    audio_request.start_time(),
                    audio_request.stop_time(),
                    GetJobProperties(detection_request, environment_properties),
                    GetMediaProperties(detection_request)
                };
            }
        }


        MPFGenericJob CreateGenericJob(
                const mpf_buffers::DetectionRequest& detection_request,
                std::string_view job_name,
                const Properties& environment_properties) {
            if (detection_request.generic_request().has_feed_forward_track()) {
                const auto& ff_track = detection_request.generic_request().feed_forward_track();
                return {
                    std::string{job_name},
                    detection_request.data_uri(),
                    MPFGenericTrack{
                        ff_track.confidence(),
                        GetProperties(ff_track.detection_properties())
                    },
                    GetJobProperties(detection_request, environment_properties),
                    GetMediaProperties(detection_request)
                };
            }
            else {
                return {
                    std::string{job_name},
                    detection_request.data_uri(),
                    GetJobProperties(detection_request, environment_properties),
                    GetMediaProperties(detection_request)
                };
            }
        }
    } // End anonymous namespace


    mpf_buffers::DetectionRequest ParseRequest(const std::vector<unsigned char>& bytes) {
        mpf_buffers::DetectionRequest detection_request;
        detection_request.ParseFromArray(bytes.data(), static_cast<int>(bytes.size()));
        return detection_request;
    }

    std::string GetJobName(
            long job_id, const mpf_buffers::DetectionRequest& detection_request) {
        std::string job_name{"Job "};
        job_name += std::to_string(job_id);
        job_name += ':';
        job_name += std::filesystem::path{detection_request.data_uri()}.filename();

        int begin = -1;
        int end = -1;
        if (detection_request.has_video_request()) {
            begin = detection_request.video_request().start_frame();
            end = detection_request.video_request().stop_frame();
        }
        else if (detection_request.has_audio_request()) {
            begin = detection_request.audio_request().start_time();
            end = detection_request.audio_request().stop_time();
        }
        else {
            return job_name;
        }
        job_name += '(';
        job_name += std::to_string(begin);
        job_name += '-';
        job_name += std::to_string(end);
        job_name += ')';
        return job_name;
    }

    job_variant_t CreateComponentJob(
            std::string_view job_name,
            const Properties& environment_job_properties,
            const mpf_buffers::DetectionRequest& detection_request) {
        switch (detection_request.data_type()) {
            case mpf_buffers::DetectionRequest_DataType::DetectionRequest_DataType_VIDEO:
                return CreateVideoJob(detection_request, job_name, environment_job_properties);
            case mpf_buffers::DetectionRequest_DataType::DetectionRequest_DataType_IMAGE:
                return CreateImageJob(detection_request, job_name, environment_job_properties);
            case mpf_buffers::DetectionRequest_DataType::DetectionRequest_DataType_AUDIO:
                return CreateAudioJob(detection_request, job_name, environment_job_properties);
            case mpf_buffers::DetectionRequest_DataType::DetectionRequest_DataType_UNKNOWN:
                return CreateGenericJob(detection_request, job_name, environment_job_properties);
            default:
                throw std::runtime_error{"Received message with incorrect data type."};
        }
    }


    ProtobufMetadata GetMetadata(const mpf_buffers::DetectionRequest& detection_request) {
        return {
            detection_request.request_id(),
            detection_request.media_id(),
            detection_request.task_index(),
            detection_request.task_name(),
            detection_request.action_index(),
            detection_request.action_name()
        };
    }
}
