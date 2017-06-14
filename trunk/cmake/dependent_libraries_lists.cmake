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

list(APPEND OpenCV_CORE_LIBS opencv_core opencv_imgcodecs opencv_imgproc opencv_videoio)

# OpenCV uses ffmpeg, gstreamer, and other libraries for codecs and containers
list(APPEND MediaFiles_NEEDED avcodec avdevice avformat avutil swscale avresample
                              avfilter postproc swresample jasper
                              tiff va xvidcore x264 x265 vorbisenc vorbis
                              theoraenc schroedinger-1.0 mp3lame soxr ogg gtk-3 gdk-3
                              cairo-gobject gstbase-1.0 gstreamer-1.0 gobject-2.0
                              gmodule-2.0 gstvideo-1.0 gstapp-1.0 gstriff-1.0 vpx png15
                              gstaudio-1.0 gsttag-1.0 gstpbutils-1.0 atk-bridge-2.0
                              speex opus gsm openjpeg atspi opencore-amrwb opencore-amrnb)

list(APPEND CMAKE_FIND_LIBRARY_SUFFIXES .so.0 .so.1)
find_package(OpenCV 3.2 EXACT REQUIRED PATHS /opt/opencv-3.2.0)

# Use the following function if the project being built needs to find and install
# the OpenCV libraries, and the ffmpeg/gstreamer libraries that OpenCV uses.  All of the
# core C++ detection components use OpenCV, at a minimum for opening
# video files and grabbing frames. The component_install_dir argument tells cmake where
# to install things, which may be different for each component. The libs_list argument
# gives the specific list of OpenCV libraries that need to be installed for a given
# component.  This list should be the same as the list that is added to the
# target_link_libraries command when building the component.

function(install_opencv component_install_dir libs_list)
   set(CMAKE_PREFIX_PATH /apps/install /lib64 /usr/lib64)

   foreach(module ${libs_list})

      file(GLOB_RECURSE MATCHING_FILES ${OpenCV_DIR}/../../lib/*${module}.so*)
      install(FILES ${MATCHING_FILES} DESTINATION ${component_install_dir})

   endforeach(module)

   foreach(lib ${MediaFiles_NEEDED})

      find_library(FF${lib} ${lib} PATHS /apps/install/lib /lib64 /usr/lib64 ENV)
# Make a list to pass to the target_link_libraries command
      list(APPEND MediaFiles_LINK ${FF${lib}})
      file(GLOB FFLIBFILES ${FF${lib}}*)
      install(FILES ${FFLIBFILES} DESTINATION ${component_install_dir})

   endforeach(lib)
endfunction(install_opencv)
