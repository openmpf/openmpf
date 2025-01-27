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

import contextlib
import contextvars
import traceback
from typing import Callable

from .logger_interface import ILogger


# Use ContextVar so each thread can have its own context message.
log_ctx_msg = contextvars.ContextVar('log_ctx_msg', default='')


class LoggerWrapper:
    def __init__(self, log_level: str, base_logger: ILogger) -> None:
        self._base_logger = base_logger
        debug_enabled = log_level in ('DEBUG', 'TRACE')
        info_enabled = debug_enabled or log_level == 'INFO'
        warn_enabled = info_enabled or log_level == 'WARN'
        error_enabled = warn_enabled or log_level == 'ERROR'
        fatal_enabled = error_enabled or log_level == 'FATAL'

        def enable(log_method: Callable[[str], None]):
            return lambda *args: log_method(self._to_string(*args))

        def disable(*_):
            # No-op for disabled log levels
            pass

        self.debug = enable(base_logger.debug) if debug_enabled else disable
        self.info = enable(base_logger.info) if info_enabled else disable
        self.warn = enable(base_logger.warn) if warn_enabled else disable
        self.error = enable(base_logger.error) if error_enabled else disable
        self.fatal = enable(base_logger.fatal) if fatal_enabled else disable

    def exception(self, *args):
        self.error(*args, '\n', traceback.format_exc())

    @contextlib.contextmanager
    def get_logger_context(self, context_message: str):
        formatted_message = f'[{context_message}] '
        reset_token = log_ctx_msg.set(formatted_message)
        try:
            self._base_logger.set_context_message(formatted_message)
            yield self
        finally:
            log_ctx_msg.reset(reset_token)
            self._base_logger.set_context_message(log_ctx_msg.get())

    @staticmethod
    def _to_string(*args):
        return ''.join(str(a) for a in args)
