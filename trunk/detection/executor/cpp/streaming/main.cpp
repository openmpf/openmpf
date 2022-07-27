/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

#include <iostream>

#include "ExecutorErrors.h"
#include "StreamingComponentExecutor.h"


int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::cerr << "Usage: " << argv[0] << " <ini_path>" << std::endl;
        return static_cast<int>(MPF::COMPONENT::ExitCode::INVALID_COMMAND_LINE_ARGUMENTS);
    }
    try {
        return static_cast<int>(
                MPF::COMPONENT::StreamingComponentExecutor::RunJob(argv[1]));
    }
    // There is a similar catch clause in RunJob, but that outputs the error using LOG4CXX so it
    // can't log errors that occur prior to or during the configuration of LOG4CXX.
    catch (const MPF::COMPONENT::FatalError &ex) {
        std::cerr << "Exiting due to error: " << ex.what() << std::endl;
        return static_cast<int>(ex.GetExitCode());
    }
    catch (const std::exception &ex) {
        std::cerr << "Exiting due to error: " << ex.what() << std::endl;
        return static_cast<int>(MPF::COMPONENT::ExitCode::UNEXPECTED_ERROR);
    }
    catch (...) {
        std::cerr << "Exiting due to error." << std::endl;
        return static_cast<int>(MPF::COMPONENT::ExitCode::UNEXPECTED_ERROR);
    }
}


