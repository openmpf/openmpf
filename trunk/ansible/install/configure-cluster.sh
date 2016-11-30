#!/usr/bin/env bash
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
#Colors for warning banner
red=`tput setaf 1`
reset=`tput sgr0`

if [ "$EUID" -ne 0 ]
  then echo "Please run as root/sudo. Exiting..."
  exit
fi

if [ ! -f /etc/ansible/hosts ]; then
    echo "${red}MPF manager not installed. Run install-mpf.sh. Exiting...${reset}"
    exit
fi

echo "--------------------------------------------------------------------------------"
echo "-                          MPF Cluster Configuraton                            -"
echo "--------------------------------------------------------------------------------"

#If we've setup the cluster previously, warn the user
if grep -Fxq "[mpf-master]" /etc/ansible/hosts
then
    echo "${red}Note: Any existing cluster info will be removed.${reset}"
    read -rsp $'Press any key to continue or <Ctrl+C> to exit\n' -n1 key
fi

echo "What is the IP/hostname of the MPF Master Host [$(hostname)]:"
read MASTER_HOST
if [ -z "$MASTER_HOST" ]
then
  MASTER_HOST=$(hostname)
fi
REPO_HOST=$MASTER_HOST
echo "[mpf-repo]" > /etc/ansible/hosts.bak
echo $REPO_HOST >> /etc/ansible/hosts.bak
echo "[mpf-master]" >> /etc/ansible/hosts.bak
echo $MASTER_HOST >> /etc/ansible/hosts.bak
echo "[mpf-child]" >> /etc/ansible/hosts.bak

echo "Updating SSH keys..."

if [ ! -d ~/.ssh ]
then
   mkdir ~/.ssh
fi

touch ~/.ssh/known_hosts

#Remove existing keys and add new keys for known_hosts
ssh-keygen -R "$MASTER_HOST"  &> /dev/null
ssh-keyscan "$MASTER_HOST" &>> ~/.ssh/known_hosts

CHILD_PROVIDED="0"

echo "Provide the hostname of each MPF Child Host. (Press <Enter> after each host, twice when complete):"
read CHILD_HOST
while [ "$CHILD_HOST" ]
do
    if [ "$CHILD_HOST" ]
    then
      CHILD_PROVIDED="1"
      echo $CHILD_HOST >> /etc/ansible/hosts.bak
      if [ "$CHILD_HOST" != "$MASTER_HOST" ]
      then
        ssh-keygen -R "$CHILD_HOST"  &> /dev/null
        ssh-keyscan "$CHILD_HOST" &>> ~/.ssh/known_hosts
      fi
    fi
    read CHILD_HOST
done
if [ "$CHILD_PROVIDED" -eq 0 ]
then
  echo "${red}Warning: No child provided to MPF. Defaulting <$MASTER_HOST> as a child node.${reset}"

  echo $MASTER_HOST >> /etc/ansible/hosts.bak
fi


cp /etc/ansible/hosts.bak /etc/ansible/hosts
echo "Completed Ansible hosts configuration."
echo "Performing MPF ansible user and ssh key configuration for cluster hosts..."
echo ""
echo "Please provide an existing priv. user that will be used to configure MPF on the remote machines."
echo -n "Username: "
read USER_NAME
#This touch is for an ansible bug... https://github.com/ansible/ansible/issues/10057
touch ~/.ssh/known_hosts
ansible-playbook /opt/mpf/manage/ansible/mpf-bootstrap.yml --ask-pass --user $USER_NAME --become --ask-become-pass
echo "To complete node configuration, run:"
echo ". /opt/mpf/manage/push-configuration.sh"
