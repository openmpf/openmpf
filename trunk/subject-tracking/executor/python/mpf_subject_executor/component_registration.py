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

import uuid
from typing import Callable

import proton
import proton.handlers
import proton.reactor

from . import executor_util as util
from .logger_wrapper import LoggerWrapper


class RegistrationHandler(proton.handlers.MessagingHandler):
    def __init__(
            self,
            logger: LoggerWrapper,
            descriptor_string: str,
            container: proton.reactor.Container,
            connection: proton.Connection,
            on_complete: Callable[[util.EventWithConnection], None]) -> None:
        super().__init__(prefetch=0, auto_accept=False, peer_close_is_error=True)
        self._logger = logger
        self._descriptor_string = descriptor_string
        self._on_complete = on_complete

        self._sender = container.create_sender(connection, 'MPF.SUBJECT_COMPONENT_REGISTRATION')
        self._receiver = container.create_receiver(connection, dynamic=True, handler=self)
        # Prefetch is disabled, so we need to manually tell sender we can accept 1 message.
        self._receiver.flow(1)


    def on_link_opened(self, _) -> None:
        # The temporary queue name is not available when initially creating the receiver. It only
        # becomes available after the link_opened event.
        reply_to = self._receiver.remote_source.address
        message = util.create_message(
            self._descriptor_string,
            reply_to=reply_to,
            correlation_id=uuid.uuid4())
        self._logger.info('Sending component registration request.')
        self._sender.send(message)


    def on_message(self, event: util.OnMessageEvent) -> None:
        if event.message.properties is None:
            self.reject(event.delivery)
            raise ComponentRegistrationError(
                    'The registration response was missing required properties.')

        self.accept(event.delivery)
        details = event.message.properties.get('detail')
        if event.message.properties.get('success'):
            self._logger.info(f'Successfully registered component. Response from server: {details}')
            self._receiver.close()
            self._sender.close()
            self._on_complete(event)
        else:
            raise ComponentRegistrationError(f'Registration failed with response: {details}')


class ComponentRegistrationError(Exception):
    pass
