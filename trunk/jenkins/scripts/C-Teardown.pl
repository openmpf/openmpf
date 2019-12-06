#!/usr/bin/perl

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

use strict;
use warnings;
use MPF::Jenkins;

if ((@ARGV) != 2) {
	MPF::Jenkins::printFatal("This script was not invoked with the correct number of command line arguments.\n");
	MPF::Jenkins::printFatal("perl $0 <amq-path> <mpf-path>\n");
	MPF::Jenkins::printFatal("Got: ".((@ARGV))." arguments.\n");
	MPF::Jenkins::fatalExit();
}

my $amqPath		= $ARGV[0];
my $mpfPath		= $ARGV[1];

MPF::Jenkins::printDebug("The AMQ path supplied as: $amqPath\n");
MPF::Jenkins::printDebug("The MPF path supplied as: $mpfPath\n");

MPF::Jenkins::printDebug("Teardown.\n");
MPF::Jenkins::stopNodeManager();
MPF::Jenkins::stopActiveMQ($amqPath);
MPF::Jenkins::stopPostgreSQL();
MPF::Jenkins::stopRedis();	#	Only flush redis and don't shut it down.
MPF::Jenkins::cleanRedis();	#	


MPF::Jenkins::printDebug("Teardown of any remaining processes.\n");
MPF::Jenkins::killEverythingElse();


MPF::Jenkins::printDebug("System status.\n");
MPF::Jenkins::getSystemStatus();
