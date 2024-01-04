#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2023 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2023 The MITRE Corporation                                      #
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

project(amq_detection_common)

## Find and install activemq-cpp
find_package(PkgConfig REQUIRED)
set(ENV{PKG_CONFIG_PATH} "$ENV{PKG_CONFIG_PATH}:/usr/local/lib/pkgconfig")
pkg_search_module(ACTIVEMQ-CPP REQUIRED activemq-cpp)
include_directories(${ACTIVEMQ-CPP_INCLUDE_DIRS})

file(GLOB MATCHING_FILES
	 ${ACTIVEMQ-CPP_LIBDIR}/libactivemq-cpp.so* )
install(FILES ${MATCHING_FILES} DESTINATION lib)

## Find Apache Portable Runtime
pkg_search_module(APR REQUIRED apr-1)
include_directories(${APR_INCLUDEDIR})

## Install Apache Portable Runtime
file(GLOB MATCHING_FILES ${APR_LIBDIR}/libapr*.so* )
install(FILES ${MATCHING_FILES} DESTINATION lib)


if(DEFINED ENV{MPF_SDK_INSTALL_PATH})
	list(APPEND CMAKE_PREFIX_PATH $ENV{MPF_SDK_INSTALL_PATH}/lib/cmake)
endif()
list(APPEND CMAKE_PREFIX_PATH $ENV{HOME}/mpf-sdk-install/lib/cmake)
find_package(mpfComponentInterface REQUIRED)
find_package(mpfDetectionComponentApi REQUIRED)

include_directories(${MSG_UTILS_DIR})

set(batch_executor_lib_files
	ComponentLoadError.h
	JobReceiver.h JobReceiver.cpp
	Messenger.h Messenger.cpp
	ProtobufRequestUtil.h ProtobufRequestUtil.cpp
	ProtobufResponseUtil.h ProtobufResponseUtil.cpp
	JobContext.h
	CppComponentHandle.cpp CppComponentHandle.h
	PythonComponentHandle.cpp PythonComponentHandle.h
	BatchExecutorUtil.cpp BatchExecutorUtil.h
	PythonComponentHandle.cpp PythonComponentHandle.h
	LoggerWrapper.h LoggerWrapper.cpp
	HealthCheck.h HealthCheck.cpp)

add_library(batch_executor_lib OBJECT ${batch_executor_lib_files} ${MSG_UTILS_FILE})
target_include_directories(batch_executor_lib PUBLIC
	$<TARGET_PROPERTY:mpfDetectionComponentApi,INTERFACE_INCLUDE_DIRECTORIES>)

target_link_libraries(batch_executor_lib PUBLIC mpfProtobufs
	pybind11::embed ${LOG4CXX_LIBRARIES} ${ACTIVEMQ-CPP_LIBRARIES})


add_executable(amq_detection_component main.cpp)
target_link_libraries(amq_detection_component PUBLIC batch_executor_lib)

install(TARGETS amq_detection_component
                RUNTIME DESTINATION bin
                LIBRARY DESTINATION lib
                ARCHIVE DESTINATION lib)

install(FILES Log4cxxConfig.xml DESTINATION config)

if(DEFINED ENV{MPF_SDK_INSTALL_PATH})
	install(DIRECTORY $ENV{MPF_SDK_INSTALL_PATH}/python/wheelhouse/ DESTINATION python/wheelhouse)
else()
	install(DIRECTORY $ENV{HOME}/mpf-sdk-install/python/wheelhouse/ DESTINATION python/wheelhouse)
endif()

add_subdirectory(test)
