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
project(MPF_PROTOBUF)

## Find ProtoBufs
# When performing non-Docker builds, we must account for how the old build guide told developers
# to install libraries in non-standard locations under /apps.
if (EXISTS /apps/install/include/google/protobuf)
    set(Protobuf_INCLUDE_DIR /apps/install/include)
endif()
if (EXISTS /apps/install/lib/libprotobuf.a)
    set(Protobuf_LIBRARY /apps/install/lib/libprotobuf.a)
elseif(EXISTS /usr/lib64/libprotobuf.a)
    set(Protobuf_LIBRARY /usr/lib64/libprotobuf.a)
endif()
find_package(Protobuf REQUIRED)

file(GLOB PROTO_FILES "${CMAKE_SOURCE_DIR}/protobuf/src/main/proto/*.proto")

# build protobuf cpp files
# this puts the generated files under build/protobufs
PROTOBUF_GENERATE_CPP(MPF_PROTO_SRCS MPF_PROTO_HEADERS ${PROTO_FILES})

# Tell cmake these will exist at build time
set_source_files_properties(${MPF_PROTO_HEADERS} PROPERTIES GENERATED TRUE)

add_library(mpfProtobufs STATIC ${MPF_PROTO_SRCS})
target_link_libraries(mpfProtobufs ${Protobuf_LIBRARY})

# include ourselves for building
target_include_directories(mpfProtobufs PUBLIC ${CMAKE_CURRENT_BINARY_DIR})

