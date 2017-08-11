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

MPF_MANAGEMENT_PATH="/opt/mpf/manage/"
echo "--------------------------------------------------------------------------------"
echo "                           Installing OpenMPF 0.10.0"
echo "--------------------------------------------------------------------------------"
if [ "$EUID" -ne 0 ]
  then echo "Please run as sudo/root. Exiting..."
  exit
fi
echo -e "Copying files to $MPF_MANAGEMENT_PATH...\c"
#Determine script path
# SCRIPT_PATH="`dirname \"$0\"`" #relative path
# SCRIPT_PATH="`( cd \"$MY_PATH\" && pwd )`"  # absolutized and normalized
SCRIPT_PATH=$(cd `dirname "${BASH_SOURCE[0]}"` && pwd)
mkdir -p "${MPF_MANAGEMENT_PATH}"
cp -rf "${SCRIPT_PATH}/install/"* "${MPF_MANAGEMENT_PATH}"
find . -name "*.json" -exec cp {} "${MPF_MANAGEMENT_PATH}/repo/files/" \;
echo "complete."
echo "Starting MPF Management Setup (${MPF_MANAGEMENT_PATH}setup-mpf.sh)..."
source ${MPF_MANAGEMENT_PATH}"/install-manager.sh"
