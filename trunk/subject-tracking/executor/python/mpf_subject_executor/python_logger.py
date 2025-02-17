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

import contextvars
import logging
from typing import Literal

from . import logger_interface

# Use ContextVar so each thread can have its own context message.
log_filter_ctx_msg = contextvars.ContextVar('log_filter_ctx_msg', default='')

class PythonLogger(logger_interface.ILogger):
    def __init__(self, log_level: str, logger_name: str):
        self._configure_logging(log_level)
        self._logger = logging.getLogger(logger_name)

    def debug(self, message: str) -> None:
        self._logger.debug(message)

    def info(self, message: str) -> None:
        self._logger.info(message)

    def warn(self, message: str) -> None:
        self._logger.warning(message)

    def error(self, message: str) -> None:
        self._logger.error(message)

    def fatal(self, message: str) -> None:
        self._logger.fatal(message)

    def set_context_message(self, context_msg: str) -> None:
        log_filter_ctx_msg.set(context_msg)

    _LOGGING_INITIALIZED = False

    @classmethod
    def _configure_logging(cls, log_level: str) -> None:
        if cls._LOGGING_INITIALIZED:
            return
        # Change default level names to match what WFM expects
        # Change default level name for logger.warn and logger.warning from 'WARNING' to 'WARN'
        logging.addLevelName(logging.WARN, 'WARN')
        # Change default level name for logger.fatal and logger.critical from 'CRITICAL' to 'FATAL'
        logging.addLevelName(logging.FATAL, 'FATAL')

        # Create handler to output logs to stderr.
        stream_handler = logging.StreamHandler()
        # Add log_ctx_filter to the handler so it inherited by all loggers.
        stream_handler.addFilter(AddLogContextFilter())
        if log_level == 'TRACE':
            # Python doesn't use TRACE so we just log everything when TRACE is provided
            log_level = 'NOTSET'
        logging.basicConfig(
            format='%(asctime)s %(levelname)-5s [%(name)s] - %(ctx)s%(message)s',
            level=log_level,
            handlers=(stream_handler,))
        cls._LOGGING_INITIALIZED = True


class AddLogContextFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> Literal[True]:
        record.ctx = log_filter_ctx_msg.get()
        return True
