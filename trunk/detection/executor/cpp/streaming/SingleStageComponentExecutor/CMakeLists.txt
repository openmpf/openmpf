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

message(STATUS "COMMON_SOURCE_DIR = ${COMMON_SOURCE_DIR}")
include_directories( ${COMMON_SOURCE_DIR}/include )
set(single_stage_component_executor_files
  StreamingComponentHandle.cpp StreamingComponentHandle.h
  SingleStageComponentExecutor.cpp SingleStageComponentExecutor.h)


add_library(single_stage_executor_lib OBJECT ${single_stage_component_executor_files})
target_include_directories(single_stage_executor_lib PUBLIC
	$<TARGET_PROPERTY:mpfDetectionComponentApi,INTERFACE_INCLUDE_DIRECTORIES>
	$<TARGET_PROPERTY:mpfProtobufs,INTERFACE_INCLUDE_DIRECTORIES>)

add_executable(single_stage_component_executor main.cpp $<TARGET_OBJECTS:single_stage_executor_lib> $<TARGET_OBJECTS:common> $<TARGET_OBJECTS:mpfStreamMsg>)
target_link_libraries(single_stage_component_executor mpfDetectionComponentApi mpfComponentInterface
        dl ${LOG4CXX_LIBRARIES} ${OpenCV_LIBS} mpfProtobufs ${ACTIVEMQ-CPP_LIBRARIES})

install(TARGETS single_stage_component_executor
	RUNTIME DESTINATION bin
	LIBRARY DESTINATION lib
	ARCHIVE DESTINATION lib)

install(FILES SingleStageExecutorLog4cxxConfig.xml DESTINATION config)

#add_subdirectory(test)
