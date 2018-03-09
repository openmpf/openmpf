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
import string
import urllib2

import mpf_util


@argh.arg('node', help='hostname or IP address of spare child node to add', action=mpf_util.VerifyHostnameOrIpAddress)
@argh.arg('--port', default='7800', help='JGroups port that spare child node will use', action=mpf_util.VerifyPortRange)
@mpf_util.env_arg('--all-mpf-nodes', 'ALL_MPF_NODES', help='List of all known nodes.')
@argh.arg('--workflow-manager-url', default='http://localhost:8080/workflow-manager/',
          help='Url to Workflow Manager')
def add_node(node, port=None, all_mpf_nodes=None, workflow_manager_url=None):
    """ Adds a spare node to the Workflow Manager """

    known_nodes = []
    for known_node_with_port in all_mpf_nodes.split(','):
        known_nodes.append(known_node_with_port.split('[')[0])

    # Check if node is already in all-mpf-nodes
    if node in known_nodes:
        print mpf_util.MsgUtil.red('Child node: %s is already in the list of known nodes: %s' % (node, known_nodes))
        print mpf_util.MsgUtil.red('Child node: %s has not been added' % node)
        return

    node_with_port = ''.join([node,'[',port,']'])
    new_nodes = ','.join([string.rstrip(all_mpf_nodes,','),node_with_port])

    endpoint_url = ''.join([string.rstrip(workflow_manager_url,'/'),'/rest/env/update?allMpfNodes=',new_nodes])
    try:
        response = urllib2.urlopen(endpoint_url) # DEBUG
        print response.read() # DEBUG
    except:
        print mpf_util.MsgUtil.red('Problem connecting to: %s' % endpoint_url)
        print mpf_util.MsgUtil.red('Child node: %s has not been added' % node)
        raise

    # TODO: modify system files on master

    print mpf_util.MsgUtil.green('Child node: %s has been added' % node)


@argh.arg('node', help='hostname or IP address of child node to remove', action=mpf_util.VerifyHostnameOrIpAddress)
@argh.arg('--workflow-manager-url', default='http://localhost:8080/workflow-manager',
          help='Url to Workflow Manager')
def remove_node(node, workflow_manager_url=None):
    """ Removes a child node from the Workflow Manager """
    pass # DEBUG
    print mpf_util.MsgUtil.green('Child node: %s has been removed' % node)


COMMANDS = (add_node, remove_node)
