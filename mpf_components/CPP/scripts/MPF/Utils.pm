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

# sudo yum install perl-File-Find-Rule.noarch
use File::Find::Rule;

use File::Basename;
use Cwd qw();
# use Data::Dumper;

# define the package.
package MPF::Utils;

sub findAvailableComponentPaths {
    # Sanity check available vs. desired components

    my (@componentsSpecified) = @_;

    my (@componentPaths) = findComponentPaths();

    my %componentsFoundMap; # map component names to directories
    map { $componentsFoundMap{File::Basename::basename($_)} = $_ } @componentPaths;
    # print "[DEBUG] Components found map: \n", Data::Dumper->Dump([%componentsFoundMap]);

    my @componentsFound = keys(%componentsFoundMap);
    # print "[DEBUG] Components found: ", join(", ", @componentsFound), "\n";

    if (@componentsSpecified) {
        # print "[DEBUG] Components specified: ", join(", ", @componentsSpecified), "\n";

        my $lc = List::Compare->new(\@componentsSpecified, \@componentsFound);
        my @componentsMissing= $lc->get_unique;

        if (@componentsMissing > 0) {
            die "[ERROR] Components missing: ", join(", ", @componentsMissing), "\n";
        }

        return @componentsFoundMap{@componentsSpecified};
    } else {
        print "[INFO] All components will be built.\n"; # DEBUG
        return values(%componentsFoundMap); # build everything
    }
}

sub findComponentPaths {
    my @componentPaths = File::Find::Rule->file->name('descriptor.json')->mindepth(2)->relative()->in(getDetectionPath());
    # print("[DEBUG] Found descriptors in:\n", join("\n", @componentPaths), "\n");

    return map { File::Basename::dirname($_) } @componentPaths;
}

sub findCmakelistsPaths {
    my @cmakelistsPaths = File::Find::Rule->file->name('CMakeLists.txt')->mindepth(2)->maxdepth(2)->relative()->in(getDetectionPath());
    # print("[DEBUG] Found CMakeLists.txt in:\n", join("\n", @cmakelistsPaths), "\n");

    return map { File::Basename::dirname($_) } @cmakelistsPaths;
}

sub getDetectionPath {
    my $cwdPath = Cwd::cwd();
    # print "$cwdPath\n"; # DEBUG

    return File::Spec->catfile($cwdPath, '../detection');
    # print "$detectionPath\n"; # DEBUG
}

1; # module initialized successfully