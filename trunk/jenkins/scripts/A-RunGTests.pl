#!/usr/bin/perl

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

use strict;
use warnings;
use MPF::Jenkins;

if ((@ARGV) != 1) {
	MPF::Jenkins::printFatal("This script was not invoked with the correct number of command line arguments.\n");
	MPF::Jenkins::printFatal("perl $0 <mpf-path>\n");
	MPF::Jenkins::printFatal("Got: ".((@ARGV))." arguments.\n");
	MPF::Jenkins::fatalExit();
}

my $mpfPath		= $ARGV[0];

MPF::Jenkins::printDebug("The MPF path supplied as: $mpfPath\n");

MPF::Jenkins::printDebug("Running GTests.\n");
MPF::Jenkins::runGTests($mpfPath);

MPF::Jenkins::printDebug("System status.\n");
MPF::Jenkins::getSystemStatus();
