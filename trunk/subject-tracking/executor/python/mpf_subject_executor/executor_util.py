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

import time
import typing
from typing import Optional

import proton
import proton.handlers
import proton.reactor


def create_message(body, **kwargs) -> proton.Message:
    return proton.Message(body, **kwargs, creation_time=time.time())


class ComponentLoadError(Exception):
    pass


def get_condition_description(event: proton.Event) -> Optional[str]:
    def get_description(obj):
        if cond := getattr(obj, 'condition', None):
            return cond.description
        else:
            return None

    if msg := get_description(event):
        return msg
    if msg := get_description(event.context):
        return msg
    if msg := get_description(event.link):
        return msg
    if msg := get_description(event.session):
        return msg
    if msg := get_description(event.transport):
        return msg
    if msg := get_description(event.connection):
        return msg


if typing.TYPE_CHECKING:
    # The classes below are only used for type checking, they will not be defined at runtime.
    # Many properties from proton.Event are declared as Optional because the same event class is
    # used for all events. There are many times where, based on the handler method being called, we
    # know that certain properties will be present. For example, in an `on_connection_opened` or an
    # `on_message` handler, the connection property will never be None.
    class EventWithConnection(proton.Event):
        @property
        def connection(self) -> proton.Connection:
            ...

    class OnMessageEvent(EventWithConnection):
        @property
        def delivery(self) -> proton.Delivery:
            ...

        @property
        def message(self) -> proton.Message:
            ...
