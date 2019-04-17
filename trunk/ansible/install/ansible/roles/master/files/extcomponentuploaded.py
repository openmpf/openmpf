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
# "packageFileName": <value>,
# "componentName": <value>,
# "componentState": "UPLOADED",
# "fullUploadedFilePath": <value>,
# "dateUploaded": <value>


import sys
import json
import time
import os

mpf_home = sys.argv[1]
external_component_name = sys.argv[2]
external_component_filepath = sys.argv[3]


def setMinComponent(cname, cpath, cfname, cstate, cupdate):
    component_dict = {"componentName":cname,"fullUploadedFilePath":cpath,"packageFileName":cfname,"componentState":cstate,"dateUploaded":cupdate}
    return component_dict


try:
    with open(mpf_home + '/data/components.json', 'r') as component_file:
        component_data = json.load(component_file)
except:
    print u'Could not open {0}/data/components.json'.format(mpf_home)
else:
    component_file.close()

add_component = 0

# components.json file is empty. Add an initial empty placeholder component
if len(component_data) == 0:
    add_component = 1
else:
    for n in range(0, len(component_data)):
        if component_data[n]["componentName"].lower() == external_component_name.lower():
            # Found a match for an existing component
            break
    else:
        # Did not find a match for an existing component
        add_component = 1


if add_component == 1:
    component_data.append((setMinComponent(external_component_name,
                                           external_component_filepath,
                                           os.path.basename(external_component_filepath),
                                           "UPLOADED",
                                           # epoch time in milliseconds
                                           int(time.time()*1000))))

# Write component_data to components.json
try:
    component_file = open(mpf_home + "/data/components.json", "w")
    json.dump(component_data, component_file, indent=4, separators=(',', ': '))
except:
    print u'Could not open {0}/data/components.json'.format(mpf_home)
else:
    component_file.close()
