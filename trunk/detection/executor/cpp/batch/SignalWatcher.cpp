/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2026 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2026 The MITRE Corporation                                       *
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

#include "SignalWatcher.h"

#include <chrono>
#include <csignal>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>
#include <utility>

#include <MPFBreaker.h>
#include "PythonComponentHandle.h"

namespace MPF::COMPONENT {

namespace {
    void blockSignals() {
        sigset_t signals;
        sigemptyset(&signals);
        sigaddset(&signals, SIGINT);
        sigaddset(&signals, SIGTERM);
        pthread_sigmask(SIG_BLOCK, &signals, nullptr);
    }

    int waitForSignal() {
        sigset_t signals;
        sigemptyset(&signals);
        sigaddset(&signals, SIGINT);
        sigaddset(&signals, SIGTERM);

        int signal = 0;
        sigwait(&signals, &signal);
        return signal;
    }

    std::string formatSignal(int signal) {
        if (const auto* sig_name = strsignal(signal)) {
            return "Signal " + std::to_string(signal) + " (" + sig_name + ')';
        }
        else {
            return "Signal " + std::to_string(signal);
        }
    }

    [[noreturn]]
    void signalWatcherThread(
            bool is_python_component, std::future<MessengerInterrupter> interrupter_future) {
        int received_signal = waitForSignal();
        std::cerr << "Signal Watcher received: " << formatSignal(received_signal)
                << ". Attempting interrupt..." << '\n';
        MPFBreaker::requestStop();
        if (is_python_component) {
            PythonComponentHandle::Interrupt();
        }

        auto future_status = interrupter_future.wait_for(std::chrono::seconds::zero());
        if (future_status == std::future_status::ready) {
            std::cerr << "Signal Watcher is requesting orderly shutdown of messenger...\n";
            interrupter_future.get().Interrupt();
            // When the program exits normally, this thread will be blocked in the call to
            // waitForSignal below. If it unblocks, then a second signal was received. We assume we
            // received the second signal because the program was taking too long to exit and we
            // attempt to forcibly exit.
            received_signal = waitForSignal();
            std::cerr << "Signal Watcher is calling exit now because "
                << formatSignal(received_signal)
                << " was received while waiting for orderly shutdown.\n";
        }
        else {
            std::cerr <<
                "Signal Watcher is calling exit now because messenger was never initialized.\n";
        }
        std::exit(128 + received_signal);
    }
} // namespace


SignalWatcher::SignalWatcher(bool is_python_component) {
    static bool has_started = false;
    if (has_started) {
        throw std::runtime_error{"Only one instance of SignalWatcher can be created."};
    }
    has_started = true;
    blockSignals();
    std::thread{
            signalWatcherThread, is_python_component, interrupter_promise_.get_future()}.detach();
}

void SignalWatcher::SetInterrupter(MessengerInterrupter interrupter) {
    interrupter_promise_.set_value(std::move(interrupter));
}

} // namespace MPF::COMPONENT
