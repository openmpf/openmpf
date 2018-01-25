/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/


#ifndef MPF_EXECUTORERRORS_H
#define MPF_EXECUTORERRORS_H

#include <stdexcept>

namespace MPF { namespace COMPONENT {

    enum class ExitCode {
        // Standard exit codes
        SUCCESS = 0,
        UNEXPECTED_ERROR = 1,
        INVALID_COMMAND_LINE_ARGUMENTS = 2,

        // Custom exit codes
        // http://tldp.org/LDP/abs/html/exitcodes.html recommends using exit codes between range 64 - 113
        INVALID_INI_FILE = 65,
        UNABLE_TO_READ_FROM_STANDARD_IN = 66,
        MESSAGE_BROKER_ERROR = 69,
        INTERNAL_COMPONENT_ERROR = 70,
        COMPONENT_LOAD_ERROR = 71,

        UNABLE_TO_CONNECT_TO_STREAM = 75,
        STREAM_STALLED = 76,
    };



    class FatalError : public std::runtime_error {
    public:
        FatalError(ExitCode exit_code, const std::string &cause);

        ExitCode GetExitCode() const;

    private:
        const ExitCode exit_code_;
    };



    class InternalComponentError : public FatalError {
    public:
        InternalComponentError(const std::string &method_name, const std::string &cause);

        explicit InternalComponentError(const std::string &method_name);
    };
}}




#endif //MPF_EXECUTORERRORS_H
