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

import argparse
import os

import argh
import pymysql
import pymysql.constants.ER


def arg_group(*arg_defs):
    """ Creates a single decorator that applies multiple argh.arg decorators for commands with a
    common set of arguments.
    Args:
        *arg_defs (list): List of decorators to apply
    """

    def decorator(fn):
        for arg_def in arg_defs:
            arg_def(fn)
        return fn

    return decorator


sql_args = arg_group(
    argh.arg('--sql-host', default='localhost', help='hostname of the SQL server'),
    argh.arg('--sql-user', default='root',
             help='username used to log in to the SQL server'),
    argh.arg('--sql-password', default='password',
             help="password used to log in to the SQL server")
)


def has_whitespace(string):
    return any(ch.isspace() for ch in string)


class VerifyNoWhiteSpace(argparse.Action):
    """ Ensures that a command line argument does not contain whitespace. Exits with a warning
     if the argument contains whitespace"""

    def __init__(self, option_strings, dest, **kwargs):
        super(VerifyNoWhiteSpace, self).__init__(option_strings, dest, **kwargs)

    def __call__(self, parser, namespace, value, option_string=None):
        arg_name = self.metavar or self.dest
        if not value:
            parser.error('%s can not be the empty string' % arg_name)
        elif has_whitespace(value):
            parser.error('%s can not contain whitespace' % arg_name)
        else:
            setattr(namespace, self.dest, value)


class _EnvDefault(argparse.Action):
    def __init__(self, env_var_name, required=True, default=None, **kwargs):
        env_var_value = os.environ.get(env_var_name, default)
        if env_var_value is not None:
            required = False
        super(_EnvDefault, self).__init__(default=env_var_value, required=required, **kwargs)

    def __call__(self, parser, namespace, values, option_string=None):
        setattr(namespace, self.dest, values)


def env_arg(arg_name, env_var, help='', **kwargs):
    """ Decorator that declares a command line argument that if not supplied the value
    will be taken from an environment variable

    Args:
        arg_name (str): A string containing the command line flag. e.g. --my-flag
        env_var (str): The name of the environment variable
        help (str): Help text for argument
        **kwargs: Additional arguments that will be passed to argh.arg
        
    """
    help += ' If not provided, it will be taken from the environment variable %s' % env_var
    return argh.arg(arg_name, env_var_name=env_var, action=_EnvDefault, help=help, **kwargs)


class MpfError(Exception):
    """ Base class for all MpfErrors """
    pass


def sql_connection(host, user, password):
    try:
        return pymysql.connect(host, user, password, db='mpf')
    except pymysql.err.OperationalError as err:
        if err[0] == 2003:
            raise SqlConnectionError(err)
        elif err[0] == pymysql.constants.ER.ACCESS_DENIED_ERROR:
            raise SqlLogInError(err)
        else:
            raise


class SqlConnectionError(MpfError):
    def __init__(self, original_error):
        super(SqlConnectionError, self).__init__(
            original_error[1] +
            '. (Make sure the MySQL server is running or try setting --sql-host)')


class SqlLogInError(MpfError):
    def __init__(self, original_error):
        super(SqlLogInError, self).__init__(
            original_error[1] + '. (Try setting --sql-user and/or --sql-password)')


class MsgUtil(object):
    _GREEN = '\033[0;32m'
    _YELLOW = '\033[0;33m'
    _RED = '\033[0;31m'
    _RESET = '\033[0m'

    @staticmethod
    def green(msg):
        return MsgUtil._create_colored_msg(msg, MsgUtil._GREEN)

    @staticmethod
    def yellow(msg):
        return MsgUtil._create_colored_msg(msg, MsgUtil._YELLOW)

    @staticmethod
    def red(msg):
        return MsgUtil._create_colored_msg(msg, MsgUtil._RED)

    @staticmethod
    def _create_colored_msg(msg, color):
        return color + msg + MsgUtil._RESET
