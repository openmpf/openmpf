#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2016 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2016 The MITRE Corporation                                      #
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

project(DetectionTestUtils)

# Need special flags for the code coverage.
# MESSAGE("Changing CMAKE_CXX_FLAGS for GTests")
# set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g -fprofile-arcs -ftest-coverage -fPIC -O0")

# Build testing utility class
add_library(detection_test_utils SHARED read_detections_from_file.cpp write_detections_to_file.cpp detection_comparison.cpp video_generation.cpp image_generation.cpp)
target_link_libraries(detection_test_utils ${OpenCV_LIBS})

# Adding this so that the project opens in cLion.
include(${CMAKE_BINARY_DIR}/detection/detectionUtils/src/detectionUtils-exports.cmake OPTIONAL)

# Export the test_utils library so other component tests can find it
export(TARGETS detection_test_utils FILE detectionTestUtils-exports.cmake)
