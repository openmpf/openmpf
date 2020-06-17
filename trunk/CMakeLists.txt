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

project(MPF)
cmake_minimum_required(VERSION 3.6)
cmake_policy(SET CMP0015 NEW)

#add_definitions(-Wno-cpp)
ADD_DEFINITIONS("-std=c++0x")

set(FIND_LIBRARY_USE_LIB64_PATHS true)

MESSAGE("CURRENT CMAKE CXX vars")
# if empty or "Debug", the "-g" flag will be added
if(CMAKE_BUILD_TYPE STREQUAL "" OR CMAKE_BUILD_TYPE STREQUAL "Debug")
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g")
	MESSAGE("Configuring as DEBUG build")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
    MESSAGE("Configuring as RELEASE build")
endif()


if(DEFINED ENV{MPF_SDK_INSTALL_PATH})
    list(APPEND CMAKE_PREFIX_PATH $ENV{MPF_SDK_INSTALL_PATH}/lib/cmake)
endif()

list(APPEND CMAKE_PREFIX_PATH $ENV{HOME}/mpf-sdk-install/lib/cmake)

find_package(mpfCMakeHelpers REQUIRED)


# Find JNI
find_package(JNI)


# Build ProtoBufs
add_subdirectory(protobuf)


find_package(PkgConfig)
pkg_search_module(LOG4CXX REQUIRED liblog4cxx)

# Find and install the log4cxx libs
file(GLOB MATCHING_FILES ${LOG4CXX_LIBDIR}/liblog4cxx${CMAKE_SHARED_LIBRARY_SUFFIX}* )
install(FILES ${MATCHING_FILES} DESTINATION "lib")


# Find and install the FFmpeg libs
find_program(FFMPEG_BINARY_PATH NAMES ffmpeg)
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    execute_process(COMMAND ${CMAKE_COMMAND} -D TARGET_BINARY_LOCATION=${FFMPEG_BINARY_PATH}
       -D DEP_LIBS_INSTALL_LOCATION=${CMAKE_INSTALL_PREFIX}/lib
       -P ${CopySharedLibDependencies_LOCATION})
endif()

# The C++ component executables for both streaming and batch jobs use a common
# set of functions to translate between MPF data types and corresponding protobuf
# types. These shared functions are held in the common/MPFMessageUtils.cpp file.
set(MSG_UTILS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/detection/executor/cpp/common)
set(MSG_UTILS_FILE ${MSG_UTILS_DIR}/MPFMessageUtils.cpp)

# Build the detection component executable for batch jobs
add_subdirectory(detection/executor/cpp/batch)
# Build the executables for streaming jobs
add_subdirectory(detection/executor/cpp/streaming)

# Build the video overlay utility
add_subdirectory(video-overlay)


