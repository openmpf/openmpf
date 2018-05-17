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
import collections
import getpass
import json
import os
import string
import subprocess
import urllib2

from subprocess import call

import mpf_util

@argh.arg('--workflow-manager-url', default='http://localhost:8080/workflow-manager',
          help='Url to Workflow Manager')
def list_nodes(workflow_manager_url=None):
    """ List JGroups membership for nodes in the OpenMPF cluster """

    if not is_wfm_running(workflow_manager_url):
        print mpf_util.MsgUtil.yellow('Cannot determine live JGroups membership.')

        [mpf_sh_valid, all_mpf_nodes] = check_mpf_sh()
        if not mpf_sh_valid:
            print mpf_util.MsgUtil.red('Could not find %s.' % MPF_SH_FILE_PATH)

        [nodes_list, _] = parse_nodes_list(all_mpf_nodes)
        if not nodes_list:
            print mpf_util.MsgUtil.red('No nodes configured in %s.' % MPF_SH_FILE_PATH)
        else:
            print 'Nodes configured in ' + MPF_SH_FILE_PATH + ':\n' + '\n'.join(nodes_list)

        return

    [username, password] = get_username_and_password(False)
    core_nodes_list = get_all_wfm_nodes(workflow_manager_url, username, password, "core")
    spare_nodes_list = get_all_wfm_nodes(workflow_manager_url, username, password, "spare")

    print "Core nodes: " + str(core_nodes_list)

    if spare_nodes_list:
        print "Spare nodes: " + str(spare_nodes_list)
    else:
        print "No spare nodes"


def get_username_and_password(for_admin):
    if for_admin:
        print 'Enter the credentials for a Workflow Manager administrator:'
    else:
        print 'Enter the credentials for a Workflow Manager user:'

    username = raw_input('Username: ')
    password = getpass.getpass('Password: ')

    return [username, password]


def get_all_wfm_nodes(wfm_manager_url, username, password, type = "all"):
    endpoint_url = ''.join([string.rstrip(wfm_manager_url,'/'),'/rest/nodes/all?type=' + type])
    request = urllib2.Request(endpoint_url)

    request.get_method = lambda: 'GET'
    base64string = base64.b64encode('%s:%s' % (username, password))
    request.add_header('Authorization', 'Basic %s' % base64string)

    try:
        response = urllib2.urlopen(request)
    except IOError as err:
        raise mpf_util.MpfError('Problem connecting to ' + endpoint_url + ':\n' + str(err))

    # convert a string of '["X.X.X.X". "X.X.X.X"]' to a Python list
    return response.read()[2:-2].translate(None, '"').split()


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


def parse_nodes_list(all_mpf_nodes):
    nodes_list = []
    nodes_with_ports_list = []
    for known_node_with_port in all_mpf_nodes.split(','):
        known_node = known_node_with_port.split('[')[0]
        if known_node:
            nodes_list.append(known_node)
            nodes_with_ports_list.append(known_node_with_port)
    return nodes_list, nodes_with_ports_list


MPF_SH_FILE_PATH = '/etc/profile.d/mpf.sh'
MPF_SH_SEARCH_STR = 'export ALL_MPF_NODES='

COMMANDS = [list_nodes]
