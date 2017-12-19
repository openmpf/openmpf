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


#include <iostream>
#include <functional>
#include <thread>

#include "QuitWatcher.h"

namespace MPF { namespace COMPONENT {

    QuitWatcher::QuitWatcher() {
        is_time_to_quit_ = false;
        has_error_ = false;
        std::thread watcher_thread(
                &QuitWatcher::WatchForQuit, std::ref(is_time_to_quit_), std::ref(has_error_));
        watcher_thread.detach();
    }



    bool QuitWatcher::IsTimeToQuit() {
        return is_time_to_quit_;
    }


    bool QuitWatcher::HasError() {
        return has_error_;
    }


    void QuitWatcher::WatchForQuit(std::atomic_bool &is_time_to_quit,  std::atomic_bool &has_error) {
        try {
            WatchStdIn(is_time_to_quit);
        }
        catch (const std::runtime_error &ex) {
            std::cerr << ex.what() << " Telling main thread to exit." << std::endl;
            has_error = true;
            is_time_to_quit = true;
        }
        catch (const std::exception &ex) {
            std::cerr << "An error occurred while trying to read from standard in. " << ex.what()
                      << "Telling main thread to exit. ";
            has_error = true;
            is_time_to_quit = true;
            throw;
        }
        catch (...) {
            std::cerr << "An error occurred while trying to read from standard in. Telling main thread to exit."
                      << std::endl;
            has_error = true;
            is_time_to_quit = true;
            throw;
        }
    }


    void QuitWatcher::WatchStdIn(std::atomic_bool &is_time_to_quit) {
        std::string line;
        while (std::getline(std::cin, line)) {
            std::cout << "Read line: " << line << std::endl;
            if (line == "quit") {
                is_time_to_quit = true;
                return;
            }
            else {
                std::cerr << "Ignoring unexpected input from standard in: " << line << std::endl;
            }
        }

        if (std::cin.eof()) {
            throw std::runtime_error("Standard in was closed before quit was received.");
        }
        throw std::runtime_error("An error occurred while trying to read from standard in.");
    }



}}