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

import getpass
import glob
import json
from collections import OrderedDict
import subprocess
import sys
import tarfile

import os
import os.path
import re
from os.path import basename
from distutils.version import StrictVersion
from collections import namedtuple
import shutil


class text_format:
    green = '\033[92m'
    red = '\033[91m'
    bold = '\033[1m'
    end = '\033[0m'


def set_component(cname, creg, cstate, cdescriptor, carchivefilepath, ctld, csetupfile, cinstfile):
    component_dict = {
        'componentName': cname,
        'register': creg,
        'componentState': cstate,
        'componentDescriptorPath': cdescriptor,
        'packageFilePath': carchivefilepath,
        'componentTLD': ctld,
        'componentSetupFile': csetupfile,
        'componentInstructionsFile': cinstfile
    }
    return component_dict


def get_component_info(component_archive_full_path):

    # Set some default values
    component_setup_file = False
    component_instructions_file = False
    component_version = False

    # Component information needed for the deployment configuration
    ComponentInfo = namedtuple('component_info', ['componentName',
                                                  'componentState',
                                                  'componentDescriptorPath',
                                                  'componentTLD',
                                                  'componentSetupFile',
                                                  'componentInstructionsFile',
                                                  'componentVersion'])

    # Open the component archive file
    component_archive = open_archive(component_archive_full_path)

    # Get the descriptor relative filepath, descriptor json, and tld of the component
    component_archive_data = check_component_archive(component_archive)

    # Load the component descriptor json data

    component_descriptor_json = component_archive_data.componentJsonData

    for key, value in component_descriptor_json.iteritems():
        # Set the component setup relative filepath if it exists
        if key == 'setupFile':
            component_setup_file = value

        # Set the component instructions relative filepath if it exists
        if key == 'instructionsFile':
            component_instructions_file = value

        if key == 'componentVersion':
            component_version = value

    return ComponentInfo(component_descriptor_json['componentName'],
                         get_component_state(component_descriptor_json['componentName'], component_file),
                         component_archive_data.componentDescriptorFilepath,
                         component_archive_data.componentTLD,
                         component_setup_file,
                         component_instructions_file,
                         component_version)


def load_json_from_file(json_filepath):
    try:
        with open(json_filepath, "r") as json_file:
            json_data = json.load(json_file, object_pairs_hook=OrderedDict)
    except:
        print 'Could not open filepath {0}'.format(json_filepath)
        json_data = False
    else:
        json_file.close()

    return json_data


def get_component_state(cname, cfile):
    state = ''
    if os.path.isfile(cfile):
        system_component_data = load_json_from_file(cfile)
        for component_item in system_component_data:
            if component_item['componentName'] == cname:
                state = component_item['componentState']
                break
    else:
        state = False
    return state


def get_instructions_info(name, path):
    instructions_string = '\n {0}{1}{2} has post-installation instructions at {0}{3}{2}'.format(text_format.bold,
                                                                                                name,
                                                                                                text_format.end,
                                                                                                path)
    return instructions_string


def open_archive(archive_targz):
    archive_tar = tarfile.open(archive_targz)
    return archive_tar


def check_component_archive(archive_tar):

    # Set default values
    descriptor_filepath = False
    component_descriptor_json_data = False
    tld_match = False

    # Data gathered from reading the component archive file
    ComponentArchiveData = namedtuple('component_archive_data', ['componentDescriptorFilepath',
                                                                 'componentJsonData',
                                                                 'componentTLD'])

    # Set a regex pattern to match component descriptor files
    # Example : ComponentTopLevelDirectory/descriptor/descriptor.json
    component_descriptor_regex = re.compile('^(?i)([a-zA-Z0-9_-]*\\/descriptor\\/[a-zA-Z0-9_-]*\\.json)')

    # Set a regex pattern to match component tld
    # Example : ComponentTopLevelDirectory/descriptor/descriptor.json
    component_tld_regex = re.compile('^([a-zA-Z0-9_-]+)$')

    # Read the component tar.gz file to get componentDescriptorPath
    tar_members = archive_tar.getnames()

    # Check the component archive list of members for a match to the component descriptor
    component_descriptor_match = \
        [members.group(0) for mbr in tar_members for members in [component_descriptor_regex.search(mbr)] if members]

    # Check the component archive list of members for a match to the component TLD
    component_tld_match = \
        [members.group(0) for mbr in tar_members for members in [component_tld_regex.search(mbr)] if members]

    descriptor_member = archive_tar.getmember(component_descriptor_match[0])
    tld_member = archive_tar.getmember(component_tld_match[0])

    # Verify the descriptor member is a file
    if descriptor_member.isfile():
        # If there is no component descriptor, registration will fail
        # Relative filepath in the component archive
        # Example : ComponentTopLevelDirectory/descriptor/descriptor.json
        descriptor_filepath = descriptor_member.name
        component_descriptor_json_data = json.load(archive_tar.extractfile(descriptor_member.name))

    # Verify the tld member is a dir
    if tld_member.isdir():
        tld_match = tld_member.name

    return ComponentArchiveData(descriptor_filepath,
                                component_descriptor_json_data,
                                tld_match)


def yes_or_no_prompt(question, default_choice):

    valid_response = False
    affirmative_choices = ['y', 'yy', 'yyy', 'yes']
    negative_choices = ['n', 'nn', 'nnn', 'no']

    prompt_string = '\n{0} [{yn}]: '.format(question, yn='Y/n' if default_choice else 'y/N')

    while not valid_response:
        user_choice = raw_input(prompt_string or default_choice).strip().lower()

        if user_choice == '':
            valid_response = True
            return default_choice
        elif user_choice in affirmative_choices:
            valid_response = True
            return True
        elif user_choice in negative_choices:
            valid_response = True
            return False
        else:
            print '{0}{1}{2} is an invalid choice. Please enter Y or N.'.format(text_format.bold, user_choice, text_format.end)


def filepath_prompt(question):

    user_choice = raw_input(question or False).strip()
    file_exists = os.path.isfile(user_choice)

    if not file_exists:
        print 'No file was found at the provided path {0}{1}{2}'.format(text_format.red, user_choice, text_format.end)
        user_choice = False
    return user_choice


# Exit if not running as the MPF user
if getpass.getuser() != 'mpf':
    sys.exit('Please run as the "mpf" user. Exiting...')

# Set mpf_home from environment if available.
if os.environ.get('MPF_HOME'):
    mpf_home = os.environ['MPF_HOME']
else:
    mpf_home = '/opt/mpf'

# Get the package configuration file(s)
package_config_files = sorted(glob.glob(''.join([mpf_home, '/manage/repo/files/openmpf-*-package.json'])),
                              key=os.path.getmtime,
                              reverse=True)

component_file = ''.join([mpf_home, '/data/components.json'])

# Initialize the deployment configuration structures
deployment_configuration = {}
mpf_components = []


# Ask if upgrading MPF from a previous installation
upgrade_choice = yes_or_no_prompt('Is this an upgrade to an existing MPF install?', False)

if upgrade_choice:
    print ('\n{0}NOTE:{1} After an upgrade no services will be running on any of the MPF nodes. You will need to '
        'reconfigure the services through the Nodes page of the web UI.'
        .format(text_format.bold, text_format.end))

    print ('\n{0}NOTE:{1} Upgrading a component will remove the pre-existing component directory located in '
        '{2}/plugins on every MPF node. Backup any necessary files in those directories. You may need to manually '
        'update/replace data files and/or licenses once a component has been upgraded.'
        .format(text_format.bold, text_format.end, mpf_home))
                               '']);
    continue_choice = yes_or_no_prompt('Continue?', True)

    if not continue_choice:
        sys.exit('User aborted. Exiting...')

# Prompt user to select config file if more than one is found
if len(package_config_files) > 1:
    print 'More than one package config file was found.'
    for idx, config_file in enumerate(package_config_files):
        print '{0}- {1}'.format(idx+1, basename(config_file))
    package_config_file_idx = 0
    while package_config_file_idx not in range(1, len(package_config_files)+1):
        print 'Which package config file to use?'
        package_config_file_idx = int(raw_input('(' + (', '.join(str(x) for x in xrange(1, len(package_config_files) + 1))) + ') [Default: 1]: ') or 1)
        package_config_files = package_config_files[package_config_file_idx - 1]

else:
    # Only one package configuration file was found
    package_config_files = package_config_files[0]

# Load the package configuration file
package_data = load_json_from_file(package_config_files)


# Prompt to register components listed in the package configuration file.
for component in package_data['MPF_Components']:

    print '\nChecking {0}{1}{2} component, please wait...'.format(text_format.bold, component['name'], text_format.end)

    # Set some default values
    component_name = component['name']
    register_component = False
    component_version_upgrade = False
    installed_component_data = False

    # Set the component archive path.
    component_archive_path = ''.join([mpf_home, '/manage/repo/tars/mpf/', component['name'], '*'])

    # If component is already installed, get version
    installed_component_descriptor_path = ''.join([mpf_home, '/plugins/', component['name'], '/descriptor/descriptor.json'])
    component_installed = os.path.isfile(installed_component_descriptor_path)

    # If upgrading and the component is already installed on the system, load the installed descriptor.
    if upgrade_choice and component_installed:
        installed_component_data = load_json_from_file(installed_component_descriptor_path)

    # Get the component archive
    component_archive_file = sorted(glob.glob(component_archive_path), reverse=True)
    component_archive_filepath = component_archive_file[0]

    # If a match was found and it is a file
    if component_archive_file and os.path.isfile(component_archive_filepath):
        component_archive_info = get_component_info(component_archive_filepath)

        # If the installed component has a componentVersion key, compare the installed version to the component in the deployment package.
        if upgrade_choice and installed_component_data and 'componentVersion' in installed_component_data:
            if StrictVersion(component_archive_info.componentVersion) > StrictVersion(installed_component_data['componentVersion']):
                component_version_upgrade = True

        # If the installed component does not have a componentVersion key, but the component in the deployment package does, install the latter.
        elif upgrade_choice and installed_component_data and 'componentVersion' not in installed_component_data and component_archive_info.componentVersion:
            component_version_upgrade = True

        # Check the component state and conditionally prompt to register it
        if component_archive_info.componentState == 'REGISTERED' and upgrade_choice and component_version_upgrade:
            register_component = yes_or_no_prompt('Upgrade and re-register component {0}{1}{2}?'.format(text_format.bold, component_archive_info.componentName, text_format.end), True)

        elif component_archive_info.componentState == 'REGISTERED' and upgrade_choice and not component_version_upgrade:
            print 'Component {0}{1}{2} is already registered and is the latest version.'.format(text_format.bold, component_archive_info.componentName, text_format.end)

        elif component_archive_info.componentState == 'REGISTERED' and not upgrade_choice:
            print 'Component {0}{1}{2} is already registered.'.format(text_format.bold, component_archive_info.componentName, text_format.end)

        elif component_archive_info.componentState and component_archive_info.componentState != 'REGISTERED':
            print 'Component {0}{1}{2} is not registered and is in state: {3}'.format(text_format.bold, component_archive_info.componentName, text_format.end, component_archive_info.componentName)

        else:
            register_component = yes_or_no_prompt('Register new component {0}{1}{2}?'.format(text_format.bold, component_archive_info.componentName, text_format.end), True)

    else:
        # A component archive file was not found at $MPF_HOME/manage/repo/tars/mpf/<component_name>*.tar.gz
        component_archive_file = [False]
        print 'Could not locate the {0}{1}{2} component archive file. Will not attempt to register it.'.format(text_format.bold, component['name'], text_format.end)

    # Append component information the component's dict for the deployment config
    mpf_components.append(
        set_component(
            component_archive_info.componentName,
            register_component,
            component_archive_info.componentState,
            component_archive_info.componentDescriptorPath,
            component_archive_filepath,
            component_archive_info.componentTLD,
            component_archive_info.componentSetupFile,
            component_archive_info.componentInstructionsFile))


# Prompt for HTTPS support
# Set some default values
https_keystore = False
https_keystore_filepath = ''
https_keystore_password_1 = ''
https_keystore_password_2 = '2'

# Prompt the user to use HTTPS
https_choice = yes_or_no_prompt('Include HTTPS support? This requires a valid keystore.', False)

if https_choice:
    # Ask for the keystore filepath and verify it exists
    while not https_keystore:
        https_keystore = filepath_prompt('Enter the full path to the keystore file in the shared storage space ({0}/share) or <CTRL+C> to abort: '.format(mpf_home))

    # Prompt and confirm for the keystore password
    while not (https_keystore_password_1 == https_keystore_password_2):
        https_keystore_password_1 = \
            getpass.getpass('\nEnter the keystore password (<Enter> for a blank password or <CTRL+C> to abort): ' or '')
        https_keystore_password_2 = \
            getpass.getpass('\nConfirm the keystore password (<Enter> for a blank password or <CTRL+C> to abort): ' or '')
        if not (https_keystore_password_1 == https_keystore_password_2):
            print text_format.red + '{0}\nThe passwords do not match.{1}'.format(text_format.red, text_format.end)

# Set the https dict
https = {'install': https_choice, 'keystore_file': https_keystore, 'keystore_pw': https_keystore_password_1}


# Prompt for password configuration
pwcfg_choice = yes_or_no_prompt('Set custom passwords for workflow-manager and MySQL?', False)

# Set the pwcfg dict
pwcfg = {'change': pwcfg_choice}

# Add choices to deployment configuration
deployment_configuration['upgrade'] = upgrade_choice
deployment_configuration['package_descriptor'] = package_config_files
deployment_configuration['mpf_components'] = mpf_components
deployment_configuration['https'] = https
deployment_configuration['pwcfg'] = pwcfg

# Try to open the deployment configuration file for writing.
try:
    deployment_config_file = open(mpf_home + '/share/deployment_config.json', 'w')
    json.dump(deployment_configuration, deployment_config_file, indent=4, separators=(',', ': '))
except:
    sys.exit('Could not open {0}/share/deployment_config.json for writing.'.format(mpf_home))
else:
    deployment_config_file.close()


print '\nPlease provide the {0}MPF{1} normal user password in the prompts below...' \
    .format(text_format.bold, text_format.end)

if pwcfg_choice:
    print text_format.bold + '\n{0}Afterwards, you will be prompted to enter custom passwords.{1}' \
        .format(text_format.bold, text_format.end)


# Set the environment variable for ANSIBLE_HOST_KEY_CHECKING to false for the playbook run
os.environ['ANSIBLE_HOST_KEY_CHECKING'] = 'False'

# Create an empty Ansible vault file if it does not exist
ansible_vault = '/home/mpf/.vault'
if not (os.path.isfile(ansible_vault)):
    try:
        open(ansible_vault, "w").close()
    # Could not open an ansible vault for writing
    except Exception as error:
        print 'Could not open the Ansible vault at {0}. Returned error is {1}'.format(ansible_vault, error)


# Setup the Ansible playbook command
ansible_cmd = ['ansible-playbook',
               '{0}/manage/ansible/mpf-site.yml'.format(mpf_home),
               '--user=mpf',
               '--ask-pass',
               '--ask-become-pass',
               '--vault-password-file',
               '/home/mpf/.vault',
               '--extra-vars',
               'pwcfg={0}'.format(pwcfg_choice)]

# Run the Ansible playbook
try:
    playbook_run = subprocess.check_call(ansible_cmd)
except (subprocess.CalledProcessError, NameError) as error:
    playbook_run = None
    print 'Failed to run the Ansible playbook. Returned error is {0}'.format(error.output)
except KeyboardInterrupt as user_aborted:
    playbook_run = None
    print 'User aborted Ansible playbook.'
except Exception as error:
    playbook_run = None
    print 'Failed to run the Ansible playbook. Returned error is {0}'.format(error)

if playbook_run is 0:
    print '\n{0}MPF installation has completed.{1}'.format(text_format.bold, text_format.end)
else:
    print '\n{0}MPF installation did not complete successfully.{1}'.format(text_format.red, text_format.end)


# Set a default value
confirm_instructions = False

# Load the MPF components file if it exists
# If installation failed but components with instructions were registered, still show them
if os.path.isfile(component_file):
    mpf_components_data = load_json_from_file(component_file)

    # Check component descriptors for an instructionsFile value
    # Will only check components that were configured to be registered
    for component in deployment_configuration['mpf_components']:
        if component['register'] and get_component_state(component['componentName'],
                                                         component_file) == 'REGISTERED':

            # Verify the component was successfully extracted and the descriptor file exists
            if os.path.isfile(''.join([mpf_home, '/plugins/', component['componentDescriptorPath']])):
                component_descriptor = load_json_from_file(''.join([mpf_home,
                                                                    '/plugins/',
                                                                    component['componentDescriptorPath']]))

                # If there is an instructionsFile path, check for the existence of the file
                # This indicates if the instructions were generated by the component setup
                # The absence of an instructions file indicates no immediate action is needed for the component
                if 'instructionsFile' in component_descriptor:
                    component_instructions_path = ''.join([mpf_home, '/plugins/',
                                                           component['componentTLD'],
                                                           '/',
                                                           component_descriptor["instructionsFile"]])

                    # If the instructions file exists, print the info to the screen
                    if os.path.isfile(component_instructions_path):
                        print get_instructions_info(component_descriptor['componentName'], component_instructions_path)
                        confirm_instructions = True

# Check for instructions on updating the https keystore
# If these instructions exist, there was a problem using the provided https keystore
if os.path.isfile('/home/mpf/mpfkeystoreupdate.txt'):
    print get_instructions_info('HTTPS keystore', '/home/mpf/mpfkeystoreupdate.txt')
    confirm_instructions = True

# Ask user to confirm notice of the post-installation instructions
if confirm_instructions:
    raw_input('\nPress the enter key to acknowledge these instructions.')
