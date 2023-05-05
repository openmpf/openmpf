#!/bin/bash

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

# don't source mpf.sh - if under /etc/profile.d we got it

if [ ! "$ACTIVE_MQ_BROKER_URI" ]; then
    ACTIVE_MQ_BROKER_URI=tcp://localhost:61616
    echo "WARNING: ACTIVE_MQ_BROKER_URI unset or empty, using $ACTIVE_MQ_BROKER_URI"
fi

if [ "$JAVA_HOME" ]; then
    java_prog=${JAVA_HOME}/bin/java
else
    java_prog=java
fi

set -x
exec "$java_prog" \
    "-Djava.library.path=${MPF_HOME}/lib" \
    "-DACTIVE_MQ_BROKER_URI=${ACTIVE_MQ_BROKER_URI}" \
    "$@"


