#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2018 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2018 The MITRE Corporation                                      #
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

# TODO: Remove this file once Python SDK is available

import collections
import logging
import sys
import mpf_component_api_util as util


class Properties(util.TypedDict):
    key_type = str
    value_type = str


@util.FieldTypes(x_left_upper=int, y_left_upper=int, width=int, height=int, confidence=float,
                 detection_properties=Properties)
class ImageLocation(object):
    def __init__(self, x_left_upper, y_left_upper, width, height, confidence=-1.0, detection_properties=None):
        self.x_left_upper = x_left_upper
        self.y_left_upper = y_left_upper
        self.width = width
        self.height = height
        self.confidence = confidence
        self.detection_properties = util.create_if_none(detection_properties, Properties)


class FrameLocationMap(util.TypedDict):
    key_type = int
    value_type = ImageLocation


@util.FieldTypes(start_frame=int, stop_frame=int, confidence=float, frame_locations=FrameLocationMap,
                 detection_properties=Properties)
class VideoTrack(object):
    def __init__(self, start_frame, stop_frame, confidence=-1.0, frame_locations=None, detection_properties=None):
        self.start_frame = start_frame
        self.stop_frame = stop_frame
        self.confidence = confidence
        self.frame_locations = util.create_if_none(frame_locations, FrameLocationMap)
        self.detection_properties = util.create_if_none(detection_properties, Properties)


@util.FieldTypes(start_time=int, stop_time=int, confidence=float, detection_properties=Properties)
class AudioTrack(object):
    def __init__(self, start_time, stop_time, confidence, detection_properties=None):
        self.start_time = start_time
        self.stop_time = stop_time
        self.confidence = confidence
        self.detection_properties = util.create_if_none(detection_properties, Properties)


@util.FieldTypes(confidence=float, detection_properties=Properties)
class GenericTrack(object):
    def __init__(self, confidence=-1.0, detection_properties=None):
        self.confidence = confidence
        self.detection_properties = util.create_if_none(detection_properties, Properties)



VideoJob = collections.namedtuple('VideoJob', ('job_name', 'data_uri', 'start_frame', 'stop_frame',
                                               'job_properties', 'media_properties', 'feed_forward_track'))

ImageJob = collections.namedtuple('ImageJob', ('job_name', 'data_uri', 'job_properties', 'media_properties',
                                               'feed_forward_location'))

AudioJob = collections.namedtuple('AudioJob', ('job_name', 'data_uri', 'start_time', 'stop_time',
                                               'job_properties', 'media_properties', 'feed_forward_track'))

GenericJob = collections.namedtuple('GenericJob', ('job_name', 'data_uri', 'job_properties', 'media_properties',
                                                   'feed_forward_track'))


class DetectionError(util.EnumBase):
    DETECTION_SUCCESS, \
        OTHER_DETECTION_ERROR_TYPE, \
        DETECTION_NOT_INITIALIZED, \
        UNRECOGNIZED_DATA_TYPE, \
        UNSUPPORTED_DATA_TYPE, \
        INVALID_DATAFILE_URI, \
        COULD_NOT_OPEN_DATAFILE, \
        COULD_NOT_READ_DATAFILE, \
        FILE_WRITE_ERROR, \
        IMAGE_READ_ERROR, \
        BAD_FRAME_SIZE, \
        BOUNDING_BOX_SIZE_ERROR, \
        INVALID_FRAME_INTERVAL, \
        INVALID_START_FRAME, \
        INVALID_STOP_FRAME, \
        DETECTION_FAILED, \
        DETECTION_TRACKING_FAILED, \
        INVALID_PROPERTY, \
        MISSING_PROPERTY, \
        PROPERTY_IS_NOT_INT, \
        PROPERTY_IS_NOT_FLOAT, \
        INVALID_ROTATION, \
        MEMORY_ALLOCATION_FAILED, \
        GPU_ERROR = util.EnumBase.element_count(24)


class DetectionException(Exception):
    def __init__(self, message, error_code=DetectionError.OTHER_DETECTION_ERROR_TYPE, *args):
        super(DetectionException, self).__init__(message, error_code, *args)
        if isinstance(error_code, DetectionError):
            self.error_code = error_code
        else:
            self.error_code = DetectionError.OTHER_DETECTION_ERROR_TYPE



def configure_logging(log_file_name, debug=False):
    # Change default level names to match what WFM expects
    # Change default level name for logger.warn and logger.warning from 'WARNING' to 'WARN'
    logging.addLevelName(logging.WARN, 'WARN')
    # Change default level name for logger.fatal and logger.critical from 'CRITICAL' to 'FATAL'
    logging.addLevelName(logging.FATAL, 'FATAL')

    logger = logging.getLogger(util.get_log_name(log_file_name))
    logger.propagate = False
    if debug:
        logger.setLevel(logging.DEBUG)
        handler = logging.StreamHandler(sys.stdout)
    else:
        logger.setLevel(logging.INFO)
        handler = logging.FileHandler(util.get_full_log_path(log_file_name))
    handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)-5s [%(filename)s:%(lineno)d] - %(message)s'))
    logger.addHandler(handler)
    return logger


def result_struct_test():
    # p = Properties(hello=5)
    # print p
    # il = ImageLocation(1, 1, 2, 3, 4, Properties(hello='world'))
    il = ImageLocation(1, 1, 2, 3, 4, {'hello': 'world'})
    il.x_left_upper = 40
    print il
    print il.detection_properties
    print str(il)

    print '--'
    # vt = VideoTrack(0, 10, -1, {1: il, 2: 'hello'})
    vt = VideoTrack(0, 10, -1, {1: il})
    vt.frame_locations[2] = ImageLocation(10, 10, 2, 3, 4, {'hello': 'world'})
    vt.detection_properties['class'] = 'person'
    print vt



if __name__ == '__main__':
    print list(DetectionError)
    result_struct_test()



