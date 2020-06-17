#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2020 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2020 The MITRE Corporation                                      #
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


import mpf_component_api as mpf


# logger = mpf.configure_logging('python-generic-test.log', __name__ == '__main__')
logger = mpf.configure_logging('python-generic-test.log', True)


class GenericTestComponent(object):
    detection_type = 'TEST GENERIC DETECTION TYPE'


    @staticmethod
    def get_detections_from_generic(generic_job):
        logger.info('[%s] Received generic job: %s', generic_job.job_name, generic_job)
        if generic_job.feed_forward_track is not None:
            return generic_job.feed_forward_track,

        echo_job, echo_media = GenericTestComponent.get_echo_msgs(generic_job)
        properties = dict(ECHO_JOB=echo_job, ECHO_MEDIA=echo_media)
        return mpf.GenericTrack(1, properties), mpf.GenericTrack(2, properties)



    @staticmethod
    def get_echo_msgs(job):
        #  Make sure properties get converted between C++ and Python properly
        return (job.job_properties.get('ECHO_JOB', 'echo_job not present'),
                job.media_properties.get('ECHO_MEDIA', 'echo_media not present'))


EXPORT_MPF_COMPONENT = GenericTestComponent
