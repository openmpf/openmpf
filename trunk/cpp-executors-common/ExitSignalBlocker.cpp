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

#include <csignal>

#include "ExitSignalBlocker.h"

namespace MPF {


ExitSignalBlocker::ExitSignalBlocker() {
    auto signal_set = GetFilledSignalSet();
    sigset_t old_signal_set;
    sigemptyset(&old_signal_set);

    pthread_sigmask(SIG_BLOCK, &signal_set, &old_signal_set);
    sigterm_was_blocked_ = sigismember(&old_signal_set, SIGTERM) == 1;
    sigint_was_blocked_ = sigismember(&old_signal_set, SIGINT) == 1;
}

ExitSignalBlocker::~ExitSignalBlocker() {
    if (sigterm_was_blocked_ && sigint_was_blocked_) {
        return;
    }
    sigset_t signal_set;
    sigemptyset(&signal_set);
    if (!sigterm_was_blocked_) {
        sigaddset(&signal_set, SIGTERM);
    }
    if (!sigint_was_blocked_) {
        sigaddset(&signal_set, SIGINT);
    }
    pthread_sigmask(SIG_UNBLOCK, &signal_set, nullptr);
}

sigset_t ExitSignalBlocker::GetFilledSignalSet() {
    sigset_t signal_set;
    sigemptyset(&signal_set);
    sigaddset(&signal_set, SIGINT);
    sigaddset(&signal_set, SIGTERM);
    return signal_set;
}

} // namespace MPF
