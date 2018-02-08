#!/bin/bash

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

# The arguments passed into this script should be 
# 1. The component jar filename
# 2. The activemq queue name
# 3. The service name

# Source log4bash
. ${MPF_HOME}/bin/log4bash.sh

# don't source mpf.sh - if under /etc/profile.d we got it

# Do an environment variable and Java property
if [ -n "${ACTIVE_MQ_HOST}" ]; then
    QUEUE_FLAGS="-DACTIVE_MQ_HOST=${ACTIVE_MQ_HOST}"
else
    QUEUE_FLAGS="-DACTIVE_MQ_HOST=tcp://localhost:61616"
    log_warn "ACTIVE_MQ_HOST unset or empty, using tcp://localhost:61616"
fi


# NOTE: As of Java 6 update 18, Java will allocate up to 1/4 of a machine's physical memory to heap space (-Xmx).
# Java will use 1/64 of a machine's physical memory for the initial heap space (-Xms).

# NOTE: As of Java 8, PermGen no longer exists. Native system memory is used to store class data.

JAVA_FLAGS="-Djava.library.path=${MPF_HOME}/lib:${MPF_HOME}/jars"
JGROUPS_FLAGS=""

if [ -n "${THIS_MPF_NODE}" -a -n "${ALL_MPF_NODES}" ]; then
    JGROUPS_FLAGS="-Djgroups.tcp.address=${THIS_MPF_NODE} -Djgroups.tcp.port=7800 -Djgroups.tcpping.initial_hosts=${ALL_MPF_NODES}"
else
    log_warn "THIS_MPF_NODE and/or ALL_MPF_NODES for jgroups are not set"
fi  

# MPF_JAVA_FLAGS is usually set in the service definition in a  nodeManagerConfig.xml

set -x
exec ${JAVA_HOME}/bin/java ${JAVA_FLAGS} -cp ${MPF_HOME}/jars/mpf-java-component-executor-2.0.0.jar:${MPF_HOME}/plugins/$3/$1 -Dlogback.configurationFile=${MPF_HOME}/plugins/$3/logback.xml ${MPF_JAVA_FLAGS} ${JGROUPS_FLAGS} ${QUEUE_FLAGS} org.mitre.mpf.component.executor.detection.MPFDetectionMain $2
set +x

