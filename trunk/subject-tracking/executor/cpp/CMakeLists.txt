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
cmake_minimum_required(VERSION 3.6)

project(subject-tracking-executor)
set(CMAKE_CXX_STANDARD 17)

set(subject_tracking_files
    main.cpp
    PythonComponent.cpp PythonComponent.h
    ComponentRegistration.cpp ComponentRegistration.h
    JobReceiver.cpp JobReceiver.h
    JobContext.h
    ExecutorConfig.cpp ExecutorConfig.h
    ComponentExecutor.cpp ComponentExecutor.h
)

add_executable(subject_tracking_executor ${subject_tracking_files})
target_link_libraries(subject_tracking_executor cpp_executors_common mpfProtobufs pybind11::embed)

install(TARGETS subject_tracking_executor RUNTIME DESTINATION bin)
