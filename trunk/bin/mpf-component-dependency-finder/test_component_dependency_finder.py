#! /usr/bin/env python

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


import unittest
import component_dependency_finder as dep_finder


class TestDependencyFinder(unittest.TestCase):

    def test_simple_topo_sort(self):
        graph = dep_finder.Graph(
            O={'LL', 'E'},
            E={'H'}
        )
        expected = ['H', 'E', 'LL', 'O']
        actual = dep_finder.topo_sort(graph)
        self.assertEqual(expected, actual)


    def test_error_when_graph_has_cycle(self):
        graph = dep_finder.Graph(
            A={'B', 'C', 'E'},
            B={'D'},
            C={'D'},
            D={'A', 'E', 'F', 'G'}
        )
        self.assertRaises(SystemExit, dep_finder.topo_sort, graph)


    def test_topo_sort1(self):
        graph = dep_finder.Graph(
            A={'B', 'C', 'E'},
            B={'D'},
            C={'D'},
            D={'E', 'F', 'G'}
        )

        expected = ['E', 'F', 'G', 'D', 'B', 'C', 'A']
        actual = dep_finder.topo_sort(graph)
        self.assertEqual(expected, actual)


    def test_topo_sort2(self):
        graph = dep_finder.Graph(
            Q={'B', 'C', 'E'},
            B={'D'},
            C={'D'},
            D={'E', 'F', 'G', 'A'}
        )

        expected = ['A', 'E', 'F', 'G', 'D', 'B', 'C', 'Q']
        actual = dep_finder.topo_sort(graph)
        self.assertEqual(expected, actual)


    def test_disconnected_graph1(self):
        graph = dep_finder.Graph(
            D={'E', 'G'},
            E={'F', 'H'},
            F={'I'},
            H={'J'},
            M={},
            N={}
        )
        expected = ['G', 'I', 'F', 'J', 'H', 'E', 'D', 'M', 'N']
        actual = dep_finder.topo_sort(graph)
        self.assertEqual(expected, actual)


    def test_disconnected_graph2(self):
        graph = dep_finder.Graph(
            D={'E', 'G'},
            E={'F', 'M'},
            F={'H'},
            M={'J'},
            I={}
        )
        expected = ['G', 'H', 'F', 'I', 'J', 'M', 'E', 'D']
        actual = dep_finder.topo_sort(graph)
        self.assertEqual(expected, actual)



    def test_pruning(self):
        graph = dep_finder.Graph(
            cvface={'mog'},
            dlib={'mog'},
            cvperson={'mog'},
            sphinx={},
            extras={'cvface', 'dlib', 'mog', 'cvperson', 'sphinx'},
            subsense={}
        )
        sub_graph = dep_finder.prune_graph('subsense', graph)
        self.assertEqual({'subsense'}, sub_graph.get_node_set())
        self.assertEqual(set(), sub_graph.get_edges())

        sub_graph = dep_finder.prune_graph('sphinx', graph)
        self.assertEqual({'extras', 'sphinx'}, sub_graph.get_node_set())
        self.assertEqual({('extras', 'sphinx')}, sub_graph.get_edges())

        sub_graph = dep_finder.prune_graph('cvface', graph)
        self.assertEqual({'extras', 'cvface'}, sub_graph.get_node_set())
        self.assertEqual({('extras', 'cvface')}, sub_graph.get_edges())

        sub_graph = dep_finder.prune_graph('mog', graph)
        self.assertEqual({'mog', 'cvface', 'dlib', 'cvperson', 'extras'}, sub_graph.get_node_set())
        expected_edges = {
            ('extras', 'cvface'),
            ('extras', 'dlib'),
            ('extras', 'cvperson'),
            ('extras', 'mog'),
            ('cvface', 'mog'),
            ('dlib', 'mog'),
            ('cvperson', 'mog')
        }
        self.assertEqual(expected_edges, sub_graph.get_edges())


    def test_pruning2(self):
        graph = dep_finder.Graph(
            D={'E', 'G'},
            E={'F', 'M'},
            F={'H'},
            M={'J'},
            I={}
        )
        terminal_node = 'F'
        sub_graph = dep_finder.prune_graph(terminal_node, graph)
        nodes = sub_graph.get_node_set()

        self.assertEqual(3, len(nodes))
        self.assertEqual({'D', 'E', 'F'}, nodes)
        expected_edges = {
            ('D', 'E'),
            ('E', 'F')
        }
        self.assertEqual(expected_edges, sub_graph.get_edges())


    def test_component_ordering(self):
        c1 = dep_finder.Component('test/path', dict(
            componentName='Component1',
            algorithm=dict(
                name='Algo1'
            ),
        ))

        c2 = dep_finder.Component('test/path2', dict(
            componentName='Component2',
            algorithm=dict(
                name='Algo2'
            ),
            actions=[
                dict(
                    name='Action2',
                    algorithm='Algo1'
                )
            ],
            tasks=[
                dict(
                    name='Task3',
                    actions=['Action2', 'Action3']
                )
            ]
        ))

        c3 = dep_finder.Component('test/path3', dict(
            componentName='Component3',
            algorithm=dict(
                name='Algo3'
            ),
            actions=[
                dict(
                    name='Action3',
                    algorithm='Algo1'
                )
            ]
        ))
        actual = dep_finder.get_component_registration_order((c1, c2, c3))
        self.assertEqual([c1, c3, c2], actual)


    def test_unresolved_dependency(self):
        c1 = dep_finder.Component('test/path', dict(
            componentName='Component1',
            algorithm=dict(
                name='Algo1'
            ),
        ))

        c2 = dep_finder.Component('test/path2', dict(
            componentName='Component2',
            algorithm=dict(
                name='Algo2'
            ),
            actions=[
                dict(
                    name='Action2',
                    algorithm='MISSING_ALGO'
                )
            ]
        ))
        self.assertRaises(SystemExit,  dep_finder.get_component_registration_order, [c1, c2])



if __name__ == '__main__':
    unittest.main()
