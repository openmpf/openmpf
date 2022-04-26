#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2021 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2021 The MITRE Corporation                                      #
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

# This project is not intended to be built on its own. Instead, build the parent project using
# openmpf-projects/openmpf/trunk/CMakeLists.txt.

project(VideoOverlay)
aux_source_directory(./src/main/cpp SRC)

find_package(OpenCV 4.5.5 EXACT REQUIRED PATHS /opt/opencv-4.5.5 COMPONENTS opencv_core opencv_freetype)

find_package(mpfComponentInterface REQUIRED)
find_package(mpfDetectionComponentApi REQUIRED)


include_directories(${JNI_INCLUDE_DIRS} ${LOG4CXX_LIBS} ${OpenCV_INCLUDE_DIRS})
add_library(mpfopencvjni SHARED ${SRC})

set(JNI_LIBRARIES_WITHOUT_AWT ${JNI_LIBRARIES})
list(REMOVE_AT JNI_LIBRARIES_WITHOUT_AWT 0)

target_link_libraries(mpfopencvjni ${OpenCV_LIBS} ${JNI_LIBRARIES_WITHOUT_AWT} ${Log_LIBS} heif
    mpfDetectionComponentApi)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET mpfopencvjni POST_BUILD
        COMMAND ${CMAKE_COMMAND} -D TARGET_BINARY_LOCATION="$<TARGET_FILE:mpfopencvjni>"
        -D DEP_LIBS_INSTALL_LOCATION="${CMAKE_INSTALL_PREFIX}/lib"
        -P ${CopySharedLibDependencies_LOCATION})
endif()

install(TARGETS mpfopencvjni
        RUNTIME DESTINATION bin
        LIBRARY DESTINATION lib
        ARCHIVE DESTINATION lib)
