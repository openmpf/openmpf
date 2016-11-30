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

import glob
import json
import sys
from os.path import basename
import os.path
import os
import getpass
import subprocess


class textFormat:
    green = '\033[92m'
    red = '\033[91m'
    bold = '\033[1m'
    end = '\033[0m'


def setExtComponent(cname, creg, cstate):
    component_dict = {'componentName':cname, 'register':creg, 'componentState':cstate}
    return component_dict


def loadJSONfromFile(json_filepath):
    try:
        with open(json_filepath, "r") as json_file:
            json_data = json.load(json_file)
    except:
        print 'Could not open filepath ', json_filepath
    else:
        json_file.close()

    return json_data


def getExtComponentState(cname):

    components_file = '/opt/mpf/share/components/components.json'
    state = ''
    if (os.path.isfile(components_file)):
        system_ext_component_data = loadJSONfromFile(components_file)
        for component in system_ext_component_data:
            if (component['componentName'] == cname):
                state = component['componentState']
                break
    else:
        state = False

    return state

def findDescriptorFile(filename, root_dir):
    for root, subdirs, files in os.walk(path):
        if filename in files:
            return os.path.join(root, filename)

def getInstructionsInfo(name, path):
    instructions_string = '\n' \
                          + textFormat.bold \
                          + name \
                          + textFormat.end \
                          + ' has post-installation instructions at ' \
                          + textFormat.bold \
                          + path \
                          + textFormat.end
    return instructions_string


#Exit if not running as the MPF user
if (getpass.getuser() != 'mpf'):
    print 'Please run as the "mpf" user. Exiting...'
    sys.exit()


#Get the package configuration file
package_config_file = glob.glob('/opt/mpf/manage/repo/files/mpf-*-package.json')

#Prompt user to select config file if more than one is found
if (len(package_config_file)>1):
    print 'More than one package config file was found.'
    for idx, config_file in enumerate(package_config_file):
        print idx+1, '- ' + basename(config_file)
    package_config_file_idx = 0
    while(package_config_file_idx not in range(1,len(package_config_file)+1)):
        print 'Which package config file to use?'
        try:
            package_config_file_idx = int(raw_input('(' + (', '.join(str(x) for x in xrange(1,len(package_config_file)+1))) + ') [Default: 1]: ') or 1)
            package_config_file = package_config_file[package_config_file_idx - 1]
        except (IndexError, NameError, SyntaxError, ValueError)  as message:
            print 'Invalid configuration file specified:', message
            package_config_file_idx = 0

# Load the component config file $MPF_HOME/share/components/components.json
package_data = loadJSONfromFile(package_config_file[0])

#Initialize the deployment configuration
deployment_configuration = {}
external_component = []
https = []
pwcfg = []

#Prompt to register external components
for component in package_data['External_Component_Tars']:
    ext_component_state = getExtComponentState(component)
    if(ext_component_state == 'REGISTERED'):
        print '\nExternal component ' + textFormat.bold + component + textFormat.end + ' is already registered.'
        register_external_component = 'n'
    elif(ext_component_state and ext_component_state != 'REGISTERED'):
        print '\nExternal component ' + textFormat.bold + component + textFormat.end + ' is not registered and is in state: ' + ext_component_state + '.'
        register_external_component = 'n'
    else:
        register_external_component = raw_input('\nRegister external component ' + textFormat.bold + component + textFormat.end +'? [y/N]: ' or 'n')
        if(register_external_component.lower() not in ['y','yes']):
            register_external_component = 'n'
        #Record component registration choice
        else:
            register_external_component = 'y'
    #set the external_components dict
    external_component.append(dict(setExtComponent(component, register_external_component, ext_component_state)))

#Prompt for HTTPS support
https_choice = raw_input('\nInclude HTTPS support? This requires a valid keystore. [y/N]: ' or 'n')
https_keystore_exists = False
if(https_choice.lower() not in ['y','yes']):
    https_install = 'n'
    https_keystore_filepath = ''
    https_keystore_password_1 = ''
else:
    https_install = 'y'
    while not (https_keystore_exists):
        https_keystore_filepath = raw_input('\nEnter the full path to the keystore file in the shared storage space (/opt/mpf/share) or <CTRL+C> to abort: ' or 'n')
        https_keystore_exists = os.path.isfile(https_keystore_filepath)
        if(https_keystore_exists):
            pass
        else:
            print textFormat.red + '\nNo file was found at the provided path: ' + textFormat.end + https_keystore_filepath

    https_keystore_password_1 = '1'
    https_keystore_password_2 = '2'
    while not (https_keystore_password_1 == https_keystore_password_2):
        https_keystore_password_1 = getpass.getpass('\nEnter the keystore password (<Enter> for a blank password or <CTRL+C> to abort): ' or '')
        https_keystore_password_2 = getpass.getpass('\nConfirm the keystore password (<Enter> for a blank password or <CTRL+C> to abort): ' or '')
        if not (https_keystore_password_1 == https_keystore_password_2):
            print textFormat.red + '\nThe passwords do not match.' + textFormat.end

#set the https dict
https = {'install': https_install, 'keystore_file': https_keystore_filepath, 'keystore_pw': https_keystore_password_1}

#Prompt for password configuration
pwcfg_choice = raw_input('\nSet custom passwords for workflow-manager and MySQL? [y/N]: ' or 'n')
if(pwcfg_choice.lower() not in ['y','yes']):
    pwcfg_choice= 'n'
else:
    pwcfg_choice = 'y'
#set the pwcfg dict
pwcfg = {'change': pwcfg_choice}

# Add choices to deployment configuration
deployment_configuration['external_component'] = external_component
deployment_configuration['https'] = https
deployment_configuration['pwcfg'] = pwcfg

try:
    deployment_config_file = open('/opt/mpf/share/deployment_config.json', 'w')
    json.dump(deployment_configuration, deployment_config_file)
except:
    print "Could not open /opt/mpf/share/deployment_config.json for writing."
else:
    deployment_config_file.close()

print '\nPlease provide the ' + textFormat.bold + 'MPF' + textFormat.end + ' normal user password in the prompts below...'
if(pwcfg_choice == 'y'):
    print textFormat.bold + '\nAfterwards, you will be prompted to enter custom passwords.' + textFormat.end

#Set the environment variable for ANSIBLE_HOST_KEY_CHECKING to false for the playbook run
os.environ['ANSIBLE_HOST_KEY_CHECKING'] = 'False'

#Create an empty Ansible vault file if it does not exist
ansible_vault = '/home/mpf/.vault'
if not (os.path.isfile(ansible_vault)):
    try:
        open(ansible_vault, "w").close()
    except:
        print 'Could not open the Ansible vault at ', ansible_vault


# Setup the Ansible playbook command
ansible_cmd_1 = 'ansible-playbook'
ansible_cmd_2 = '/opt/mpf/manage/ansible/mpf-site.yml'
ansible_cmd_3 = '--user=mpf'
ansible_cmd_4 = '--ask-pass'
ansible_cmd_5 = '--ask-become-pass'
ansible_cmd_6 = '--vault-password-file'
ansible_cmd_7 = '/home/mpf/.vault'
ansible_cmd_8 = '--extra-vars'
ansible_cmd_9 = 'pwcfg=\'' + pwcfg_choice + '\''


# Run the Ansible playbook
try:
    playbook_run = subprocess.call([ansible_cmd_1,
                                    ansible_cmd_2,
                                    ansible_cmd_3,
                                    ansible_cmd_4,
                                    ansible_cmd_5,
                                    ansible_cmd_6,
                                    ansible_cmd_7,
                                    ansible_cmd_8,
                                    ansible_cmd_9])
except:
    print "Failed to run the Ansible playbook."

if playbook_run == 0:
    print textFormat.bold + '\nMPF installation has completed.' + textFormat.end
    mpf_components_file = '/opt/mpf/share/components/components.json'

    confirm_instructions = 0

    #external components
    if os.path.isfile(mpf_components_file):
        mpf_components_data = loadJSONfromFile(mpf_components_file)

        for ext_component in deployment_configuration['external_component']:
            for component in mpf_components_data:
                if(ext_component['componentName'] == component['componentName'] and
                           ext_component['register'] == 'y'):
                    # load component descriptor
                    component_descriptor = loadJSONfromFile(component['jsonDescriptorPath'])
                    if('instructionsFile' in component_descriptor):
                        component_instructions_path = ''.join(['/opt/mpf/plugins/',
                                                               component['componentName'],
                                                               '/',
                                                               component_descriptor["instructionsFile"]])
                        if os.path.isfile(component_instructions_path):
                            print getInstructionsInfo(component['componentName'], component_instructions_path)
                            confirm_instructions = 1
                    break

    #mpf components
    for component in package_data['MPF_Component_Tars']:

        component_path = ''.join(['/opt/mpf/plugins/',
                                  component,
                                  '/descriptor/*.json'])
        component_descriptor_file = glob.glob(component_path)
        component_descriptor = loadJSONfromFile(component_descriptor_file[0])
        if('instructionsFile' in component_descriptor):
            component_instructions_path = ''.join(['/opt/mpf/plugins/',
                                                   component_descriptor['componentName'],
                                                   '/',
                                                   component_descriptor["instructionsFile"]])
            if os.path.isfile(component_instructions_path):
                print getInstructionsInfo(component_descriptor['componentName'], component_instructions_path)
                confirm_instructions = 1

    #Check for instructions on updating the https keystore
    if os.path.isfile('/home/mpf/mpfkeystoreupdate.txt'):
        print getInstructionsInfo('HTTPS keystore', '/home/mpf/mpfkeystoreupdate.txt')
        confirm_instructions = 1

    #Ask user to confirm notice of the post-installation instructions
    if (confirm_instructions == 1):
        raw_input('\nPress the enter key to acknowledge these instructions.')
else:
    print textFormat.red + '\nMPF installation did not complete successfully.' + textFormat.end
