#! /usr/bin/env python3
#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2020 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2020 The MITRE Corporation                                      #
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

import argh
import argh.utils

from . import mpf_clean
from . import mpf_nodes
from . import mpf_sys
from . import mpf_users
from . import mpf_util


def main():
    parser = PrintHelpOnErrorParser()
    parser.add_commands(mpf_sys.COMMANDS)
    parser.add_commands(mpf_users.COMMANDS)
    parser.add_commands(mpf_clean.COMMANDS)
    parser.add_commands(mpf_nodes.COMMANDS)

    subs = argh.utils.get_subparsers(parser)
    subs.help = 'For command specific arguments and help run: %(prog)s <command> --help\n'
    subs.metavar = 'Commands'

    if len(sys.argv) < 2:
        parser.error('too few arguments')

    try:
        parser.dispatch()
    except mpf_util.MpfError as err:
        parser.exit(mpf_util.MsgUtil.red('Error: %s' % err))


class PrintHelpOnErrorParser(argh.ArghParser):
    def error(self, message):
        if len(sys.argv) > 1:
            super(PrintHelpOnErrorParser, self).error(message)
        else:
            # no command provided
            self.print_help()
            self.exit(2, mpf_util.MsgUtil.red('\n%s: Error: %s\n' % (self.prog, message)))


if __name__ == '__main__':
    main()
