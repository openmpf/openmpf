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
cmake_minimum_required(VERSION 3.6)
set(CMAKE_CXX_STANDARD 11)

## Find and install activemq-cpp
find_package(PkgConfig)
set(ENV{PKG_CONFIG_PATH} "$ENV{PKG_CONFIG_PATH}:/usr/local/lib/pkgconfig")
pkg_search_module(ACTIVEMQ-CPP REQUIRED activemq-cpp)
include_directories(${ACTIVEMQ-CPP_INCLUDE_DIRS})

file(GLOB MATCHING_FILES
	 ${ACTIVEMQ-CPP_LIBRARY_DIRS}/libactivemq-cpp.so* )
install(FILES ${MATCHING_FILES} DESTINATION lib)

## Find Apache Portable Runtime
set(ENV{PKG_CONFIG_PATH} "$ENV{PKG_CONFIG_PATH}:/usr/local/apr/lib/pkgconfig")
pkg_search_module(APR REQUIRED apr-1)
include_directories(${APR_INCLUDEDIR})

## Install Apache Portable Runtime
file(GLOB MATCHING_FILES ${APR_LIBDIR}/libapr*.so* )
install(FILES ${MATCHING_FILES} DESTINATION lib)

include_directories(${MPF_PROTO_PATH})

find_package(OpenCV 3.3 EXACT REQUIRED PATHS /opt/opencv-3.3.0
    COMPONENTS opencv_core opencv_imgcodecs opencv_imgproc opencv_videoio)


if(DEFINED ENV{MPF_SDK_INSTALL_PATH})
	list(APPEND CMAKE_PREFIX_PATH $ENV{MPF_SDK_INSTALL_PATH}/lib/cmake)
endif()
list(APPEND CMAKE_PREFIX_PATH $ENV{HOME}/mpf-sdk-install/lib/cmake)
find_package(mpfComponentInterface REQUIRED)
find_package(mpfDetectionComponentApi REQUIRED)

#include_directories(${MSG_UTILS_DIR})
#include_directories(MessageUtils)
#add_subdirectory(MessageUtils)

#include_directories(FrameStore)
#add_subdirectory(FrameReader)

set(streaming_component_executor_files
	StreamingComponentExecutor.cpp StreamingComponentExecutor.h
    StandardInWatcher.cpp StandardInWatcher.h
    JobSettings.h JobSettings.cpp
	StreamingComponentHandle.cpp StreamingComponentHandle.h
    BasicAmqMessageSender.cpp BasicAmqMessageSender.h
	StreamingVideoCapture.cpp StreamingVideoCapture.h
	ExecutorErrors.cpp ExecutorErrors.h
	ExecutorUtils.cpp ExecutorUtils.h)

add_library(streaming_executor_lib OBJECT ${streaming_component_executor_files})
target_include_directories(streaming_executor_lib PUBLIC $<TARGET_PROPERTY:mpfDetectionComponentApi,INTERFACE_INCLUDE_DIRECTORIES>)

add_executable(streaming_component_executor main.cpp $<TARGET_OBJECTS:streaming_executor_lib> ${MSG_UTILS_FILE})
target_link_libraries(streaming_component_executor mpfDetectionComponentApi mpfComponentInterface
	dl ${LOG4CXX_LIBRARIES} ${OpenCV_LIBS} ${PROTOBUF_LIBRARY} ${MPF_PROTOBUF_LIB} ${ACTIVEMQ-CPP_LIBRARIES})

install(TARGETS streaming_component_executor
	RUNTIME DESTINATION bin
	LIBRARY DESTINATION lib
	ARCHIVE DESTINATION lib)

install(FILES StreamingExecutorLog4cxxConfig.xml DESTINATION config)

add_subdirectory(test)
