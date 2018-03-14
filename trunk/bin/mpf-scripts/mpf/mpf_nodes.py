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
import os.path
import string
import urllib2

from subprocess import call

import mpf_util


@argh.arg('node', help='hostname or IP address of spare child node to add', action=mpf_util.VerifyHostnameOrIpAddress)
@argh.arg('--port', default='7800', help='JGroups port that spare child node will use', action=mpf_util.VerifyPortRange)
@mpf_util.env_arg('--all-mpf-nodes', 'ALL_MPF_NODES', help='List of all known nodes.')
@argh.arg('--workflow-manager-url', default='http://localhost:8080/workflow-manager/',
          help='Url to Workflow Manager')
def add_node(node, port=None, all_mpf_nodes=None, workflow_manager_url=None):
    """ Adds a spare node to the OpenMPF cluster """

    [nodes_list, _] = get_nodes_list(all_mpf_nodes)

    # Check if node is already in all-mpf-nodes
    if node in nodes_list:
        print mpf_util.MsgUtil.red('Child node %s is already in the list of known nodes: %s' % (node, nodes_list))
        print mpf_util.MsgUtil.red('Child node %s has not been added to the cluster.' % node)
        return

    nodes_list.append(node)

    node_with_port = ''.join([node,'[',port,']'])
    new_nodes_with_ports = ','.join([string.rstrip(all_mpf_nodes,','),node_with_port,'']) # add trailing comma

    # If the WFM is running, update the env. variable being used by the WFM
    if is_wfm_running(workflow_manager_url):
        print 'Updating value of ALL_MPF_NODES used by the Workflow Manager.'
        try:
            update_wfm_all_mpf_nodes(workflow_manager_url, new_nodes_with_ports)
        except:
            print mpf_util.MsgUtil.red('Child node %s has not been added to the cluster.' % node)
            raise

    # Modify system files
    updated_mpf_sh = update_mpf_sh(new_nodes_with_ports)
    updated_ansible_hosts = update_ansible_hosts(nodes_list)

    if not updated_mpf_sh or not updated_ansible_hosts:
        print mpf_util.MsgUtil.yellow('Child node %s has not been completely added to the cluster. Manual steps required.' % node)
    else:
        print mpf_util.MsgUtil.green('Child node %s has been added to the cluster.' % node)
        print mpf_util.MsgUtil.green('Refresh the Nodes page of the Web UI if it\'s currently open.')
        print mpf_util.MsgUtil.green('Use that page to add the node and configure services.')
        print mpf_util.MsgUtil.green('Run \"source /etc/profile.d/mpf.sh\" in any open terminal windows.')


@argh.arg('node', help='hostname or IP address of child node to remove', action=mpf_util.VerifyHostnameOrIpAddress)
@mpf_util.env_arg('--all-mpf-nodes', 'ALL_MPF_NODES', help='List of all known nodes.')
@argh.arg('--workflow-manager-url', default='http://localhost:8080/workflow-manager',
          help='Url to Workflow Manager')
def remove_node(node, all_mpf_nodes=None, workflow_manager_url=None):
    """ Removes a child node from the OpenMPF cluster """

    [nodes_list, nodes_with_ports_list] = get_nodes_list(all_mpf_nodes)

    # Check if node is in all-mpf-nodes
    try:
        index = nodes_list.index(node) # will throw ValueError if not found
        del nodes_list[index]
        del nodes_with_ports_list[index]
    except ValueError:
        print mpf_util.MsgUtil.yellow('Child node %s is not in the list of known nodes: %s' % (node, nodes_list))
        print mpf_util.MsgUtil.yellow('Nothing to do.')
        return

    new_nodes_with_ports = ','.join(nodes_with_ports_list) + ',' # add trailing comma

    # If the WFM is running, update the env. variable being used by the WFM and the WFM nodes config
    if is_wfm_running(workflow_manager_url):
        print 'Updating value of ALL_MPF_NODES used by the Workflow Manager and current node configuration.'
        try:
            update_wfm_all_mpf_nodes(workflow_manager_url, new_nodes_with_ports)
        except:
            print mpf_util.MsgUtil.red('Child node %s has not been removed from the cluster.' % node)
            raise

    # Modify system files
    updated_mpf_sh = update_mpf_sh(new_nodes_with_ports)
    updated_ansible_hosts = update_ansible_hosts(nodes_list)

    if not updated_mpf_sh or not updated_ansible_hosts:
        print mpf_util.MsgUtil.yellow('Child node %s has not been completely removed from the cluster. Manual steps required.' % node)
    else:
        print mpf_util.MsgUtil.green('Child node %s has been removed from the cluster.' % node)
        print mpf_util.MsgUtil.green('Refresh the Nodes page of the Web UI if it\'s currently open.')
        print mpf_util.MsgUtil.green('Run \"source /etc/profile.d/mpf.sh\" in any open terminal windows.')


def get_nodes_list(all_mpf_nodes):
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
        print mpf_util.MsgUtil.yellow('Detected that the Workflow Manager is not running. Proceeding.')
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


def update_mpf_sh(nodes_with_ports):
    filename = '/home/mpf/Desktop/TMP/mpf-test.sh' # DEBUG: /etc/profile.d/mpf.sh
    findstr = 'export ALL_MPF_NODES='

    error = False

    if not os.path.isfile(filename):
        print mpf_util.MsgUtil.red('Error: Could not open ' + filename + '.')
        error = True

    if not error:
        retcode = call(['grep', '-q', findstr, filename])
        if retcode != 0:
            print mpf_util.MsgUtil.red('Error: Could not find \"' + findstr + '\" in ' + filename + '.')
            error = True
        else:
            retcode = call(['sudo', 'sed', '-i', 's/\\(' + findstr + '\\).*/\\1' + nodes_with_ports + '/', filename])
            if retcode != 0:
                print mpf_util.MsgUtil.red('Error: Could not update \"' + findstr + '\" in ' + filename + '.')
                error = True
            else:
                print 'Updated \"' + findstr + '\" in ' + filename + '.'

    if error:
        print mpf_util.MsgUtil.red('Please manually update \"' + findstr + '\" in ' + filename + '.')

    return not error


def update_ansible_hosts(nodes_list):
    filename = '/home/mpf/Desktop/TMP/hosts-test' # DEBUG: /etc/ansible/hosts
    findstr = '[mpf-child]'

    error = False

    try:
        curr_line_num = -1
        start_line_num = -1
        stop_line_num = -1

        with open(filename, 'r') as file:
            lines = file.readlines()

            for line in lines:
                stripped_line = line.strip()
                curr_line_num += 1
                if start_line_num == -1:
                    if stripped_line == findstr:
                        start_line_num = curr_line_num
                else:
                    if not stripped_line:
                        stop_line_num = curr_line_num-1 # reached blank line
                        break

            if start_line_num == -1:
                print mpf_util.MsgUtil.red('Error: Could not find \"' + findstr + '\" in ' + filename + '.')
                error = True
            else:
                if stop_line_num == -1:
                    stop_line_num = curr_line_num # reached end of file

        if not error:
            with open(filename, 'w') as file:
                for i in range (0, len(lines)):
                    if i < start_line_num or i > stop_line_num:
                        file.write(lines[i])
                    elif i == start_line_num:
                        file.write(findstr + '\n')
                        for node in nodes_list:
                            file.write(node + '\n')

            print 'Updated \"' + findstr + '\" in ' + filename + '.'

    except IOError:
        print mpf_util.MsgUtil.red('Error: Could not open ' + filename + '.')
        error = True

    if error:
        print mpf_util.MsgUtil.red('Please manually update \"' + findstr + '\" in ' + filename + '.')

    return not error


COMMANDS = (add_node, remove_node)
