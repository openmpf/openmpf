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

#project(amq_detection_common)

#aux_source_directory(. SRC)

## Find Qt
include(${QT_USE_FILE})
add_definitions(${QT_DEFINITIONS})

## Find ProtoBufs
#include_directories(${PROTOBUF_INCLUDE_DIRS})
#include_directories(${MPF_PROTO_PATH})

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

#include_directories(${MPF_PROTO_PATH})


if(DEFINED ENV{MPF_SDK_INSTALL_PATH})
	list(APPEND CMAKE_PREFIX_PATH $ENV{MPF_SDK_INSTALL_PATH}/lib/cmake)
endif()
list(APPEND CMAKE_PREFIX_PATH $ENV{HOME}/mpf-sdk-install/lib/cmake)
find_package(mpfComponentInterface REQUIRED)
find_package(mpfDetectionComponentApi REQUIRED)

include_directories(include)

file(GLOB srcs src/*.cpp)

add_library(mpfStreamMsg ${srcs})

# add_executable(amq_detection_component ${SRC})
target_link_libraries(mpfStreamMsg mpfComponentInterface ${ACTIVEMQ-CPP_LIBRARIES})

include_directories($<TARGET_PROPERTY:mpfDetectionComponentApi,INTERFACE_INCLUDE_DIRECTORIES>)

 install(TARGETS mpfStreamMsg
                 LIBRARY DESTINATION lib)

# install(FILES Log4cxxConfig.xml DESTINATION config)
