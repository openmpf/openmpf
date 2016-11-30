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


unset(OpenCV_CONFIG_PATH CACHE)
find_package(OpenCV 2.4.9 EXACT REQUIRED)

# Build the detection component API utilities lib

add_library(mpfDetectionComponentAPI SHARED
        detectionComponentUtils.cpp
        MPFImageReader.cpp
        MPFVideoCapture.cpp
        MPFInvalidPropertyException.cpp
        frame_transformers/BaseDecoratedTransformer.cpp
        frame_transformers/FrameCropper.cpp
        frame_transformers/FrameFlipper.cpp
        frame_transformers/FrameRotator.cpp
        frame_transformers/FrameTransformerFactory.cpp
        frame_transformers/NoOpFrameTransformer.cpp)
target_link_libraries(mpfDetectionComponentAPI ${OpenCV_LIBS})



