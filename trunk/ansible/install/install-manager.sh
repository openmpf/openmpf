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
echo "--------------------------------------------------------------------------------"
echo "                       MPF Management Installation                              "
echo "--------------------------------------------------------------------------------"
#Determine script path
# SCRIPT_PATH="`dirname \"$0\"`" #relative path
# SCRIPT_PATH="`( cd \"$MY_PATH\" && pwd )`"  # absolutized and normalized
SCRIPT_PATH=$(cd `dirname "${BASH_SOURCE[0]}"` && pwd)

if [ "$SCRIPT_PATH" != "/opt/mpf/manage" ]; then
  echo "Warning: MPF initial configuration not performed. Run install.sh."
  exit
fi

REPO_PATH=$SCRIPT_PATH"/repo"

echo "Installing createrepo..."
if [ ! -d $REPO_PATH"/rpms" ]; then
  echo "rpms subdirectory not found in [$SCRIPT_PATH]. Exiting..."
  exit
fi

if ! rpm -q --quiet createrepo ; then
  DLT_RPM=$(find $REPO_PATH"/rpms/management/" -type f -name "deltarpm*.rpm")
  PYDLT_RPM=$(find $REPO_PATH"/rpms/management/" -type f -name "python-deltarpm*.rpm")
  CR_RPM=$(find $REPO_PATH"/rpms/management/" -type f -name "createrepo*.rpm")
  PYXML2_RPM=$(find $REPO_PATH"/rpms/management/" -type f -name "libxml2-python*.rpm")
  XML2_RPM=$(find $REPO_PATH"/rpms/management/" -type f -name "libxml2*.rpm")
  LIBSEL_RPM=$(find $REPO_PATH"/rpms/management/" -type f -name "libselinux-python*.rpm")
  PYTHON_RPM=$(find $REPO_PATH"/rpms/management/" -type f -name "python-2*.rpm")
  PYLIB_RPM=$(find $REPO_PATH"/rpms/management/" -type f -name "python-libs*.rpm")
  PYDEVEL_RPM=$(find $REPO_PATH"/rpms/management/" -type f -name "python-devel*.rpm")

  yum -y --nogpgcheck localinstall --disablerepo=* $DLT_RPM $PYDLT_RPM $CR_RPM $PYXML2_RPM $XML2_RPM $LIBSEL_RPM $PYTHON_RPM $PYLIB_RPM $PYDEVEL_RPM

  echo "Completed installing createrepo."
else
  echo "Createrepo already installed."
fi
if [ -z $(rpm -qa | grep createrepo) ]; then
  echo "Required package not found: createrepo. Exiting..."
  exit
fi

echo -e "Creating local MPF RPM repository...\c"
#Create a temporary repo for installing ansible
createrepo -q $REPO_PATH"/rpms/management"
touch /etc/yum.repos.d/mpf-temp.repo
echo "[mpf-temp-repo]" > /etc/yum.repos.d/mpf-temp.repo
echo "name=MPF Temp Repository" >> /etc/yum.repos.d/mpf-temp.repo
echo "baseurl=file://"$REPO_PATH"/rpms/management" >> /etc/yum.repos.d/mpf-temp.repo
echo "gpgcheck=0" >> /etc/yum.repos.d/mpf-temp.repo
echo "enabled=1" >> /etc/yum.repos.d/mpf-temp.repo
echo "MPF ansible repository created."

echo ""
echo -e "Installing ansible..."
yum clean all -q
yum install ansible --disablerepo=* --enablerepo=mpf-temp-repo -y -q

#Clean up the temp repo now that ansible is installed
rm /etc/yum.repos.d/mpf-temp.repo
yum clean all -q

#Increase SSH timeout value for ansible to 30 seconds
sudo sed -i 's/#timeout = 10/timeout = 30/' /etc/ansible/ansible.cfg

echo "Ansible installation complete."
echo "Executing ansible playbook for local system..."
#TODO: Only add this if not present
# echo "[mpf-repo]" >> /etc/ansible/hosts
# echo "child" >> /etc/ansible/hosts
#This is to handle weird bug with not being able to run the playbook from /opt/mpf/manage
cd /opt/mpf/manage/ansible; ansible-playbook /opt/mpf/manage/ansible/mpf-repo.yml
echo "MPF Management Server Setup Complete."
echo "To configure MPF, please run: sh /opt/mpf/manage/configure-cluster.sh"
