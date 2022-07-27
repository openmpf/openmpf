#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2022 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2022 The MITRE Corporation                                      #
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

import logging

import mpf_component_api as mpf

logger = logging.getLogger('TestComponent')

class TestComponent(object):
    detection_type = 'TEST DETECTION TYPE'

    def __init__(self):
        logger.info('Creating instance of TestComponent')


    @staticmethod  # Make sure executor can call static methods
    def get_detections_from_image(image_job):
        logger.info('[%s] Received image job: %s', image_job.job_name, image_job)
        if image_job.feed_forward_location is not None:
            yield image_job.feed_forward_location
            return
        il = mpf.ImageLocation(0, 0, 100, 100)
        echo_job, echo_media = TestComponent.get_echo_msgs(image_job)

        il.detection_properties['METADATA'] = 'extra info for first result'
        il.detection_properties['ECHO_JOB'] = echo_job
        il.detection_properties['ECHO_MEDIA'] = echo_media

        # Make sure generators are acceptable return values
        yield il

        error_code = image_job.job_properties.get('raise_exception', None)
        if error_code is not None:
            raise mpf.DetectionException('Exception Message', mpf.DetectionError(int(error_code)))

        yield mpf.ImageLocation(10, 20, 12, 34, -1,
                                {'METADATA': 'extra info for second result',
                                 'ECHO_JOB': echo_job,
                                 'ECHO_MEDIA': echo_media})

        logger.info('[%s] Found %s detections', image_job.job_name, 2)



    # Doesn't need to be a instance method, just making sure executor can call instance methods
    def get_detections_from_video(self, video_job):
        logger.info('[%s] Received video job: %s', video_job.job_name, video_job)
        if video_job.feed_forward_track is not None:
            return [video_job.feed_forward_track]

        echo_job, echo_media = self.get_echo_msgs(video_job)

        track1 = mpf.VideoTrack(0, 1)
        track1.frame_locations[0] = mpf.ImageLocation(1, 2, 3, 4, -1,
                                                      {'METADATA': 'test', 'ECHO_JOB': echo_job,
                                                       'ECHO_MEDIA': echo_media})

        track1.frame_locations[1] = mpf.ImageLocation(5, 6, 7, 8, -1)
        track1.frame_locations[1].detection_properties['ECHO_JOB'] = echo_job
        track1.frame_locations[1].detection_properties['ECHO_MEDIA'] = echo_media
        track1.detection_properties.update(video_job.job_properties)
        track1.detection_properties.update(video_job.media_properties)

        track2 = mpf.VideoTrack(
            3, 4, -1,
            {3: mpf.ImageLocation(9, 10, 11, 12, -1, dict(ECHO_JOB=echo_job, ECHO_MEDIA=echo_media))},
            dict(ECHO_JOB=echo_job, ECHO_MEDIA=echo_media))
        # Make sure regular collections are accepted
        return [track1, track2]



    @classmethod  # Doesn't need to be a class method, just making sure executor can call class methods
    def get_detections_from_audio(cls, audio_job):
        logger.info('[%s] Received audio job: %s', audio_job.job_name, audio_job)
        if audio_job.feed_forward_track is not None:
            return audio_job.feed_forward_track,
        echo_job, echo_media = cls.get_echo_msgs(audio_job)
        detection_properties = dict(ECHO_JOB=echo_job, ECHO_MEDIA=echo_media)

        track1 = mpf.AudioTrack(0, 10, .75, detection_properties)
        # Make sure multiple return values are accepted
        return track1, mpf.AudioTrack(10, 20, 1, detection_properties)

    @staticmethod
    def get_echo_msgs(job):
        #  Make sure properties get converted between C++ and Python properly
        return (job.job_properties.get('ECHO_JOB', 'echo_job not present'),
                job.media_properties.get('ECHO_MEDIA', 'echo_media not present'))


# The component executor looks for a module level variable named EXPORT_MPF_COMPONENT and calls it to create a
# instance of the component that will be executed. EXPORT_MPF_COMPONENT will normally be assigned to a class as below,
# but any callable can be used as long as it returns an instance of the component.
# Note that calling a class produces an instance of that class.
EXPORT_MPF_COMPONENT = TestComponent



def run_component_test():
    tc = EXPORT_MPF_COMPONENT()
    job = mpf.ImageJob('Test Job', '/home/mpf/sample-data/dog.jpg', {'job_prop1': 'job_val1'},
                       {'media_prop1': 'media_val1'}, None)

    logger.info('About to call get_detections_from_image')
    results = list(tc.get_detections_from_image(job))
    logger.info('get_detections_from_image found: %s detections', len(results))
    logger.info('get_detections_from_image results: %s', results)



if __name__ == '__main__':
    run_component_test()
