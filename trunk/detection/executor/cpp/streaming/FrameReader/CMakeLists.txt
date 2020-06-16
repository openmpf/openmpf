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
cmake_minimum_required(VERSION 3.6)

if(DEFINED ENV{MPF_SDK_INSTALL_PATH})
	list(APPEND CMAKE_PREFIX_PATH $ENV{MPF_SDK_INSTALL_PATH}/lib/cmake)
endif()
list(APPEND CMAKE_PREFIX_PATH $ENV{HOME}/mpf-sdk-install/lib/cmake)
find_package(mpfComponentInterface REQUIRED)
find_package(mpfDetectionComponentApi REQUIRED)

include_directories($<TARGET_PROPERTY:mpfDetectionComponentApi,INTERFACE_INCLUDE_DIRECTORIES>)
include_directories(${MSG_UTILS_DIR})

add_executable(frame_reader_component main)
target_link_libraries(frame_reader_component mpfStreamMsg ${LOG4CXX_LIBRARIES} ${ACTIVEMQ-CPP_LIBRARIES} ${QT_LIBRARIES})

install(TARGETS frame_reader_component
                RUNTIME DESTINATION bin
                LIBRARY DESTINATION lib
                ARCHIVE DESTINATION lib)

install(FILES Log4cxx-framereader-Config.xml framereader.ini DESTINATION config)
