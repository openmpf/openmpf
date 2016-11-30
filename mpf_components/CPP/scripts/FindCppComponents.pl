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

use File::Basename;
use File::Copy;

# sudo yum install perl-List-Compare.noarch
use List::Compare;

use MPF::Utils;


# What are the desired components?

my @componentsSpecified;

if ((@ARGV) > 1) {
    print "Usage: $0 [\"componentA,componentB,componentC,...\"]\n";
    exit -1;
}

# NOTE: "-DcppComponents=<blank>" is the same as not providing the option
if ((@ARGV) == 1) {
	@componentsSpecified = split(',', $ARGV[0]);
}


# Find available components in source code tree
# The value of cppComponents determines which components will eventually be registered

my @componentPathsFound;
eval { @componentPathsFound = MPF::Utils::findAvailableComponentPaths(@componentsSpecified); };
if ($@) { print $@; exit -1; }
# print "[DEBUG] Component paths found: ", join(", ", @componentPathsFound), "\n";


# Find top-level CMakeLists.txt files in source code tree

my @cmakelistsPaths;
eval { @cmakelistsPaths = MPF::Utils::findCmakelistsPaths(); };
if ($@) { print $@; exit -1; }


# Remove the CMakeLists.txt paths that don't contain a desired component

my %componentPathsFound = map{$_ =>1} @componentPathsFound;
my @cmakelistsPathsDiff = grep(!defined $componentPathsFound{$_}, @cmakelistsPaths);

my %cmakelistsPaths = map{$_ =>1} @cmakelistsPaths;
my @componentPathsFoundDiff = grep(!defined $cmakelistsPaths{$_}, @componentPathsFound);

my @cmakelistsPathsTmp = grep( $componentPathsFound{$_}, @cmakelistsPaths ); # intersection

foreach my $cmakelistsPath (@cmakelistsPathsDiff) {
    # Does this CMakeLists.txt path contain at least one desired component?
    foreach my $componentPath (@componentPathsFoundDiff) {
        # Does cmakelistsPath appear at the start of componentPath?
        if (index($componentPath, $cmakelistsPath) == 0) {
            push @cmakelistsPathsTmp, $cmakelistsPath;
            last;
        }
    }
}

@cmakelistsPaths = @cmakelistsPathsTmp;
# print("[DEBUG] cmakelistsPaths: " . join(",", @cmakelistsPaths) . "\n"); # DEBUG


# Copy template CMakeLists.txt file and append add_subdirectory() entries to the end

my $detectionPath = MPF::Utils::getDetectionPath();
my $cmakelistsTemplate = File::Spec->catfile($detectionPath, 'CMakeLists.tpl');
my $newCmakelists = File::Spec->catfile($detectionPath, 'CMakeLists.txt');

copy($cmakelistsTemplate, $newCmakelists) or die "Copy failed: $!"; # will overwrite existing file

my $content = join "\n", map { 'add_subdirectory(' . $_ . ')' } @cmakelistsPaths;
# print("$content\n"); # DEBUG

open FILE, ">>", $newCmakelists;
print FILE $content;
close FILE;


# Create component listing

my $componentListing = File::Spec->catfile($detectionPath, 'components.lst');

$content = join "\n", map { File::Spec->catfile($detectionPath, $_) } @componentPathsFound;

open FILE, ">", $componentListing;
print FILE $content;
close FILE;