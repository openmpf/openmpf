/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

#include <atomic>
#include <csignal>
#include <mutex>
#include <thread>
#include <utility>
#include <vector>

#include "ExitSignalBlocker.h"
#include "QuitReceived.h"

#include "Shutdown.h"

namespace MPF::Shutdown {

namespace {

    class ShutdownHandler {
    public:
        static void AddAction(std::function<void()> action) {
            std::unique_lock lock{mutex_};
            if (quit_received_) {
                action();
            }
            else {
                actions_.push_back(std::move(action));
            }
        }

        static bool QuitReceived() {
            return quit_received_;
        }

    private:
        struct Init {
            Init() { std::thread{WatchForSignal}.detach(); }
        };

        static std::mutex mutex_;
        static std::atomic_bool quit_received_;
        static std::vector<std::function<void()>> actions_;
        static Init init_helper_;

        static void WatchForSignal() {
            // If we don't block signals, sigwait will never actually return because the default
            // signal handler will terminate the program before sigwait returns.
            ExitSignalBlocker blocker;
            auto signal_set = ExitSignalBlocker::GetFilledSignalSet();
            int signal_received = -1;
            // Use a thread and sigwait instead of a signal handler because there are very few
            // actions that are safe to do in a signal handler. sigwait allows us to do whatever
            // we want in response to the signal.
            sigwait(&signal_set, &signal_received);
            quit_received_ = true;
            std::unique_lock lock{mutex_};
            for (const auto& action : actions_) {
                action();
            }
            actions_.clear();
        }
    };


    // Initialize static member variables here because static member variables can not be
    // initialized within the class body.
    std::mutex ShutdownHandler::mutex_{};
    std::atomic_bool ShutdownHandler::quit_received_ = false;
    std::vector<std::function<void()>> ShutdownHandler::actions_{};
    ShutdownHandler::Init ShutdownHandler::init_helper_{};
} // namespace


void AddShutdownAction(std::function<void()> action) {
    ShutdownHandler::AddAction(std::move(action));
}

void CheckQuit() {
    if (ShutdownHandler::QuitReceived()) {
        throw QuitReceived{""};
    }
}

} // namespace MPF::Shutdown
