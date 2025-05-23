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

project(batch-executor-tests)
set(CMAKE_CXX_STANDARD 17)


find_package(GTest)
if (${GTEST_FOUND})
    enable_testing()

    add_executable(BatchExecutorTest test_batch_executor.cpp)
    target_link_libraries(BatchExecutorTest GTest::GTest GTest::Main batch_executor_lib)

    add_test(NAME BatchExecutorTest COMMAND BatchExecutorTest)
    file(COPY test_python_components/ DESTINATION test_python_components)
    file(CREATE_LINK
        ${CMAKE_CURRENT_SOURCE_DIR}/test-health-check.ini
        ${CMAKE_CURRENT_BINARY_DIR}/test-health-check.ini SYMBOLIC)

endif()
