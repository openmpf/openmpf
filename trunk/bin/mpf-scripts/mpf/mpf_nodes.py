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

import argh
import base64
import getpass
import os
import re
import shutil
import string
import subprocess
import tempfile
import urllib2

from subprocess import call

import mpf_util


@argh.arg('node', help='hostname or IP address of spare child node to add', action=mpf_util.VerifyHostnameOrIpAddress)
@argh.arg('--port', default='7800', help='JGroups port that spare child node will use', action=mpf_util.VerifyPortRange)
@argh.arg('--workflow-manager-url', default='http://localhost:8080/workflow-manager/',
          help='Url to Workflow Manager')
def add_node(node, port=None, workflow_manager_url=None):
    """ Adds a spare node to the OpenMPF cluster """

    # TODO:
    # - TEST: remove node from WFM twice in a row
    # - TEST: register plugin
    # - go back to using MERGE2
    # - check if spare node-manager is running:
    #   - wait for up to 1 minute to see if peer in view after updating initial_hosts
    #     (implement endpoint to get hosts in view - periodically check)


    # Fail fast if we're not the mpf user
    if not getpass.getuser() == 'mpf':
        print mpf_util.MsgUtil.yellow('Please run this command as the \'mpf\' user.')
        return

    # Fail fast if user doesn't have root privileges
    if os.system('sudo whoami &> /dev/null') != 0:
        print mpf_util.MsgUtil.red('Root privilege test failed.')
        return

    # Fail fast if mpf.sh is invalid
    [mpf_sh_valid, all_mpf_nodes] = check_mpf_sh()
    if not mpf_sh_valid:
        return

    # Fail fast if ansible hosts file is invalid
    if not check_ansible_hosts():
        return

    [nodes_list, _] = parse_nodes_list(all_mpf_nodes)

    # Check if node is already in all-mpf-nodes
    if node in nodes_list:
        print mpf_util.MsgUtil.yellow('Child node %s is already in the list of known nodes: %s' % (node, nodes_list))
        print mpf_util.MsgUtil.yellow('Please remove it first.')
        return

    # Add node to known_hosts
    updated_known_hosts = update_known_hosts(node)

    if updated_known_hosts:

        node_with_port = ''.join([node,'[',port,']'])
        new_nodes_with_ports = ','.join([string.rstrip(all_mpf_nodes,','),node_with_port,'']) # add trailing comma

        # If the WFM is running, update the env. variable being used by the WFM
        wfm_running = is_wfm_running(workflow_manager_url)
        if wfm_running:
            print 'Updating value of ALL_MPF_NODES used by the Workflow Manager.'
            try:
                update_wfm_all_mpf_nodes(workflow_manager_url, new_nodes_with_ports)
            except:
                print mpf_util.MsgUtil.red('Child node %s has not been added to the cluster.' % node)
                raise
        else:
            print mpf_util.MsgUtil.yellow('Proceeding anyway.')

        # Modify system files
        updated_mpf_sh = update_mpf_sh(new_nodes_with_ports)
        updated_ansible_hosts = update_ansible_hosts(node, True)

    if not updated_known_hosts or not updated_mpf_sh or not updated_ansible_hosts:
        print mpf_util.MsgUtil.yellow('Child node %s has not been completely added to the cluster. Manual steps required.' % node)
    else:
        print mpf_util.MsgUtil.green('Child node %s has been added to the cluster.' % node)
        if wfm_running:
            print mpf_util.MsgUtil.green('Refresh the Nodes page of the Web UI if it\'s currently open.')
            print mpf_util.MsgUtil.green('Use that page to add the node and configure services.')
        else:
            print mpf_util.MsgUtil.green('Add the node and configure services using Nodes page of the Web UI '
                                         'the next time you start the Workflow Manager.')
        print mpf_util.MsgUtil.green('Run \"source /etc/profile.d/mpf.sh\" in any open terminal windows.')


@argh.arg('node', help='hostname or IP address of child node to remove', action=mpf_util.VerifyHostnameOrIpAddress)
@argh.arg('--workflow-manager-url', default='http://localhost:8080/workflow-manager',
          help='Url to Workflow Manager')
def remove_node(node, workflow_manager_url=None):
    """ Removes a child node from the OpenMPF cluster """

    # Fail fast if we're not the mpf user
    if not getpass.getuser() == 'mpf':
        print mpf_util.MsgUtil.yellow('Please run this command as the \'mpf\' user.')
        return

    # Fail fast if user doesn't have root privileges
    if os.system('sudo whoami &> /dev/null') != 0:
        print mpf_util.MsgUtil.red('Root privilege test failed.')
        return

    # Fail fast if mpf.sh is invalid
    [mpf_sh_valid, all_mpf_nodes] = check_mpf_sh()
    if not mpf_sh_valid:
        return

    # Fail fast if ansible hosts file is invalid
    if not check_ansible_hosts():
        return

    [nodes_list, nodes_with_ports_list] = parse_nodes_list(all_mpf_nodes)

    # Check if node is in all-mpf-nodes
    try:
        index = nodes_list.index(node) # will throw ValueError if not found
        del nodes_with_ports_list[index]
    except ValueError:
        print mpf_util.MsgUtil.yellow('Child node %s is not in the list of known nodes: %s.' % (node, nodes_list))
        print mpf_util.MsgUtil.yellow('Proceeding anyway.')

    new_nodes_with_ports = ','.join(nodes_with_ports_list) + ',' # add trailing comma

    # If the WFM is running, update the env. variable being used by the WFM and the WFM nodes config
    wfm_running = is_wfm_running(workflow_manager_url)
    if wfm_running:
        print 'Updating value of ALL_MPF_NODES used by the Workflow Manager and current node configuration.'
        try:
            update_wfm_all_mpf_nodes(workflow_manager_url, new_nodes_with_ports)
        except:
            print mpf_util.MsgUtil.red('Child node %s has not been removed from the cluster.' % node)
            raise
    else:
        print mpf_util.MsgUtil.yellow('Proceeding anyway.')

    # Modify system files
    updated_mpf_sh = update_mpf_sh(new_nodes_with_ports)
    updated_ansible_hosts = update_ansible_hosts(node, False)

    if not updated_mpf_sh or not updated_ansible_hosts:
        print mpf_util.MsgUtil.yellow('Child node %s has not been completely removed from the cluster. Manual steps required.' % node)
    else:
        print mpf_util.MsgUtil.green('Child node %s has been removed from the cluster.' % node)
        if wfm_running:
            print mpf_util.MsgUtil.green('Refresh the Nodes page of the Web UI if it\'s currently open.')
        else:
            print mpf_util.MsgUtil.green('Remove the node using Nodes page of the Web UI '
                                         'the next time you start the Workflow Manager.')
        print mpf_util.MsgUtil.green('Run \"source /etc/profile.d/mpf.sh\" in any open terminal windows.')


@argh.arg('--workflow-manager-url', default='http://localhost:8080/workflow-manager',
          help='Url to Workflow Manager')
def list_nodes(workflow_manager_url=None):
    """ List available nodes in the OpenMPF cluster """

    if not is_wfm_running(workflow_manager_url):
        print mpf_util.MsgUtil.yellow('Cannot list available nodes.')
        return

    endpoint_url = ''.join([string.rstrip(workflow_manager_url,'/'),'/rest/nodes/available-nodes'])

    print 'Enter the credentials for a Workflow Manager user:'
    username = raw_input('Username: ')
    password = getpass.getpass('Password: ')

    request = urllib2.Request(endpoint_url)
    request.get_method = lambda: 'GET'
    base64string = base64.b64encode('%s:%s' % (username, password))
    request.add_header('Authorization', 'Basic %s' % base64string)

    try:
        response = urllib2.urlopen(request).read()
    except:
        print mpf_util.MsgUtil.red('Problem connecting to %s' % endpoint_url)
        raise

    print response # DEBUG

    # TODO: format and print results


def parse_nodes_list(all_mpf_nodes):
    nodes_list = []
    nodes_with_ports_list = []
    for known_node_with_port in all_mpf_nodes.split(','):
        known_node = known_node_with_port.split('[')[0]
        if known_node:
                nodes_list.append(known_node)
                nodes_with_ports_list.append(known_node_with_port)
    return nodes_list, nodes_with_ports_list


def is_wfm_running(wfm_manager_url):
    request = urllib2.Request(wfm_manager_url)
    request.get_method = lambda: 'HEAD'
    try:
        urllib2.urlopen(request)
        print 'Detected that the Workflow Manager is running.'
        return True
    except:
        print mpf_util.MsgUtil.yellow('Detected that the Workflow Manager is not running.')
        return False


def update_wfm_all_mpf_nodes(wfm_manager_url, nodes_with_ports):
    endpoint_url = ''.join([string.rstrip(wfm_manager_url,'/'),'/rest/nodes/all-mpf-nodes'])

    print 'Enter the credentials for a Workflow Manager administrator:'
    username = raw_input('Username: ')
    password = getpass.getpass('Password: ')

    request = urllib2.Request(endpoint_url, data=nodes_with_ports)
    request.get_method = lambda: 'PUT'
    base64string = base64.b64encode('%s:%s' % (username, password))
    request.add_header('Authorization', 'Basic %s' % base64string)

    try:
        urllib2.urlopen(request)
    except:
        print mpf_util.MsgUtil.red('Problem connecting to %s' % endpoint_url)
        raise


def check_mpf_sh():
    if not os.path.isfile(MPF_SH_FILE_PATH):
        print mpf_util.MsgUtil.red('Error: Could not open ' + MPF_SH_FILE_PATH + '.')
        return [False, None]

    if call(['grep', '-q', MPF_SH_SEARCH_STR, MPF_SH_FILE_PATH]) != 0:
        print mpf_util.MsgUtil.red('Error: Could not find \"' + MPF_SH_SEARCH_STR + '\" in ' + MPF_SH_FILE_PATH + '.')
        return [False, None]

    process = subprocess.Popen(['sed', '-n', 's/^' + MPF_SH_SEARCH_STR + '\\(.*\\)/\\1/p', MPF_SH_FILE_PATH], stdout=subprocess.PIPE)
    [out, _] = process.communicate() # blocking
    if process.returncode != 0:
        print mpf_util.MsgUtil.red('Error: Could not parse \"' + MPF_SH_SEARCH_STR + '\" in ' + MPF_SH_FILE_PATH + '.')
        return [False, None]

    return [True, out.strip()]


def update_mpf_sh(nodes_with_ports):
    # NOTE: check_mpf_sh() should be called before this function

    if call(['sudo', 'sed', '-i', 's/\\(' + MPF_SH_SEARCH_STR + '\\).*/\\1' + nodes_with_ports + '/', MPF_SH_FILE_PATH]) != 0:
        print mpf_util.MsgUtil.red('Error: Could not update \"' + MPF_SH_SEARCH_STR + '\" in ' + MPF_SH_FILE_PATH + '.')
        print mpf_util.MsgUtil.red('Please manually update \"' + MPF_SH_SEARCH_STR + '\" in ' + MPF_SH_FILE_PATH + '.')
        return False

    print 'Updated \"' + MPF_SH_SEARCH_STR + '\" in ' + MPF_SH_FILE_PATH + '.'
    return True


def check_ansible_hosts():
    if not os.path.isfile(ANSIBLE_HOSTS_FILE_PATH):
        print mpf_util.MsgUtil.red('Error: Could not open ' + ANSIBLE_HOSTS_FILE_PATH + '.')
        return False

    if call(['grep', '-q', re.escape(ANSIBLE_HOSTS_SEARCH_STR), ANSIBLE_HOSTS_FILE_PATH]) != 0:
        print mpf_util.MsgUtil.red('Error: Could not find \"' + ANSIBLE_HOSTS_SEARCH_STR + '\" in ' + ANSIBLE_HOSTS_FILE_PATH + '.')
        return False

    return True


def update_ansible_hosts(node, add_node):
    # NOTE: check_ansible_hosts() should be called before this function
    error = False

    try:
        curr_line_num = -1
        start_line_num = -1
        stop_line_num = -1
        child_node_list = []

        with open(ANSIBLE_HOSTS_FILE_PATH, 'r') as file:
            lines = file.readlines()

            for line in lines:
                stripped_line = line.strip()
                curr_line_num += 1
                if start_line_num == -1:
                    if stripped_line == ANSIBLE_HOSTS_SEARCH_STR:
                        start_line_num = curr_line_num
                elif not stripped_line:
                    stop_line_num = curr_line_num-1 # reached blank line
                    break
                else:
                    child_node_list.append(stripped_line)

            if start_line_num == -1:
                print mpf_util.MsgUtil.red('Error: Could not find \"' + ANSIBLE_HOSTS_SEARCH_STR + '\" in ' + ANSIBLE_HOSTS_FILE_PATH + '.')
                error = True
            else:
                if stop_line_num == -1:
                    stop_line_num = curr_line_num # reached end of file

    except IOError:
        print mpf_util.MsgUtil.red('Error: Could not open ' + ANSIBLE_HOSTS_FILE_PATH + '.')
        error = True

    if not error:
        remove_all(child_node_list, node) # just in case, prevent duplicates

        if add_node:
            child_node_list.append(node)

        # Create a temporary file so that we can write to it without root privileges
        temppath = create_temp_copy(ANSIBLE_HOSTS_FILE_PATH)

        with open(temppath, 'w') as file:
            for i in range (0, len(lines)):
                if i < start_line_num or i > stop_line_num:
                    file.write(lines[i])
                elif i == start_line_num:
                    file.write(ANSIBLE_HOSTS_SEARCH_STR + '\n')
                    for child_node in child_node_list:
                        file.write(child_node + '\n')

        if call(['sudo', 'cp', '--no-preserve=mode,ownership', temppath, ANSIBLE_HOSTS_FILE_PATH]) != 0:
            print mpf_util.MsgUtil.red('Error: Could not replace ' + ANSIBLE_HOSTS_FILE_PATH + '.')
            error = True

        remove_temp(temppath)

    if error:
        print mpf_util.MsgUtil.red('Please manually update \"' + ANSIBLE_HOSTS_SEARCH_STR + '\" in ' + ANSIBLE_HOSTS_FILE_PATH + '.')
    else:
        print 'Updated \"' + ANSIBLE_HOSTS_SEARCH_STR + '\" in ' + ANSIBLE_HOSTS_FILE_PATH + '.'

    return not error


def update_known_hosts(node):
    # NOTE: Run keygen and keyscan as the 'mpf' user
    error = False

    # This call may fail if the node doesn't exist in ~/.ssh/known_hosts. That's okay.
    os.system('ssh-keygen -R ' + node + ' &> /dev/null')

    temppath = create_temp()
    print 'Retrieving public SSH key(s) for %s.' % node
    if os.system('ssh-keyscan ' + node + ' &>> ' + temppath) != 0:
        error = True
    else:
        with open(temppath, 'r') as file:
            data = file.read()
            if 'Invalid argument' in data or 'No route to host' in data:
                error = True

    if error:
        print mpf_util.MsgUtil.red('Error: Could not get public SSH key(s) for %s. '
                                   'Please ensure that the node is online and try again.' % node)

    if not error and os.system('echo \'' + data.strip() + '\'  &>> ' + KNOWN_HOSTS_FILE_PATH) != 0:
        print mpf_util.MsgUtil.red('Error: Could not add %s entry to %s.' % (node, KNOWN_HOSTS_FILE_PATH))
        print mpf_util.MsgUtil.yellow('Please manually add %s entry to %s.' % (node, KNOWN_HOSTS_FILE_PATH))
        error = True

    remove_temp(temppath)

    if not error:
        print 'Updated ' + KNOWN_HOSTS_FILE_PATH + '.'

    return not error


def create_temp():
    return tempfile.NamedTemporaryFile().name


def create_temp_copy(filepath):
    temppath = create_temp()
    shutil.copy2(filepath, temppath)
    return temppath


def remove_temp(temppath):
    try:
        os.remove(temppath)
    except OSError:
        pass


def remove_all(list, value):
    list[:] = (x for x in list if x != value)


MPF_SH_FILE_PATH = '/etc/profile.d/mpf.sh' # DEBUG: /home/mpf/Desktop/TMP/mpf-test.sh
MPF_SH_SEARCH_STR = 'export ALL_MPF_NODES='

ANSIBLE_HOSTS_FILE_PATH = '/etc/ansible/hosts' # DEBUG: /home/mpf/Desktop/TMP/hosts-test
ANSIBLE_HOSTS_SEARCH_STR = '[mpf-child]'

KNOWN_HOSTS_FILE_PATH = '~/.ssh/known_hosts'

COMMANDS = (add_node, remove_node, list_nodes)
