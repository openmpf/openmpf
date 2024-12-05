#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2024 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2024 The MITRE Corporation                                      #
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

from __future__ import annotations

import argparse
import json
import os
import os.path
import sys
from pathlib import Path
from typing import NamedTuple, Tuple


def get_config() -> ExecutorConfig:
    cli_args = parse_cli_args()
    if not cli_args.amqp_uri:
        cli_args.amqp_uri = get_broker_uri_from_env()
    if not cli_args.language:
        cli_args.language = get_language_from_env()
    mpf_home = Path(os.getenv('MPF_HOME') or '/opt/mpf')
    descriptor_string = read_descriptor_string(cli_args, mpf_home)
    name, lib = get_name_and_lib(descriptor_string)
    queue_name = get_queue_name(name)
    is_python = cli_args.language == 'python'
    if not is_python:
        raise InvalidConfigurationError('Only Python components are currently supported.')

    return ExecutorConfig(
        cli_args.amqp_uri,
        int(os.getenv('AMQP_CONNECT_ATTEMPTS', '20')),
        queue_name,
        descriptor_string,
        name,
        is_python,
        lib,
        get_log_level_and_set_env())


class ExecutorConfig(NamedTuple):
    amqp_uri: str
    num_connection_attempts: int
    job_queue: str
    descriptor_string: str
    component_name: str
    is_python: bool
    lib: str
    log_level: str


def get_broker_uri_from_env() -> str:
    if env_broker_uri := os.getenv('AMQP_BROKER_URI'):
        return env_broker_uri
    if host := os.getenv('ACTIVE_MQ_HOST'):
        return f'amqp://{host}:5672'
    raise InvalidConfigurationError('AMQP_BROKER_URI, ACTIVE_MQ_HOST, and -a where not set.')


def get_language_from_env() -> str:
    if env := os.getenv('COMPONENT_LANGUAGE'):
        return env
    raise InvalidConfigurationError('COMPONENT_LANGUAGE environment variable and -l was not set.')


def read_descriptor_string(cli_args, mpf_home: Path):
    if cli_args.descriptor_path:
        descriptor_path = expand_path_if_needed(cli_args.descriptor_path)
    else:
        descriptor_path = find_descriptor(mpf_home)
    try:
        return descriptor_path.read_text()
    except OSError as e:
        raise InvalidConfigurationError(
            f'Failed to read descriptor from {descriptor_path} due to: {e}') from e


def find_descriptor(mpf_home: Path) -> Path:
    descriptors = list((mpf_home / 'plugins').glob('*/descriptor/descriptor.json'))
    if len(descriptors) == 1:
        return descriptors[0]
    if not descriptors:
        raise InvalidConfigurationError('Unable to find descriptor')

    descriptors_iter = iter(descriptors)
    first = next(descriptors_iter)
    all_same = all(first.samefile(d) for d in descriptors_iter)
    if all_same:
        return first
    raise InvalidConfigurationError('Found multiple descriptors')


def expand_path_if_needed(descriptor_path: str) -> Path:
    raw_path = Path(descriptor_path)
    if raw_path.exists():
        return raw_path
    expanded_path = Path(os.path.expandvars(descriptor_path)).expanduser()
    if expanded_path.exists():
        return expanded_path
    raise InvalidConfigurationError(f'Descriptor did not exist at: {descriptor_path}')


def get_name_and_lib(descriptor_string: str) -> Tuple[str, str]:
    descriptor = json.loads(descriptor_string)
    try:
        return descriptor['componentName'], descriptor['componentLibrary']
    except KeyError as e:
        raise InvalidConfigurationError(f'The descriptor did not contain: {e.args[0]}') from e


def get_queue_name(component_name: str) -> str:
    return f'MPF.SUBJECT_{component_name.upper()}_REQUEST'


def get_log_level_and_set_env() -> str:
    env_level = os.getenv('LOG_LEVEL')
    if not env_level:
        os.environ['LOG_LEVEL'] = 'INFO'
        return 'INFO'

    level_name = env_level.upper()
    if level_name == 'WARNING':
        # Python logging accepts either WARNING or WARN, but Log4CXX requires it be WARN.
        os.environ['LOG_LEVEL'] = 'WARN'
        return 'WARN'
    elif level_name == 'CRITICAL':
        # Python logging accepts either CRITICAL or FATAL, but Log4CXX requires it be FATAL.
        os.environ['LOG_LEVEL'] = 'FATAL'
        return 'FATAL'
    elif level_name in ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'):
        return level_name
    else:
        level_name = 'DEBUG'
        print(
            f'The LOG_LEVEL environment variable is set to {env_level}, but that is not a valid'
            f'log level. Using {level_name} instead.', file=sys.stderr)
        os.environ['LOG_LEVEL'] = level_name
        return level_name


def parse_cli_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('-a', '--amqp-uri')
    parser.add_argument('-d', '--descriptor-path')
    parser.add_argument('-l', '--language')
    return parser.parse_args()


class InvalidConfigurationError(Exception):
    pass
