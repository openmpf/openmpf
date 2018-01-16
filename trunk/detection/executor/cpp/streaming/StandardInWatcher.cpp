/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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


#include <functional>
#include <iostream>
#include <thread>

#include "StandardInWatcher.h"
#include "ExecutorErrors.h"

namespace MPF { namespace COMPONENT {
    std::atomic_bool StandardInWatcher::quit_received_(false);
    std::string StandardInWatcher::error_message_;

    StandardInWatcher* StandardInWatcher::instance_(nullptr);


    StandardInWatcher::StandardInWatcher() {
        std::thread watcher_thread(&Watch);
        // detach so that when there is an error elsewhere, the process can exit without waiting for the quit command.
        watcher_thread.detach();
    }


    bool StandardInWatcher::QuitReceived() const {
        if (quit_received_ && !error_message_.empty()) {
            throw FatalError(ExitCode::UNABLE_TO_READ_FROM_STANDARD_IN, error_message_);
        }
        return quit_received_;
    }


    void StandardInWatcher::Watch() {
        try {
            std::string line;
            while (std::getline(std::cin, line)) {
                if (line == "quit") {
                    quit_received_ = true;
                    return;
                }
                // TODO: Check for "pause" and "resume" when adding support for multistage pipelines.
                std::cerr << "Ignoring unexpected input from standard in: " << line << std::endl;
            }

            if (std::cin.eof()) {
                SetError("Standard in was closed before quit was received.");
            }
            else {
                SetError("An error occurred while trying to read from standard in.");
            }
        }
        catch (const std::exception &ex) {
            SetError(std::string("An error occurred while trying to read from standard in: ") + ex.what());
        }
        catch (...) {
            SetError("An error occurred while trying to read from standard in.");
        }
    }


    StandardInWatcher* StandardInWatcher::GetInstance() {
        if (instance_ == nullptr) {
            instance_ = new StandardInWatcher;
        }
        return instance_;
    }

    void StandardInWatcher::SetError(std::string &&error_message) {
        // error_message_ must be set before is_time_to_quit_ to ensure proper synchronization.
        // error_message_ is not atomic because there is no atomic string.
        // Since is_time_to_quit_ is atomic, reading or writing to is_time_to_quit_ will cause a synchronization event.
        error_message_ = std::move(error_message);
        quit_received_ = true;
    }
}}