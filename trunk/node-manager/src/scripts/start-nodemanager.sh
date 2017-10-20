#!/bin/bash

#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2017 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2017 The MITRE Corporation                                      #
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

# Set some MPF-specific environment variables
. /etc/profile.d/mpf.sh

# Source log4bash
. ${MPF_HOME}/bin/log4bash.sh

pidfile=$1
logfile=$2

jarfile="${MPF_HOME}/jars/mpf-nodemanager-1.0.0.jar"

# for debugging
# log_debug "Environment:\n`env`" "${logfile}" false

if [ -z "${JAVA_HOME}" ]; then
    log_error "JAVA_HOME is unset" "${logfile}" true
    exit 1
fi

javabin=${JAVA_HOME}/bin/java

if [ -n "${THIS_MPF_NODE}" -a -n "${ALL_MPF_NODES}" ]; then
    JGROUPS_FLAGS="-Djgroups.tcp.address=${THIS_MPF_NODE} -Djgroups.tcp.port=7800 -Djgroups.tcpping.initial_hosts=${ALL_MPF_NODES}"
else
    log_warn "THIS_MPF_NODE and/or ALL_MPF_NODES for jgroups are not set" "${logfile}" false
fi  

# this should not be set unless running local tests
#export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:install/lib"

# ideally, daemons should have a wd of / (root), however nodemanager might cwd when it executes a service
cd /

# log_info "Using MPF_HOME=${MPF_HOME}" "${logfile}" false

# jar process will manage its own log; log will be rotated every night at midnight
nohup  ${javabin} ${JGROUPS_FLAGS} ${MPF_JAVA_FLAGS} -jar ${jarfile} > /dev/null & #2>&1 #now displaying std err

# pid of the nohup process
pid=$!

# give it some time to potentially fail
sleep 1

# check to see if it really is running or if this is stale
if ! [[ -e /proc/$pid ]]; then
  log_error "Failed to start ${jarfile}" "${logfile}" true
  exit 1
fi

log_info "NodeManager started with PID $pid"
echo $pid > ${pidfile}
RETVAL=$?

# be explicit
exit $RETVAL
