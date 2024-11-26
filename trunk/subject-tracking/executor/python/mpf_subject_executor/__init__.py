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

import contextlib
import socket
import sys
import time
import traceback
from typing import NoReturn

import proton
import proton.handlers
import proton.reactor

from . import executor_config
from . import executor_util as util
from . import python_logger
from .component_registration import ComponentRegistrationError, RegistrationHandler
from .executor_config import ExecutorConfig
from .job_handler import ComponentErrorEvent, JobHandler
from .logger_wrapper import LoggerWrapper


def main() -> int:
    try:
        config = executor_config.get_config()
        logger = get_logger(config)
    except Exception as e:
        traceback.print_exc(file=sys.stderr)
        print(f'An exception occurred before logging could be configured: {e}', file=sys.stderr)
        return 1

    try:
        run_container(logger, config)
        return 0
    except ComponentRegistrationError:
        log_fatal_exception('An error occurred while trying to register component: ', logger)
        return 38
    except util.ComponentLoadError:
        log_fatal_exception('An error occurred while trying to load component: ', logger)
        return 39
    except Exception:
        log_fatal_exception('A fatal error occurred: ', logger)
        return 1



def run_container(logger: LoggerWrapper, config: ExecutorConfig):
    backoff_iter = iter(proton.reactor.Backoff(max_tries=config.num_connection_attempts))
    while True:
        try:
            with StartupHandler(logger, config) as handler:
                proton.reactor.Container(handler).run()
            return
        except socket.gaierror as e:
            next_delay = next(backoff_iter, None)
            if next_delay is None:
                raise
            logger.warn(
                f'Will attempt to re-connect in {next_delay} seconds because a socket error '
                f'occurred: {e.strerror}')
            time.sleep(next_delay)


class StartupHandler(proton.handlers.MessagingHandler):
    def __init__(self, logger: LoggerWrapper, config: ExecutorConfig):
        super().__init__(prefetch=0, auto_accept=False, peer_close_is_error=True)
        self._logger = logger
        self._config = config
        self._exit_stack = contextlib.ExitStack()
        self.handlers.append(ReconnectHandler(logger, config))


    def on_start(self, event: proton.Event) -> None:
        connection = event.container.connect(self._config.amq_uri)
        RegistrationHandler(
                self._logger,
                self._config.descriptor_string,
                event.container,
                connection,
                on_complete=self._component_registered)


    def _component_registered(self, event: proton.Event) -> None:
        assert event.connection
        self._exit_stack.enter_context(
                JobHandler(self._logger, self._config, event.container, event.connection))


    def on_component_error(self, event: ComponentErrorEvent) -> NoReturn:
        event.raise_exception()

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self._exit_stack.close()



class ReconnectHandler(proton.Handler):
    def __init__(self, logger: LoggerWrapper, config: ExecutorConfig):
        self._logger = logger
        self._max_connect_attempts = config.num_connection_attempts
        self._num_disconnects = 0


    def on_transport_closed(self, event: proton.Event):
        self._num_disconnects += 1
        error_description = util.get_condition_description(event)
        message = (f'Connection failed due to: "{error_description}". '
                   f'Connection has failed {self._num_disconnects} times in a row.')
        if self._num_disconnects >= self._max_connect_attempts:
            raise NoMoreRetriesException(message)
        else:
            self._logger.warn(message)


    def on_connection_remote_open(self, _: proton.Event):
        if self._num_disconnects > 0:
            self._logger.info('Connection was successful. Resetting disconnect count.')
            self._num_disconnects = 0


class NoMoreRetriesException(Exception):
    pass


def log_fatal_exception(msg_prefix: str, logger: LoggerWrapper):
    logger.fatal(traceback.format_exc())
    logger.fatal(msg_prefix, sys.exc_info()[1])


def get_logger(config: ExecutorConfig) -> LoggerWrapper:
    if config.is_python:
        base_logger = python_logger.PythonLogger(config.log_level, 'org.mitre.mpf.subject')
        return LoggerWrapper(config.log_level, base_logger)
    else:
        raise NotImplementedError('Only Python components are currently supported.')


if __name__ == '__main__':
    sys.exit(main())
