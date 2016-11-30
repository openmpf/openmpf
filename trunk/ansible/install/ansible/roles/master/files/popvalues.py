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

# Populate these values into $MPF_HOME/share/components/components.json
#"dateUploaded":<value>,
#"fullUploadedFilePath":<value>,
#"packageFileName":<value>

import sys
import json
import os

mpf_home = sys.argv[1]
external_component_name = sys.argv[2]
external_component_filepath = sys.argv[3]

# Load the component config file $MPF_HOME/share/components/components.json
try:
    with open(mpf_home + '/share/components/components.json', 'r') as component_file:
        component_data = json.load(component_file)
except:
    print 'Could not open ' + mpf_home + '/share/components/components.json'
else:
    component_file.close()

# find the component in components.json
for n in range(0, len(component_data)):
    if(component_data[n]['componentName'].lower() == external_component_name.lower()):
        #Found a match for an existing component

        if not component_data[n]['dateUploaded'] and component_data[n]["componentState"] == 'REGISTERED':
            # Set upload date to registration date
            component_data[n]['dateUploaded'] = component_data[n]['dateRegistered']

            # Set packageFileName
            component_data[n]['packageFileName'] = os.path.basename(external_component_filepath)

            # Set fullUploadedFilePath
            component_data[n]['fullUploadedFilePath'] = ''.join([mpf_home, '/share/components/', component_data[n]['packageFileName']])

            # Write component_data to components.json
            try:
                component_file = open(mpf_home + '/share/components/components.json', 'w')
                json.dump(component_data, component_file)
            except:
                print 'Could not open ' + mpf_home + '/share/components/components.json for writing.'
            else:
                component_file.close()
            break
        else:
            print '\nCheck components.json file.'
            print '\nComponent ' + component_data[n]['componentName'] + ' should be in state REGISTERED and is in state:', component_data[n]['componentState']
            print '\nComponent ' + component_data[n]['componentName'] + ' should have no dateUploaded value and has a dateUploaded value of:', component_data[n]['dateUploaded']
            break

else:
    print 'Check components.json file. The component should have already been registered but was not found.'