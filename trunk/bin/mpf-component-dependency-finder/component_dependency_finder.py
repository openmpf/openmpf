#! /usr/bin/env python

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

import argparse
import heapq
import json
import multiprocessing
import os
import sys
import tarfile


"""
Given a collection of components, this script attempts to determine a valid registration order.
A valid registration order is when each component's dependencies are registered before the
component itself.
This is implemented is Python instead of Java because both the deployment scripts and
the Workflow Manager need this functionality.
"""


def main():
    args = parse_args()
    json_paths, targz_paths = get_paths_from_cmdline_args(args)
    components = get_components(json_paths, targz_paths)
    if args.for_single_component:
        registration_order = get_registration_order_for_single_component(args.for_single_component, components)
    else:
        registration_order = get_component_registration_order(components)
    for component in registration_order:
        print component.path


def parse_args():
    parser = argparse.ArgumentParser(description='Determines a valid component registration order')
    parser.add_argument('paths', nargs='+',
                        help='One or more paths specifying which components to use in the ordering. '
                             'Can be a path to a .tar.gz file or JSON file. '
                             'Can also be a path to directory containing .tar.gz and/or JSON files.')
    parser.add_argument('--for-single-component', '-c')
    return parser.parse_args()



def get_paths_from_cmdline_args(args):
    paths = []
    for arg in args.paths:
        if os.path.isfile(arg):
            paths.append(arg)
        elif os.path.isdir(arg):
            for file_name in os.listdir(arg):
                full_path = os.path.join(arg, file_name)
                paths.append(full_path)

    json_paths = []
    targz_paths = []
    for file_path in paths:
        if file_path.endswith('.json'):
            json_paths.append(file_path)
        elif file_path.endswith('.tar.gz'):
            targz_paths.append(file_path)

    return json_paths, targz_paths



def get_components(json_paths, targz_paths):
    json_descriptors = load_descriptors_from_json_files(json_paths)
    targz_descriptors = load_descriptors_from_targz_files(targz_paths)
    all_descriptors = json_descriptors + targz_descriptors
    components = [Component(path, desc) for path, desc in all_descriptors]
    return components



def load_descriptors_from_json_files(json_file_paths):
    descriptors = []
    for path in json_file_paths:
        with open(path) as f:
            descriptor = json.load(f)
            descriptors.append((path, descriptor))
    return descriptors


def load_descriptors_from_targz_files(targz_file_paths):
    num_files = len(targz_file_paths)
    if num_files == 0:
        return []
    if num_files == 1:
        return [load_descriptor_from_archive(targz_file_paths[0])]
    # Decompressing large components can be slow.
    pool = multiprocessing.Pool(min(num_files, multiprocessing.cpu_count()))
    return pool.map(load_descriptor_from_archive, targz_file_paths)



def load_descriptor_from_archive(path):
    with tarfile.open(path) as tar_file:
        descriptor_dir_contents = get_descriptor_tar_dir_content(tar_file)
        if not descriptor_dir_contents:
            raise Exception('No descriptor directory within %s', path)

        for tar_member in descriptor_dir_contents:
            if tar_member.name.endswith('.json'):
                return load_descriptor_from_tar(tar_file, tar_member)

        member = descriptor_dir_contents[0]
        return load_descriptor_from_tar(tar_file, member)


def get_descriptor_tar_dir_content(tar_file):
    descriptor_dir_contents = []
    for tar_member in tar_file.getmembers():
        if parent_dir_name(tar_member.name) != 'descriptor':
            continue

        file_name = os.path.basename(tar_member.name)
        if file_name == 'descriptor.json':
            # Found descriptor file with expected name, so we are done.
            return [tar_member]
        else:
            descriptor_dir_contents.append(tar_member)
    return descriptor_dir_contents



def parent_dir_name(path):
    dirs = os.path.dirname(path)
    return os.path.basename(dirs)


def load_descriptor_from_tar(tar_file, tar_member):
    descriptor_file = tar_file.extractfile(tar_member)
    return tar_file.name, json.load(descriptor_file)


def get_component_registration_order(components):
    dependency_graph = get_dependencies(components)
    return topo_sort(dependency_graph)


def get_registration_order_for_single_component(component_path, all_components):
    target_component = next((c for c in all_components if c.path == component_path), None)
    if target_component is None:
        sys.exit('Couldn\'t find specified component.')
    dependency_graph = get_dependencies(all_components)
    sub_graph = prune_graph(target_component, dependency_graph)
    return topo_sort(sub_graph)


def prune_graph(terminal_node, graph):
    nodes_to_keep = set()
    nodes_to_check = {terminal_node}
    while nodes_to_check:
        node = nodes_to_check.pop()
        nodes_to_keep.add(node)
        nodes_to_check.update(graph.predecessors(node))

    return graph.create_sub_graph(nodes_to_keep)


def get_dependencies(components):
    graph = Graph()
    add_pipeline_obj_dependencies(
        components,
        lambda c: c.provided_algorithms,
        lambda c: c.required_algorithms,
        graph
    )
    add_pipeline_obj_dependencies(
        components,
        lambda c: c.provided_actions,
        lambda c: c.required_actions,
        graph
    )
    add_pipeline_obj_dependencies(
        components,
        lambda c: c.provided_tasks,
        lambda c: c.required_tasks,
        graph
    )
    return graph


def add_pipeline_obj_dependencies(components, to_provides_fn, to_required_fn, graph):
    provided_items_index = create_provided_items_index(components, to_provides_fn)
    missing_dependencies = []
    for component in components:
        graph.add_node(component)
        for required_item in to_required_fn(component):
            try:
                other_component = provided_items_index[required_item.upper()]
                graph.add_edge(component, other_component)
            except KeyError:
                missing_dependencies.append((required_item, component))

    if missing_dependencies:
        missing_deps_str = '\n'.join('%s required by %s' % d for d in missing_dependencies)
        sys.exit('Error: Unable to locate the following dependencies:\n' + missing_deps_str)



def create_provided_items_index(components, to_provides_fn):
    index = {}
    for component in components:
        for key in to_provides_fn(component):
            index[key.upper()] = component
    return index


class Component(object):
    PREDEFINED_ACTIONS = {'OCV GENERIC MARKUP ACTION'}
    PREDEFINED_TASKS = {'OCV GENERIC MARKUP TASK'}

    def __init__(self, path, descriptor):
        self.path = path
        self.name = descriptor['componentName']

        self.provided_algorithms = set()
        algo_element = descriptor.get('algorithm')
        if algo_element:
            self.provided_algorithms.add(algo_element['name'])

        actions = descriptor.get('actions', ())
        algo_refs = {a['algorithm'] for a in actions}
        self.required_algorithms = algo_refs - self.provided_algorithms
        self.provided_actions = {a['name'] for a in actions}

        tasks = descriptor.get('tasks', ())
        action_refs = {a for t in tasks for a in t['actions']}
        self.required_actions = action_refs - self.provided_actions - Component.PREDEFINED_ACTIONS
        self.provided_tasks = {t['name'] for t in tasks}

        pipelines = descriptor.get('pipelines', ())
        task_refs = {t for p in pipelines for t in p['tasks']}
        self.required_tasks = task_refs - self.provided_tasks - Component.PREDEFINED_TASKS

    def __repr__(self):
        return self.name

    # Object needs to be hashable in order to use it as a dictionary key,
    # which is how the graph is stored.
    def __hash__(self):
        return hash(self.name)

    # Object will be stored in a heap,
    # so the comparison operators need to be defined.
    def __cmp__(self, other):
        return cmp(self.name, other.name)



def topo_sort(graph):
    """
    The algorithm keeps track of all the nodes that have no outgoing edges.
    Of the nodes with no outgoing edges, the one with the minimum value is
    removed from the graph and added the list containing the topologically sorted
    nodes.
    Once the node is removed, we look for any nodes that as a result no longer
    have any outgoing edges.

    :return: Topologically sorted list of nodes with ties broken by their natural ordering
    """
    topo_sorted_nodes = []
    # Keep track of nodes with no outgoing edges. Nodes with no outgoing edges either have no
    # dependencies, or the the dependencies have already been added to topo_sorted_nodes.
    nodes_with_no_successor = [n for n in graph.get_node_set() if not graph.successors(n)]
    # Use a heap instead of a regular list so that if there is a tie in the topological ordering,
    # the elements' natural ordering will be used to break the tie.
    heapq.heapify(nodes_with_no_successor)
    while nodes_with_no_successor:
        current_node = heapq.heappop(nodes_with_no_successor)
        topo_sorted_nodes.append(current_node)
        # current_node has been resolved, now we need to remove its incoming edges
        predecessors = graph.predecessors(current_node)
        for predecessor in predecessors:
            graph.remove_edge(predecessor, current_node)
            if not graph.successors(predecessor):
                # current_node was predecessor's last dependency, so now all of
                # predecessor's dependencies have been resolved.
                heapq.heappush(nodes_with_no_successor, predecessor)

    cycle_edges = graph.get_edges()
    if cycle_edges:
        edges_str = '\n'.join('%s -> %s' % edge for edge in cycle_edges)
        sys.exit('Error: There is no valid registration order because the following dependencies are circular:\n'
                 + edges_str)
    return topo_sorted_nodes



class Graph(object):
    def __init__(self, **adjacency_list):
        self._adjacency_list = dict()
        if adjacency_list:
            self._adjacency_list.update(adjacency_list)

    def get_node_set(self):
        node_set = set(self._adjacency_list.keys())
        for dest_nodes in self._adjacency_list.values():
            node_set.update(dest_nodes)
        return node_set

    def get_edges(self):
        edges = set()
        for src_node, dest_nodes in self._adjacency_list.iteritems():
            for dest in dest_nodes:
                edges.add((src_node, dest))
        return edges

    def successors(self, target_node):
        return self._adjacency_list.get(target_node, set())

    def predecessors(self, target_node):
        predecessors = set()
        for src_node, dest_nodes in self._adjacency_list.iteritems():
            if target_node in dest_nodes:
                predecessors.add(src_node)
        return predecessors

    def remove_edge(self, src_node, dest_node):
        existing_dest_nodes = self._adjacency_list.get(src_node)
        if existing_dest_nodes:
            existing_dest_nodes.discard(dest_node)

    def create_sub_graph(self, nodes):
        sub_graph = Graph()
        for node in nodes:
            sub_graph.add_node(node)

        for src, dest in self.get_edges():
            if src in nodes and dest in nodes:
                sub_graph.add_edge(src, dest)
        return sub_graph

    def add_edge(self, src_node, dest_node):
        self._adjacency_list.setdefault(src_node, set()).add(dest_node)

    def add_node(self, node):
        self._adjacency_list.setdefault(node, set())

    def __repr__(self):
        return repr(self._adjacency_list)


if __name__ == '__main__':
    main()
