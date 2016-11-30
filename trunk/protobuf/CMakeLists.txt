#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2016 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2016 The MITRE Corporation                                      #
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

project(MFP_PROTOBUF)

## Find ProtoBufs
find_package(Protobuf REQUIRED)
include_directories(${PROTOBUF_INCLUDE_DIRS})

# Install Protobuf
get_filename_component(PROTOBUF_LIBDIR ${PROTOBUF_LIBRARY} DIRECTORY)
file(GLOB MATCHING_FILES ${PROTOBUF_LIBDIR}/libprotobuf${CMAKE_SHARED_LIBRARY_SUFFIX}* )
install(FILES ${MATCHING_FILES} DESTINATION lib)
file(GLOB MATCHING_FILES ${PROTOBUF_LIBDIR}/libprotoc${CMAKE_SHARED_LIBRARY_SUFFIX}* )
install(FILES ${MATCHING_FILES} DESTINATION lib)

file(GLOB PROTO_FILES "${CMAKE_SOURCE_DIR}/protobuf/src/main/proto/*.proto")

# build protobuf cpp files
# this puts the generated files under build/protobufs
PROTOBUF_GENERATE_CPP(MPF_PROTO_SRCS MPF_PROTO_HEADERS ${PROTO_FILES})

add_library(mpfProtobufs SHARED ${MPF_PROTO_SRCS})

# include ourselves for building
target_include_directories(mpfProtobufs PUBLIC ${CMAKE_CURRENT_BINARY_DIR})

# pass our path up to the parent
set(MPF_PROTO_PATH ${CMAKE_CURRENT_BINARY_DIR} PARENT_SCOPE )

# pass these back up to the parent so we can tell it that these get built
set(MPF_PROTO_HEADERS ${MPF_PROTO_HEADERS} PARENT_SCOPE)

install(TARGETS mpfProtobufs
        RUNTIME DESTINATION bin
        LIBRARY DESTINATION lib
        ARCHIVE DESTINATION lib)

set(MPF_PROTOBUF_LIB mpfProtobufs PARENT_SCOPE)
