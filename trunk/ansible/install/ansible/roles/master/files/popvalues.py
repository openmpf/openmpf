#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2019 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2019 The MITRE Corporation                                      #
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

# Populate these values into $MPF_HOME/data/components.json
# "dateUploaded":<value>,
# "fullUploadedFilePath":<value>,
# "packageFileName":<value>

import sys
import json
import os

mpf_home = sys.argv[1]
mpf_component_name = sys.argv[2]
mpf_component_filepath = sys.argv[3]

# Load the component config file $MPF_HOME/data/components.json
try:
    with open(mpf_home + '/data/components.json', 'r') as component_file:
        component_data = json.load(component_file)
except:
    print u'Could not open {0}/data/components.json'.format(mpf_home)
else:
    component_file.close()

# find the component in components.json
for n in range(0, len(component_data)):
    if mpf_component_name in component_data[n]['componentName']:
        # Found a match for an existing component
        if not component_data[n]['dateUploaded'] and component_data[n]["componentState"] == 'REGISTERED':

            # Set upload date to registration date
            component_data[n]['dateUploaded'] = component_data[n]['dateRegistered']

            # Set packageFileName
            component_data[n]['packageFileName'] = os.path.basename(mpf_component_filepath)

            # Set fullUploadedFilePath
            component_data[n]['fullUploadedFilePath'] = mpf_component_filepath

            # Write component_data to components.json
            try:
                component_file = open(mpf_home + '/data/components.json', 'w')
                json.dump(component_data, component_file,indent=4, separators=(',', ': '))
            except:
                print u'Could not open {0}/data/components.json'.format(mpf_home)
            else:
                component_file.close()
            break
        else:
            print '\nCheck components.json file.'
            print u'\nComponent {0} should be in state REGISTERED and is in state: {1}'.format(component_data[n]['componentName'],
                                                                                               component_data[n]['componentState'])
            print u'\nComponent {0} should have no dateUploaded value and has a dateUploaded value of: {1}'.format(component_data[n]['componentName'],
                                                                                                                   component_data[n]['dateUploaded'])
            break

else:
    print 'Check components.json file. The component should have already been registered but was not found.'
