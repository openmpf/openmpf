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

use utf8;
use JSON;
use File::Basename;

my $mpfVersion = "0.10.0";
my $ansibleRepoPath = "/mpfdata/ansible/install/repo";

# The following hashes use the name of the packaging element as a key, and the source path
# of the element as the value.  The names of desired packages in a packaging configuration file
# must match one of these keys.

my %mpfCoreRPMs = ('markup' => "trunk/markup/target/rpm/mpf-markup/RPMS/noarch",
		   'nodeManager' => "trunk/node-manager/target/rpm/mpf-nodeManager/RPMS/noarch",
		   'protobuf' => "trunk/protobuf/target/rpm/mpf-protobuf/RPMS/noarch",
		   'video-overlay' => "trunk/video-overlay/target/rpm/mpf-video-overlay/RPMS/noarch",
		   'workflowManager' => "trunk/workflow-manager/target/rpm/mpf-workflowManager/RPMS/noarch",
		   'java-component-api' => "../openmpf-java-component-sdk/java-component-api/target/rpm/mpf-java-component-api/RPMS/noarch",
		   'java-component-executor' => "trunk/detection/executor/java/target/rpm/mpf-java-component-executor/RPMS/noarch");

my %mpfCoreTars = ('mpf-install-dep' => "trunk/mpf-install/target");


####################################
# Start processing here.
####################################

if ((@ARGV) != 4) {
    MPF::Jenkins::printFatal("This script was invoked with an incorrect number of command line arguments.\n");
    MPF::Jenkins::printFatal("perl $0 <mpf-path> <git-branch> <build-num> <config-file-path>\n");
    MPF::Jenkins::printFatal("Got: ".((@ARGV))." arguments.\n");
    MPF::Jenkins::fatalExit();
}

my $mpfPath	    = $ARGV[0];
my $gitBranch	= $ARGV[1];
my $buildNum	= $ARGV[2];
my $filePath    = $ARGV[3];


MPF::Jenkins::printDebug("The MPF path         supplied as: $mpfPath\n");
MPF::Jenkins::printDebug("The Git branch       supplied as: $gitBranch\n");
MPF::Jenkins::printDebug("The build num        supplied as: $buildNum\n");
MPF::Jenkins::printDebug("The config file path supplied as: $filePath\n");

my $pwd = `pwd`;
my $json;

{
# Open the file and read in the data
    local $/;   # enables slurp mode
    open my $fh, '<', $filePath or die "Failed to open $filePath";
    $json = <$fh>;
    close $fh;
}

my $data = decode_json($json);
my $packageTag = $data->{'packageTag'};

print "\nCreating Custom package: Package Tag = " . $packageTag . "\n";

	
my @tokens = split /\//, $gitBranch;
$gitBranch = $tokens[$#tokens];
	
my $workspace = "/mpfdata/openmpf-$packageTag-package/mpf-release";
my $rpmDest = "$workspace/install/repo/rpms/mpf";
my $tarDest = "$workspace/install/repo/tars/mpf";

(my $ffmpeg_path = qx(which ffmpeg)) =~ s/\n//g;
(my $ffprobe_path = qx(which ffprobe)) =~ s/\n//g;
(my $ffserver_path = qx(which ffserver)) =~ s/\n//g;

my %ffmpeg = ("ffmpeg","$ffmpeg_path","ffprobe","$ffprobe_path","ffserver","$ffserver_path");

# Create the workspace. Open up its permissions since we won't be doing the rest of the
# staging as root.
MPF::Jenkins::printInfo("Creating the workspace directory: $workspace\n");
system "sudo mkdir -p $workspace";
system "sudo chmod 777 $workspace";
if (!(-d $workspace)) {
    MPF::Jenkins::printFatal("The workspace folder ($workspace) was not created successfully.\n");
    MPF::Jenkins::fatalExit();
}
MPF::Jenkins::printInfo("Creating the RPM destination directory: $rpmDest\n");
system "mkdir -p $rpmDest";
if (!(-d $rpmDest)) {
    MPF::Jenkins::printFatal("The RPM destination folder ($rpmDest) was not created successfully.\n");
    MPF::Jenkins::fatalExit();
}

MPF::Jenkins::printInfo("Creating the TAR destination directory: $tarDest\n");
system "mkdir -p $tarDest";
if (!(-d $tarDest)) {
    MPF::Jenkins::printFatal("The RPM destination folder ($tarDest) was not created successfully.\n");
    MPF::Jenkins::fatalExit();
}

# Copy the packaging config file to the workspace

system "cp $filePath $workspace";
	
# Copy the base Ansible folder hierarchy.

MPF::Jenkins::printInfo("Copying the base Ansible folder hierarchy ($mpfPath/trunk/ansible) to the workspace ($workspace).\n");
if (!(-d "$mpfPath/trunk/ansible")) {
    MPF::Jenkins::printFatal("The ansible folder ($mpfPath/trunk/ansible) did not exist in the MPF path ($mpfPath).\n");
    MPF::Jenkins::fatalExit();
}
system "cp -r $mpfPath/trunk/ansible/* $workspace";
	
# Copy the external RPMs and Tars to the Ansible folder hierarchy.

MPF::Jenkins::printInfo("Copying the external (non-MPF) RPMs ($ansibleRepoPath) into workspace ($workspace/install).\n");
if (!(-d "$ansibleRepoPath")) {
    MPF::Jenkins::printFatal("The ansible folder ($ansibleRepoPath) did not exist.\n");
    MPF::Jenkins::fatalExit();
}

# Create the FFmpeg archive staging folder
MPF::Jenkins::printInfo("Creating the FFmpeg destination folder: $workspace/install/repo/tars/ffmpeg\n");
system "mkdir -p $workspace/install/repo/tars/ffmpeg";
if (!(-d "$workspace/install/repo/tars/ffmpeg")) {
    MPF::Jenkins::printFatal("The FFmpeg destination folder ($workspace/install/repo/tars/ffmpeg) was not created successfully.\n");
    MPF::Jenkins::fatalExit();
    }

# Create the FFmpeg archive
MPF::Jenkins::printInfo("Copying FFmpeg files into destination ($workspace/install/repo/tars/ffmpeg).\n");
while ((my $name, my $file) = each(%ffmpeg))  {
    if (!(-e "$file")) {
        MPF::Jenkins::printFatal("The file $name did not exist at the path ($file).\n");
        MPF::Jenkins::fatalExit();
        }
    system "cp $file $workspace/install/repo/tars/ffmpeg/";
    }

# Create the FFmpeg tar.gz
MPF::Jenkins::printInfo("Creating FFmpeg tar.gz package.\n");
	if (!(-d "$workspace/install/repo/tars/ffmpeg")) {
        MPF::Jenkins::printFatal("The FFmpeg folder ($workspace/install/repo/tars/ffmpeg) did not exist.\n");
        MPF::Jenkins::fatalExit();
	}
	system "tar -C $workspace/install/repo/tars -pczf $workspace/install/repo/tars/ffmpeg.tar.gz ffmpeg";

# Remove the FFmpeg directory
MPF::Jenkins::printInfo("Removing FFmpeg destination directory: $workspace/install/repo/tars/ffmpeg\n");
    if (!(-d "$workspace/install/repo/tars/ffmpeg")) {
        MPF::Jenkins::printInfo("The FFmpeg folder ($workspace/install/repo/tars/ffmpeg) did not exist.\n");
    }
    system "rm -rf $workspace/install/repo/tars/ffmpeg";

# Create the MPF admin python script tar.gz
MPF::Jenkins::printInfo("Creating MPF admin python script tar.gz package.\n");
	if (!(-d "$mpfPath/trunk/bin/mpf-scripts")) {
		MPF::Jenkins::printFatal("The mpf-scripts folder ($mpfPath/trunk/bin/mpf-scripts) did not exist.\n");
		MPF::Jenkins::fatalExit();
	}
	system "tar -C $mpfPath/trunk/bin -pczf $tarDest/mpf-admin-scripts-$mpfVersion.tar.gz mpf-scripts";

system "cp -a $ansibleRepoPath/files $workspace/install/repo/";
system "cp -a $ansibleRepoPath/rpms $workspace/install/repo/";
system "cp -a $ansibleRepoPath/pip $workspace/install/repo/";

system "cp $ansibleRepoPath/tars/apache* $workspace/install/repo/tars/";

# Process the MPF Core RPMs
my $elem;
foreach $elem ( @{ $data->{'MPF_Core_RPMs'} }) {
    print "MPF Core RPM = $elem\n";
    my $path = $mpfCoreRPMs{$elem};
    system "cp $mpfPath/$path/mpf-$elem*.rpm $rpmDest";
}

# Process the MPF Core Tars
foreach $elem ( @{ $data->{'MPF_Core_Tars'} }) {
    print "MPF Core tar = $elem\n";
    my $path = $mpfCoreTars{$elem};
    system "cp $mpfPath/$path/$elem*.tar.gz $tarDest";
}

# Process the MPF Component Tars
MPF::Jenkins::printInfo("Copying plugin packages from $mpfPath/mpf-component-build/plugin-packages.\n");
my @plugins = glob("$mpfPath/mpf-component-build/plugin-packages/*.tar.gz");
foreach my $plugin (@plugins) {
    my($filename, $dirs, $suffix) = fileparse($plugin, ".tar.gz");
    system "cp $dirs$filename.tar.gz $tarDest/$filename-$mpfVersion.tar.gz";
}

# Tar everything up!
MPF::Jenkins::printInfo("Tarballing the Ansible workspace and placing in the releases folder.\n");

chdir $workspace."/..";

if (!(-d "mpf-release")) {
    MPF::Jenkins::printFatal("The workspace directory mpf-release does not exist.\n");
    MPF::Jenkins::fatalExit();
}
my $release_tree = `tree mpf-release`;
MPF::Jenkins::printDebug("Ansible release directory structure:'\n$release_tree\n");

system "sudo tar -pczf /mpfdata/releases/openmpf-$packageTag-$mpfVersion+$gitBranch-$buildNum.tar.gz mpf-release";	
	
# Remove the workspace.
MPF::Jenkins::printInfo("Removing the workspace.\n");
system "sudo rm -rf $workspace";
chdir $pwd;
	
if(-f "/mpfdata/releases/openmpf-$packageTag-$mpfVersion+$gitBranch-$buildNum.tar.gz") {
    MPF::Jenkins::printInfo("Successfully generated /mpfdata/releases/openmpf-$data->{'packageTag'}-$mpfVersion+$gitBranch-$buildNum.tar.gz.\n");
} else {
    MPF::Jenkins::printFatal("\n\nFailed to generate /mpfdata/releases/openmpf-$packageTag-$mpfVersion+$gitBranch-$buildNum.tar.gz.\n\n\n");
    MPF::Jenkins::fatalExit();
}

