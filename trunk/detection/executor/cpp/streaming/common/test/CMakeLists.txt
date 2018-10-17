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

project(executor-utils-tests)
set(CMAKE_CXX_STANDARD 11)

set(ExecutorUtilsSrcs ${COMMON_SOURCE_DIR}/src/ExecutorUtils.cpp
                      ${COMMON_SOURCE_DIR}/src/ExecutorErrors.cpp
                      ${COMMON_SOURCE_DIR}/src/JobSettings.cpp)


find_package(GTest)
if (${GTEST_FOUND})
    enable_testing()

    add_executable(StreamingExecutorUtilsTest test_executor_utils.cpp ${ExecutorUtilsSrcs})

    target_include_directories(StreamingExecutorUtilsTest PUBLIC
	$<TARGET_PROPERTY:mpfDetectionComponentApi,INTERFACE_INCLUDE_DIRECTORIES>)

    target_link_libraries(StreamingExecutorUtilsTest GTest::GTest GTest::Main ${LOG4CXX_LIBRARIES})

    add_test(NAME StreamingExecutorUtilsTest COMMAND StreamingExecutorUtilsTest)

endif()
