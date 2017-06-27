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


cmake_minimum_required(VERSION 3.6)
project(cpp-hello-world-test-component)

set(CMAKE_CXX_STANDARD 11)

include(../ComponentSetup.cmake)

find_package(OpenCV 3.2 EXACT REQUIRED PATHS /opt/opencv-3.2.0)
find_package(mpfComponentInterface REQUIRED)
find_package(mpfDetectionComponentApi REQUIRED)


set(CPP_HELLO_WORLD_SOURCE_FILES HelloWorld.h HelloWorld.cpp)

add_library(mpfCplusplusHelloWorld SHARED ${CPP_HELLO_WORLD_SOURCE_FILES})
target_link_libraries(mpfCplusplusHelloWorld mpfComponentInterface mpfDetectionComponentApi)

configure_mpf_component(CplusplusHelloWorld mpfCplusplusHelloWorld)
