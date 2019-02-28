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

import sys
from os.path import expanduser, expandvars

# Try to use version in repo before installed version, so that when run in a development environment the
# most up to date version of the component api is used. A developer also might run these tests before
# doing a full build and install.
# On Jenkins the first path will not exist, but that is okay because Jenkins does a full build and install
# before running these tests.
raw_paths = (
    "~/openmpf-projects/openmpf-python-component-sdk/detection/api",
    "~/mpf-sdk-install/python/site-packages",
    "$MPF_SDK_INSTALL_PATH/python/site-packages",
    "$MPF_HOME/python/site-packages"
)

sys.path[0:0] = (expanduser(expandvars(p)) for p in raw_paths)
