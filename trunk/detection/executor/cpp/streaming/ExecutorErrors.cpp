/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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


#include "ExecutorErrors.h"


namespace MPF { namespace COMPONENT {

    FatalError::FatalError(ExitCode exit_code, const std::string &cause)
            : std::runtime_error(cause)
            , exit_code_(exit_code) {

    }

    ExitCode FatalError::GetExitCode() const {
        return exit_code_;
    }




    InternalComponentError::InternalComponentError(const std::string &method_name, const std::string &cause)
        : FatalError(
            ExitCode::INTERNAL_COMPONENT_ERROR,
            "The loaded component threw an exception while executing its \"" + method_name +"\" method: " + cause) {
    }

    InternalComponentError::InternalComponentError(const std::string &method_name)
        : FatalError(
            ExitCode::INTERNAL_COMPONENT_ERROR,
            "The loaded component threw an object that does not derive from std::exception while executing its \""
                + method_name + " method.") {
    }
}}