#!/bin/bash

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

# This script is for updating expected outputs with the Jenkins-generated versions of the outputs. Run this script from
# $HOME/mpf/trunk/mpf-system-tests/src/test/resources/output to replace the cm-controlled versions of the files
# with files that were produced by Jenkins.

if [[ $# -le 0 ]]
then
	printf "Usage:  copy-jenkins-outputs-nightly.sh <jenkins username>\n"
	exit 1
else
    JENKINS_USER=$1
fi

JENKINS_HOST=jenkins-mpf-1.mitre.org

# Create an ssh socket so that the user doesn't have to enter the ssh/scp password multiple times
SSHSOCKET=~/.ssh/"$JENKINS_USER"@"$JENKINS_HOST"
ssh -M -f -N -o ControlPath=$SSHSOCKET "$JENKINS_USER"@"$JENKINS_HOST"

# Copy the entire output-objects directory into /mpfdata, to a directory with r-x permissions for group & others (unlike the source (share) directory)
ssh -t -o ControlPath=$SSHSOCKET "$JENKINS_USER"@"JENKINS_HOST" "sudo rm -rf /mpfdata/output/output-objects; sudo cp -rp /var/lib/jenkins/jobs/mpf-nightly/workspace/trunk/install/share/output-objects /mpfdata/output/output-objects; sudo chmod -R 755 /mpfdata/output/output-objects"

scp -p -o ControlPath=$SSHSOCKET "$JENKINS_USER"@"JENKINS_HOST":/mpfdata/output/output-objects/1/detection.json face/runDetectMarkupMultipleMediaTypes.json
scp -p -o ControlPath=$SSHSOCKET "$JENKINS_USER"@"JENKINS_HOST":/mpfdata/output/output-objects/2/detection.json face/runFaceCombinedDetectImage.json
scp -p -o ControlPath=$SSHSOCKET "$JENKINS_USER"@"JENKINS_HOST":/mpfdata/output/output-objects/3/detection.json motion/runMotionPreprocessing.json
scp -p -o ControlPath=$SSHSOCKET "$JENKINS_USER"@"JENKINS_HOST":/mpfdata/output/output-objects/4/detection.json motion/runMotionPreprocessingFaceDetectionMarkup.json
scp -p -o ControlPath=$SSHSOCKET "$JENKINS_USER"@"JENKINS_HOST":/mpfdata/output/output-objects/6/detection.json motion/runMotionTracking.json

ssh -S $SSHSOCKET -O exit "$JENKINS_USER"@"$JENKINS_HOST"
