#!/usr/bin/perl

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

use strict;
use warnings;
use MPF::Jenkins;

if ((@ARGV) != 3) {
	MPF::Jenkins::printFatal("This script was not invoked with the correct number of command line arguments.\n");
	MPF::Jenkins::printFatal("perl $0 <mpf-path> <mpf-log-path> <hostname>\n");
	MPF::Jenkins::printFatal("Got: ".((@ARGV))." arguments.\n");
	MPF::Jenkins::fatalExit();
}

my $mpfPath	   = $ARGV[0];
my $mpfLogPath = $ARGV[1];
my $hostname   = $ARGV[2];

MPF::Jenkins::printDebug("The MPF path supplied as: $mpfPath\n");
MPF::Jenkins::printDebug("The log path supplied as: $mpfLogPath\n");
MPF::Jenkins::printDebug("The hostname supplied as: $hostname\n");

if ($hostname eq "master") {
	MPF::Jenkins::printInfo("The hostname was returned as 'master' by jenkins, using jenkins-mpf-1.mitre.org instead.\n");
	$hostname = "jenkins-mpf-1.mitre.org";
}

MPF::Jenkins::printDebug("Compiling Node Manager from source.\n");
MPF::Jenkins::mavenCompileNodeManager($mpfPath);

MPF::Jenkins::printDebug("Installing the MPF runtime environment profile.\n");
MPF::Jenkins::installProfile($mpfPath, $mpfLogPath, $hostname);

MPF::Jenkins::printDebug("Installing Node Manager.\n");
MPF::Jenkins::installNodeManager($mpfPath);

MPF::Jenkins::printDebug("Running Node Manager.\n");
MPF::Jenkins::runNodeManager();

MPF::Jenkins::printDebug("System status.\n");
MPF::Jenkins::getSystemStatus();

MPF::Jenkins::printInfo("Changing directory to $mpfPath");
chdir $mpfPath;


