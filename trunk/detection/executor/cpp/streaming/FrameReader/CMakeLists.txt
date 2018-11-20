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

set(frame_reader_executor_files main.cpp
    OcvFrameReader.h OcvFrameReader.cpp)

add_executable(frame_reader_component ${frame_reader_executor_files} $<TARGET_OBJECTS:common>)
target_include_directories(frame_reader_component PUBLIC
	$<TARGET_PROPERTY:mpfDetectionComponentApi,INTERFACE_INCLUDE_DIRECTORIES>)
target_link_libraries(frame_reader_component mpfDetectionComponentApi mpfProtobufs
        ${LOG4CXX_LIBRARIES} ${ACTIVEMQ-CPP_LIBRARIES} ${HIREDIS_LIB})

install(TARGETS frame_reader_component
                RUNTIME DESTINATION bin
                LIBRARY DESTINATION lib
                ARCHIVE DESTINATION lib)

install(FILES Log4cxx-framereader-Config.xml DESTINATION config)
