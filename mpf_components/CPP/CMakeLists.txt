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

project(MPF_CPP_Components)
cmake_minimum_required(VERSION 2.8)

# TODO: Do we still need this policy?  Do we use any relative paths when linking?
cmake_policy(SET CMP0015 NEW)

ADD_DEFINITIONS("-std=c++0x")

set(FIND_LIBRARY_USE_LIB64_PATHS true)

# if empty or "Debug", the "-g" flag will be added
if(CMAKE_BUILD_TYPE STREQUAL "" OR CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g")
    MESSAGE("Configuring as DEBUG build")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
    MESSAGE("Configuring as RELEASE build")
endif()

set(MPF_BASE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../trunk)
message(STATUS "MPF_BASE_DIR = ${MPF_BASE_DIR}")

set(MPF_COMPONENT_INTERFACE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/componentInterface/include)
message(STATUS "MPF_COMPONENT_INTERFACE_DIR = ${MPF_COMPONENT_INTERFACE_DIR}")

set(MPF_COMPONENT_INSTALL_DIR ${MPF_BASE_DIR}/install/plugins)
message(STATUS "MPF_COMPONENT_INSTALL_DIR = ${MPF_COMPONENT_INSTALL_DIR}")

set(CMAKE_INSTALL_PREFIX ${MPF_COMPONENT_INSTALL_DIR})
message(STATUS "CMAKE_INSTALL_PREFIX = ${CMAKE_INSTALL_PREFIX}")

# Use the following macro if the project being built needs to find and install
# the OpenCV 2.4.9 libraries, and the ffmpeg that it depends on.  All of the
# C++ detection components use OpenCV 2.4.9 (except the caffe component, which
# uses OpenCV 3.1), at a minimum for opening video files and grabbing frames.
# The component_install_dir tells cmake where to install things, which may be
# different for each component.

macro(install_opencv component_install_dir)
    unset(OpenCV_CONFIG_PATH CACHE)
    find_package(OpenCV 2.4.9 EXACT REQUIRED)

   foreach(module
	 opencv_calib3d opencv_contrib opencv_core opencv_features2d opencv_ffmpeg
	 opencv_flann opencv_gpu opencv_highgui opencv_imgproc opencv_legacy opencv_ml
	 opencv_nonfree opencv_objdetect opencv_ocl opencv_photo opencv_stitching
	 opencv_superres opencv_video opencv_videostab)

            file(GLOB_RECURSE MATCHING_FILES ${OpenCV_DIR}/../../lib/*${module}*)
            install(FILES ${MATCHING_FILES} DESTINATION ${component_install_dir})

   endforeach(module)

# OpenCV uses ffmpeg for codecs and containers

   foreach(lib
         avcodec avformat avutil mp3lame ogg opencore-amrnb opencore-amrwb swscale
	 theora	theoraenc theoradec vorbis vorbisenc vorbisfile	x264)

            find_library(FF${lib} ${lib} PATHS ENV)
            file(GLOB FFLIBFILES ${FF${lib}}*)
            install(FILES ${FFLIBFILES} DESTINATION ${component_install_dir})

   endforeach(lib)
endmacro(install_opencv)

# The qmake program needs to be on the path in order for find_package to work for
# the Qt package.
set(ENV{PATH} "$ENV{PATH}:/usr/local/Trolltech/Qt-4.8.5/bin")
find_package(Qt4 COMPONENTS QtCore REQUIRED)

# Use the following macro if the project being built needs to find and install
# the QtCore library.

macro(install_Qt component_install_dir modules)

   include(${QT_USE_FILE})
   set(QT_LIBS ${QT_LIBRARIES})
   add_definitions(${QT_DEFINITIONS})

   foreach(module ${modules})
     file(GLOB_RECURSE MATCHING_FILES
	 ${QT_LIBRARY_DIR}/*${module}${CMAKE_SHARED_LIBRARY_SUFFIX}*)
     install(FILES ${MATCHING_FILES} DESTINATION ${component_install_dir})
   endforeach(module)
endmacro(install_Qt)

find_package(PkgConfig)
pkg_search_module(LOG4CXX REQUIRED liblog4cxx)

# Use the following macro if the project being built needs to find and install the
# log4cxx package
macro(install_log4cxx component_install_dir)
   file(GLOB MATCHING_FILES ${LOG4CXX_LIBDIR}/liblog4cxx${CMAKE_SHARED_LIBRARY_SUFFIX}* )
   install(FILES ${MATCHING_FILES} DESTINATION  ${component_install_dir})
endmacro(install_log4cxx)

# Find google test
add_subdirectory(gtest-1.7.0)
enable_testing()
include_directories(${gtest_SOURCE_DIR}/include ${gtest_SOURCE_DIR})

include_directories(${MPF_COMPONENT_INTERFACE_DIR})

add_subdirectory(detection)
