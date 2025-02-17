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

import abc

class ILogger(abc.ABC):

    @abc.abstractmethod
    def debug(self, message: str) -> None:
        ...

    @abc.abstractmethod
    def info(self, message: str) -> None:
        ...

    @abc.abstractmethod
    def warn(self, message: str) -> None:
        ...

    @abc.abstractmethod
    def error(self, message: str) -> None:
        ...

    @abc.abstractmethod
    def fatal(self, message: str) -> None:
        ...

    @abc.abstractmethod
    def set_context_message(self, context_msg: str) -> None:
        ...
