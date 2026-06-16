#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2024 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2024 The MITRE Corporation                                      #
#                                                                           #
# Licensed under the Apache License, Version 2.0 (the "License");           #
# you may not use this file except in compliance with the License.          #
# You may obtain a copy of the License at                                   #
#                                                                           #
#    http://www.apache.org/licenses/LICENSE-2.0                             #
#                                                                           #
# Unless required by applicable law or agreed to in writing, software       #
# distributed under the License is distributed on an "AS IS" BASIS,         #
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  #
# See the License for the specific language governing permissions and       #
# limitations under the License.                                            #
#############################################################################

import importlib.metadata
from typing import Type

import mpf_component_api as mpf
import mpf_subject_api as mpf_sub

from . import executor_util as util
from .logger_wrapper import LoggerWrapper
from .mpf_proto import detection_pb2 as pb_det
from .mpf_proto import subject_pb2 as pb_sub


class PythonComponent:
    def __init__(self, logger: LoggerWrapper, distribution_name: str):
        self._logger = logger
        self._component_instance = self._load_component_cls(distribution_name)()


    def get_subjects(self, pb_job: pb_sub.SubjectTrackingJob) -> pb_sub.SubjectTrackingResult:
        job = ProtobufToSubjectJobConverter.convert(pb_job)
        try:
            results = self._component_instance.get_subjects(job)
        except Exception as e:
            self._logger.exception(
                'Creating error response because the component threw an exception: ', e)
            return pb_sub.SubjectTrackingResult(errors=[str(e)])
        return SubjectResultToProtobufConverter.convert(results)


    @staticmethod
    def _load_component_cls(distribution_name: str) -> Type[mpf_sub.SubjectTrackingComponent]:
        try:
            distribution = importlib.metadata.distribution(distribution_name)
            if len(distribution.entry_points) == 1:
                return next(iter(distribution.entry_points)).load()
            group_matches = None
            for entry_point in distribution.entry_points:
                if entry_point.group != 'mpf.exported_component':
                    continue
                if entry_point.name == 'component':
                    return entry_point.load()
                group_matches = entry_point
            if group_matches:
                # An entry point in the "mpf.exported_component" group was found, but the
                # left-hand side of the '=' was something else. For example
                # 'mpf.exported_component': 'MyComponentClass = my_module:MyComponentClass'
                # We really only care about the entry point group, since we don't do anything
                # with entry point name.
                return group_matches.load()
        except importlib.metadata.PackageNotFoundError as e:
            raise util.ComponentLoadError(
                    f'Could not find package named {distribution_name}') from e
        except Exception as e:
            raise util.ComponentLoadError(f'Failed to load component due to: {e}') from e
        raise util.ComponentLoadError('Component entrypoint not present.')




class ProtobufToSubjectJobConverter:
    @classmethod
    def convert(cls, pb_job: pb_sub.SubjectTrackingJob) -> mpf_sub.SubjectTrackingJob:
        video_jobs = [cls._convert_video_job(j) for j in pb_job.video_job_results]
        image_jobs = [cls._convert_image_job(j) for j in pb_job.image_job_results]
        audio_jobs = [cls._convert_audio_job(j) for j in pb_job.audio_job_results]
        generic_jobs = [cls._convert_generic_job(j) for j in pb_job.generic_job_results]
        return mpf_sub.SubjectTrackingJob(
            pb_job.job_name,
            dict(pb_job.job_properties),
            video_jobs,
            image_jobs,
            audio_jobs,
            generic_jobs)


    @classmethod
    def _convert_video_job(
            cls, pb_vid_job: pb_sub.VideoDetectionJobResults) -> mpf_sub.VideoDetectionJobResults:
        detection_results = {
                mpf_sub.TrackId(t_id): cls._convert_video_track(track)
                for t_id, track in  pb_vid_job.results.items()}
        detection_job = pb_vid_job.detection_job
        return mpf_sub.VideoDetectionJobResults(
            detection_job.data_uri,
            mpf_sub.MediaId(detection_job.media_id),
            detection_job.algorithm,
            mpf_sub.DetectionComponentType(detection_job.track_type),
            dict(detection_job.job_properties),
            dict(detection_job.media_properties),
            detection_results)


    @classmethod
    def _convert_image_job(
            cls, pb_img_job: pb_sub.ImageDetectionJobResults) -> mpf_sub.ImageDetectionJobResults:
        detection_results = {
                mpf_sub.TrackId(t_id): cls._convert_image_detection(img_loc)
                for t_id, img_loc in pb_img_job.results.items()}

        detection_job = pb_img_job.detection_job
        return mpf_sub.ImageDetectionJobResults(
            detection_job.data_uri,
            mpf_sub.MediaId(detection_job.media_id),
            detection_job.algorithm,
            mpf_sub.DetectionComponentType(detection_job.track_type),
            dict(detection_job.job_properties),
            dict(detection_job.media_properties),
            detection_results)

    @classmethod
    def _convert_video_track(cls, track: pb_det.VideoTrack) -> mpf.VideoTrack:
        frame_locations = {
            f: cls._convert_image_detection(img_loc)
            for f, img_loc in track.frame_locations.items()}
        return mpf.VideoTrack(
            track.start_frame,
            track.stop_frame,
            track.confidence,
            frame_locations,
            dict(track.detection_properties))

    @staticmethod
    def _convert_image_detection(pb_img_detection: pb_det.ImageLocation) -> mpf.ImageLocation:
        return mpf.ImageLocation(
            pb_img_detection.x_left_upper,
            pb_img_detection.y_left_upper,
            pb_img_detection.width,
            pb_img_detection.height,
            pb_img_detection.confidence,
            dict(pb_img_detection.detection_properties))


    @classmethod
    def _convert_audio_job(
            cls, pb_job: pb_sub.AudioDetectionJobResults) -> mpf_sub.AudioDetectionJobResults:
        detection_results = {
            mpf_sub.TrackId(t_id): cls._convert_audio_track(track)
            for t_id, track in pb_job.results.items()
        }
        detection_job = pb_job.detection_job
        return mpf_sub.AudioDetectionJobResults(
            detection_job.data_uri,
            mpf_sub.MediaId(detection_job.media_id),
            detection_job.algorithm,
            mpf_sub.DetectionComponentType(detection_job.track_type),
            dict(detection_job.job_properties),
            dict(detection_job.media_properties),
            detection_results)

    @staticmethod
    def _convert_audio_track(pb_track: pb_det.AudioTrack) -> mpf.AudioTrack:
        return mpf.AudioTrack(
            pb_track.start_time,
            pb_track.stop_time,
            pb_track.confidence,
            dict(pb_track.detection_properties))


    @classmethod
    def _convert_generic_job(
            cls, pb_job: pb_sub.GenericDetectionJobResults) -> mpf_sub.GenericDetectionJobResults:
        detection_results = {
            mpf_sub.TrackId(t_id): cls._convert_generic_track(track)
            for t_id, track in pb_job.results.items()
        }
        detection_job = pb_job.detection_job
        return mpf_sub.GenericDetectionJobResults(
            detection_job.data_uri,
            mpf_sub.MediaId(detection_job.media_id),
            detection_job.algorithm,
            mpf_sub.DetectionComponentType(detection_job.track_type),
            dict(detection_job.job_properties),
            dict(detection_job.media_properties),
            detection_results)


    @staticmethod
    def _convert_generic_track(pb_track: pb_det.GenericTrack) -> mpf.GenericTrack:
        return mpf.GenericTrack(pb_track.confidence, dict(pb_track.detection_properties))



class SubjectResultToProtobufConverter:

    @classmethod
    def convert(cls, results: mpf_sub.SubjectTrackingResults) -> pb_sub.SubjectTrackingResult:
        pb_entity_groups = {}
        for type_, entities in results.entities.items():
            pb_entity_groups[type_] = pb_sub.EntityList(
                    entities=[cls._convert_entity(e) for e in entities])

        pb_rel_groups = {}
        for type_, rels in results.relationships.items():
            pb_rel_groups[type_] = pb_sub.RelationshipList(
                    relationships=[cls._convert_relationship(r) for r in rels])

        return pb_sub.SubjectTrackingResult(
                entity_groups=pb_entity_groups,
                relationship_groups=pb_rel_groups,
                properties=results.properties,
                errors=())


    @staticmethod
    def _convert_entity(entity: mpf_sub.Entity) -> pb_sub.Entity:
        pb_tracks = {
                str(type_): pb_sub.TrackIdList(track_ids=ids)
                for type_, ids in entity.tracks.items()}
        return pb_sub.Entity(
            id=str(entity.id),
            score=entity.score,
            tracks=pb_tracks,
            properties=entity.properties
        )

    @staticmethod
    def _convert_relationship(relationship: mpf_sub.Relationship) -> pb_sub.Relationship:
        return pb_sub.Relationship(
            entities=[str(e_id) for e_id in relationship.entities],
            frames={m.id: pb_sub.FrameList(frames=m.frames) for m in relationship.frames},
            properties=relationship.properties
        )
