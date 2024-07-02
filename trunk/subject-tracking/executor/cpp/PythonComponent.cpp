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

#include <exception>
#include <string>
#include <utility>

#include <pybind11/stl.h>

#include "detection.pb.h"
#include "PythonUtil.h"
#include "PythonScopedSignalHandler.h"

#include "PythonComponent.h"

namespace MPF::SUBJECT {

namespace {

    py::dict ToPython(const google::protobuf::Map<std::string, std::string>& map) {
        py::dict result;
        for (const auto& [key, value] : map) {
            result[py::str(key)] = value;
        }
        return result;
    }


    google::protobuf::Map<std::string, std::string> ConvertDict(const py::object& mapping) {
        google::protobuf::Map<std::string, std::string> result;
        for (const auto& pair : mapping.attr("items")()) {
            auto [key, value] = PythonUtil::ToStdPair<std::string, std::string>(pair);
            result[key] = std::move(value);
        }
        return result;
    }


    py::object ToPython(
            const mpf_buffers::ImageLocation& img_loc,
            const py::module_& detection_api) {
        return detection_api.attr("ImageLocation")(
            img_loc.x_left_upper(),
            img_loc.y_left_upper(),
            img_loc.width(),
            img_loc.height(),
            img_loc.confidence(),
            ToPython(img_loc.detection_properties())
        );
    }

    py::object ToPython(const mpf_buffers::VideoTrack& track, const py::module_& detection_api) {
        py::dict frame_locations;
        for (const auto& [frame, img_loc] : track.frame_locations()) {
            frame_locations[py::int_{frame}] = ToPython(img_loc, detection_api);
        }

        return detection_api.attr("VideoTrack")(
            track.start_frame(),
            track.stop_frame(),
            track.confidence(),
            frame_locations,
            ToPython(track.detection_properties())
        );
    }


    mpf_buffers::Entity ConvertEntity(py::handle py_entity) {
        mpf_buffers::Entity pb_entity;
        pb_entity.set_id(py::str(py_entity.attr("id")));
        pb_entity.set_score(1);
        auto& pb_tracks = *pb_entity.mutable_tracks();

        for (const auto& pair : py_entity.attr("tracks").attr("items")()) {
            auto [track_type, tracks] = PythonUtil::ToStdPair<std::string>(pair);
            auto& pb_track_id_list = pb_tracks[track_type];
            for (const auto& track_id : tracks) {
                pb_track_id_list.add_track_ids(track_id.cast<std::string>());
            }
        }
        *pb_entity.mutable_properties() = ConvertDict(py_entity.attr("properties"));
        return pb_entity;
    }

    mpf_buffers::Relationship ConvertRelationship(py::handle py_relationship) {
        mpf_buffers::Relationship pb_relationship;
        for (const auto& py_entity_id : py_relationship.attr("entities")) {
            pb_relationship.add_entities(py_entity_id.cast<py::str>());
        }

        auto& pb_frame_map = *pb_relationship.mutable_frames();
        for (const auto& py_media : py_relationship.attr("frames")) {
            auto& pb_frames = pb_frame_map[py_media.attr("id").cast<std::string>()];
            for (const auto& item : py_media.attr("frames")) {
                pb_frames.add_frames(item.cast<long>());
            }
        }
        *pb_relationship.mutable_properties() = ConvertDict(py_relationship.attr("properties"));
        return pb_relationship;
    }


    mpf_buffers::SubjectTrackingResult ResultsToCpp(py::handle py_results) {
        mpf_buffers::SubjectTrackingResult results;
        *results.mutable_properties() = ConvertDict(py_results.attr("properties"));

        auto& pb_entity_groups = *results.mutable_entity_groups();
        for (const auto& pair : py_results.attr("entities").attr("items")()) {
            auto [entity_type, entity_list] = PythonUtil::ToStdPair<std::string>(pair);
            auto& pb_entity_list = pb_entity_groups[entity_type];
            for (const auto& entity : entity_list) {
                *pb_entity_list.add_entities() = ConvertEntity(entity);
            }
        }

        auto& pb_relationship_groups = *results.mutable_relationship_groups();
        for (const auto& pair : py_results.attr("relationships").attr("items")()) {
            auto [relationship_type, relationship_list] = PythonUtil::ToStdPair<std::string>(pair);
            auto& pb_relationship_list = pb_relationship_groups[relationship_type];
            for (const auto& relationship : relationship_list) {
                *pb_relationship_list.add_relationships() = ConvertRelationship(relationship);
            }
        }
        return results;
    }

    mpf_buffers::SubjectTrackingResult CreateErrorResponse(const std::exception& exception) {
        mpf_buffers::SubjectTrackingResult result;
        result.add_errors(exception.what());
        return result;
    }
} // namespace

PythonComponent::PythonComponent(LoggerWrapper logger, std::string_view distribution_name)
        : logger_{std::move(logger)}
        , component_instance_{PythonUtil::LoadComponent(distribution_name)()} {
}

mpf_buffers::SubjectTrackingResult PythonComponent::GetSubjects(
        const mpf_buffers::SubjectTrackingJob& job) const {
    PythonScopedSignalHandler sig;
    auto py_job = ConvertSubjectTrackingJob(job);
    py::object py_results;
    try {
        py_results = component_instance_.attr("get_subjects")(py_job);
    }
    catch (const std::exception& e) {
        logger_.Error("The component threw an exception: ", e.what());
        return CreateErrorResponse(e);
    }
    return ResultsToCpp(py_results);
}

py::object PythonComponent::ConvertSubjectTrackingJob(
        const mpf_buffers::SubjectTrackingJob& subject_tracking_job) const {
    py::list image_jobs;
    for (const auto& image_job : subject_tracking_job.image_job_results()) {
        image_jobs.append(ConvertImageDetectionJob(image_job));
    }

    py::list video_jobs;
    for (const auto& video_job : subject_tracking_job.video_job_results()) {
        video_jobs.append(ConvertVideoDetectionJob(video_job));
    }
    return mpf_subject_api_.attr("SubjectTrackingJob")(
            subject_tracking_job.job_name(),
            ToPython(subject_tracking_job.job_properties()),
            video_jobs,
            image_jobs,
            py::list{},
            py::list{});
}

py::object PythonComponent::ConvertImageDetectionJob(
        const mpf_buffers::ImageDetectionJobResults& image_job) const {
    py::dict detection_results;
    for (const auto& [track_id, img_loc] : image_job.results()) {
        detection_results[py::str(track_id)] = ToPython(img_loc, mpf_component_api_);
    }
    const auto& detection_job = image_job.detection_job();
    return mpf_subject_api_.attr("ImageDetectionJobResults")(
            detection_job.data_uri(),
            detection_job.media_id(),
            detection_job.algorithm(),
            detection_job.track_type(),
            ToPython(detection_job.job_properties()),
            ToPython(detection_job.media_properties()),
            detection_results);
}


py::object PythonComponent::ConvertVideoDetectionJob(
        const mpf_buffers::VideoDetectionJobResults& video_job) const {
    py::dict detection_results;
    for (const auto& [track_id, track] : video_job.results()) {
        detection_results[py::str{track_id}] = ToPython(track, mpf_component_api_);
    }

    const auto& detection_job = video_job.detection_job();
    return mpf_subject_api_.attr("VideoDetectionJobResults")(
        detection_job.data_uri(),
        detection_job.media_id(),
        detection_job.algorithm(),
        detection_job.track_type(),
        ToPython(detection_job.job_properties()),
        ToPython(detection_job.media_properties()),
        detection_results
    );
}

} // namespace MPF::SUBJECT
