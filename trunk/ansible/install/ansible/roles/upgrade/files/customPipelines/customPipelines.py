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
# file: roles/upgrade/files/customPipelines/customPipelines.py

import os
import glob
import json
from lxml import etree as et


def load_json_from_file(json_filepath):
    """

    :param json_filepath: An absolute filepath to a JSON file. Example: /opt/mpf/plugins/Component/descriptor/descriptor.json
    :return: A deserialized JSON document converted to a Python object.
    """
    try:
        with open(json_filepath, "r") as json_file:
            json_data = json.load(json_file)
    except:
        print 'Could not open filepath {0}'.format(json_filepath)
        json_data = False
    else:
        json_file.close()

    return json_data


def load_xml_from_file(xml_filepath):
    """

    :param xml_filepath: Absolute filepath to an XML file. Example: /opt/mpf/data/Actions.xml
    :return: An ElementTree object.
    """

    # Use custom parser to remove blank text
    custom_parser = et.XMLParser(remove_blank_text=True)

    # Parse the XML file into a tree
    xml_tree = et.parse(source=xml_filepath, parser=custom_parser)

    # Return an ElementTree object parsed from the input file.
    return xml_tree


def get_reference_aatp():
    """

    :return: List of lists with the reference Algorithms, Actions, Tasks, Pipelines present on a system.
    """

    # Get MPF_HOME
    mpf_home = os.getenv('MPF_HOME', '/opt/mpf')

    # set default values
    algorithm_names = []
    action_names = []
    task_names = []
    pipeline_names = []

    # List of generated reference lists
    generate_reference_list = [action_names, task_names, pipeline_names, algorithm_names]

    # Pipeline pieces stored as lists
    pipeline_pieces = [u'actions', u'tasks', u'pipelines']

    # Markup always exists
    algorithm_names.append(u'MARKUPCV')
    action_names.append(u'OCV GENERIC MARKUP ACTION')
    task_names.append(u'OCV GENERIC MARKUP TASK')

    # Iterate through descriptors of installed components to determine baseline Algorithms, Actions, Tasks, Pipelines
    # Set path to search for component descriptors
    descriptor_dirs = ''.join([mpf_home, '/plugins/*/descriptor/descriptor.json'])

    # Generate a list of the  component descriptor files
    descriptors = glob.glob(descriptor_dirs)

    # Read each descriptor and get the Algorithms, Actions, Tasks, Pipelines
    for descriptor in descriptors:
        # Load descriptor json
        descriptor_data = load_json_from_file(descriptor)

        # Get algorithm name in upper case
        if u'algorithm' in descriptor_data:
            algorithm_names.append(descriptor_data[u'algorithm'][u'name'].upper())

        for piece, referenceList in zip(pipeline_pieces, generate_reference_list):
            # Get name in upper case
            if piece in descriptor_data:
                for part in descriptor_data[piece]:
                    referenceList.append(part['name'].upper())

    # Load the components.json file and to get Algorithms, Actions, Tasks, Pipelines from registered components
    # Previous releases of openmpf used path $MPF_HOME/share/components/components.json, releases 0.9+ use $MPF_HOME/data/components.json
    component_data = load_json_from_file(''.join([mpf_home, '/data/components.json']))

    # Scan through the component.json file to add any missing external component Algorithms, Actions, Tasks, Pipelines
    for component in component_data:
        if component['componentState'] == 'REGISTERED':
            if component['algorithmName'] not in algorithm_names and component['algorithmName'] is not None:
                algorithm_names.append(component_data['algorithmName'])

            for piece, referenceList in zip(pipeline_pieces, generate_reference_list):
                if piece in component:
                    for part in component[piece]:
                        if part not in referenceList:
                            referenceList.append(part.upper())

# Return the baseline reference list of Algorithms, Actions, Tasks, Pipelines installed before the upgrade
    return generate_reference_list


# Generate the custom Algorithms, Actions, Tasks, and Pipelines files
def get_custom_aatp(ref_list, installed_file, output_xml_file):
    """

    :param ref_list: list of lists returned from get_reference_aatp().
    :param installed_file: Absolute filepath to the installed file.
    :param output_xml_file: Absolute filepath for the generated custom file.
    """

    # Parse the XML file into a tree
    installed_xml_tree = load_xml_from_file(installed_file)

    # Get the root of the installed file XML tree
    custom_xml_tree_root = installed_xml_tree.getroot()

    # Get the child elements of the installed file XML tree
    custom_xml_tree_children = custom_xml_tree_root.findall("./")

    # For each child element in the XML tree
    for child in custom_xml_tree_children:
        # Get the name of the child element
        child_name = child.get('name')

        # If the child matches anything from the reference set, remove it
        if any(child_name in e for e in ref_list):
            custom_xml_tree_root.remove(child)

    # Write the custom XML file to the specified location
    installed_xml_tree.write(output_xml_file,
                             xml_declaration=True,
                             encoding=installed_xml_tree.docinfo.encoding,
                             pretty_print=True)


def merge_aatp(installed_file, custom_file, merged_file):
    """

    :param installed_file: Absolute filepath to the installed file. Example: /opt/mpf/data/Actions.xml
    :param custom_file: Absolute filepath to the file generated from get_custom_aatp(). Example: /opt/mpf/data/customActions.xml
    :param merged_file: Absolute filepath for the generated merged output file. Example: /opt/mpf/data/Actions.xml
    """

    # Load the installed XML file
    installed_xml_data = load_xml_from_file(installed_file)
    installed_xml_data_root = installed_xml_data.getroot()

    # Load the generated custom XML file
    custom_xml_data = load_xml_from_file(custom_file)
    custom_xml_data_root = custom_xml_data.getroot()
    custom_xml_data_children = custom_xml_data_root.findall("./")

    # Verify the root tags match
    if installed_xml_data_root.tag == custom_xml_data_root.tag:
        # For each custom item
        for item in custom_xml_data_children:
            # Check if the custom item already exists in the installed file. If not, add it.
            if not installed_xml_data_root.findall("{0}[@name='{1}']".format(item.tag, item.get('name'))):
                # Add the custom items to the XML tree under root element
                installed_xml_data_root.append(item)

    # Open the destination file for writing
    with open(merged_file, 'a'):
        # Write back the merged XML file to the specified location
        installed_xml_data.write(merged_file, xml_declaration=True,
                                 encoding=installed_xml_data.docinfo.encoding,
                                 pretty_print=True)
