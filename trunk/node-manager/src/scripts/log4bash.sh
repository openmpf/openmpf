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

symlinker=${MPF_HOME}/bin/symlink.sh

log() {
    local log_text="$1"
    local log_level="$2"
    local log_file="$3"
    local log_echo="$4"
   
    # echo first in case log file doesn't exist
    if [ "${log_echo}" = true ] ; then
        # to terminal or capture device
    	echo -e "${log_text}";
    fi

    if ! [ -z "${log_file}" ] ; then
        # bash scripts will manage their own log
        if [ `whoami` == ${MPF_USER} ] ; then
            echo -e "$(date +"%Y-%m-%d %H:%M:%S,%3N") ${log_level} - ${log_text}" >> ${log_file}
        else
            su - ${MPF_USER} -c "echo -e \"$(date +"%Y-%m-%d %H:%M:%S,%3N") ${log_level} - ${log_text}\" >> \"${log_file}\""
        fi
    fi

    # return the status for the command that was on the right side of the pipe
    return ${PIPESTATUS[1]};
}

log_success()   { log "$1" "SUCCESS" "$2" "$3"; }
log_error()     { log "$1" "ERROR"   "$2" "$3"; }
log_warn()      { log "$1" "WARN"    "$2" "$3"; }
log_info()      { log "$1" "INFO"    "$2" "$3"; }
log_debug()     { log "$1" "DEBUG"   "$2" "$3"; }


