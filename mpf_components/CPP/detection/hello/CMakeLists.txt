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

project(CplusplusHelloWorld)

# Find Qt. This is needed for the build of the sample_hello_detector executable
install_qt(${PROJECT_NAME}/lib QtCore)

# Find log4cxx
install_log4cxx(${PROJECT_NAME}/lib)

# Build library
add_library(mpfCplusplusHelloWorld SHARED helloWorld)
target_link_libraries(mpfCplusplusHelloWorld mpfDetectionUtils mpfDetectionComponentAPI ${LOG4CXX_LIBRARIES})

install(TARGETS mpfCplusplusHelloWorld LIBRARY DESTINATION ${PROJECT_NAME}/lib)

get_target_property(UTILS_LOCATION mpfDetectionUtils LOCATION)
install(FILES ${UTILS_LOCATION} DESTINATION ${PROJECT_NAME}/lib)

get_target_property(API_LOCATION mpfDetectionComponentAPI LOCATION)
install(FILES ${API_LOCATION} DESTINATION ${PROJECT_NAME}/lib)

# Build executable
add_executable(sample_hello_detector main.cpp)
target_link_libraries(sample_hello_detector mpfCplusplusHelloWorld ${QT_LIBRARIES})

# Install config files
install(FILES Log4cxxConfig.xml
      DESTINATION ${PROJECT_NAME}/config)

# Install descriptor file
install(FILES descriptor.json
      DESTINATION ${PROJECT_NAME}/descriptor)
