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
project(MPF_PROTOBUF)

# In our Dockerfiles, we compile a static version of libprotobuf. Video overlay depends on
# mpfProtobufs and is a shared library. In order for a shared library to link to a static library,
# position independent code must be enabled.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# https://github.com/protocolbuffers/protobuf/blob/main/docs/cmake_protobuf_generate.md
find_package(protobuf CONFIG REQUIRED)

add_library(
    mpfProtobufs OBJECT
    src/main/proto/detection.proto
    src/main/proto/markup.proto
    src/main/proto/subject.proto)

target_link_libraries(mpfProtobufs PUBLIC protobuf::libprotobuf)

target_include_directories(mpfProtobufs PUBLIC ${CMAKE_CURRENT_BINARY_DIR})

protobuf_generate(TARGET mpfProtobufs
    IMPORT_DIRS src/main/proto
    LANGUAGE python
    PLUGIN_OPTIONS pyi_out
    PROTOC_OUT_DIR ${CMAKE_SOURCE_DIR}/subject-tracking/executor/python/mpf_subject_executor/mpf_proto)

protobuf_generate(TARGET mpfProtobufs IMPORT_DIRS src/main/proto LANGUAGE cpp)
