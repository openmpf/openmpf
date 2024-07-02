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

project(cpp-executors-common)

set(cpp_executors_common_files
    AmqMessageType.h
    BasicMessenger.cpp BasicMessenger.h
    LoggerWrapper.cpp LoggerWrapper.h
    ILogger.h
    PythonLogger.cpp PythonLogger.h
    PythonUtil.cpp PythonUtil.h
    PythonBase.cpp PythonBase.h
    PythonScopedSignalHandler.cpp PythonScopedSignalHandler.h
    ExecutorUtil.cpp ExecutorUtil.h
    ComponentLoadError.h
    QuitReceived.h
    Shutdown.cpp Shutdown.h
    ExitSignalBlocker.cpp ExitSignalBlocker.h
)

find_library(amq_lib libactivemq-cpp.a)

find_package(PkgConfig REQUIRED)
pkg_search_module(ACTIVEMQ-CPP REQUIRED activemq-cpp)

find_library(apr_lib apr-1)

add_library(cpp_executors_common OBJECT ${cpp_executors_common_files})

target_include_directories(cpp_executors_common PUBLIC
    ${ACTIVEMQ-CPP_INCLUDE_DIRS} ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(cpp_executors_common PUBLIC
    ${amq_lib} ${apr_lib} pthread ssl crypto
    pybind11::embed)
