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

# This project is not intended to be built on its own. Instead, build the parent project using
# openmpf-projects/openmpf/trunk/CMakeLists.txt.

project(VideoOverlay)
set(CMAKE_CXX_STANDARD 17)
set(JNI_SRC_DIR ./src/main/cpp)
set(JNI_HELPER_FILES ${JNI_SRC_DIR}/JniHelper.h ${JNI_SRC_DIR}/JniHelper.cpp)

# Build the OpenCV-based JNI library used by artifact extraction.
set(OCV_JNI_SRC ${JNI_SRC_DIR}/BoundingBoxImageHandle.h ${JNI_SRC_DIR}/BoundingBoxImageHandle.cpp
                ${JNI_SRC_DIR}/BoundingBoxVideoHandle.h ${JNI_SRC_DIR}/BoundingBoxVideoHandle.cpp
                ${JNI_SRC_DIR}/BoundingBoxWriter.cpp
                ${JNI_SRC_DIR}/FrameExtractor.cpp
                ${JNI_SRC_DIR}/ResolutionConfig.h)
find_package(OpenCV 4.13.0 EXACT REQUIRED PATHS /opt/opencv-4.13.0 COMPONENTS opencv_core opencv_freetype)

find_package(mpfComponentInterface REQUIRED)
find_package(mpfDetectionComponentApi REQUIRED)

include_directories(${JAVA_INCLUDE_PATH} ${JAVA_INCLUDE_PATH2} ${OpenCV_INCLUDE_DIRS})

add_library(mpfopencvjni SHARED ${OCV_JNI_SRC} ${JNI_HELPER_FILES})
target_link_libraries(mpfopencvjni ${OpenCV_LIBS} ${JAVA_JVM_LIBRARY} mpfProtobufs
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

# Build the HEIF JNI library used by HeifConverter.
set(HEIF_JNI_SRC ${JNI_SRC_DIR}/HeifConverter.cpp)
find_package(PNG)
add_library(mpfheifjni SHARED ${HEIF_JNI_SRC} ${JNI_HELPER_FILES})
target_link_libraries(mpfheifjni heif heifio ${JAVA_JVM_LIBRARY} PNG::PNG)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET mpfheifjni POST_BUILD
        COMMAND ${CMAKE_COMMAND} -D TARGET_BINARY_LOCATION="$<TARGET_FILE:mpfheifjni>"
        -D DEP_LIBS_INSTALL_LOCATION="${CMAKE_INSTALL_PREFIX}/lib"
        -P ${CopySharedLibDependencies_LOCATION})
endif()

install(TARGETS mpfheifjni
        RUNTIME DESTINATION bin
        LIBRARY DESTINATION lib
        ARCHIVE DESTINATION lib)
