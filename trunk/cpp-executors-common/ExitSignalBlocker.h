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

#pragma once

#include <csignal>

namespace MPF {

/**
 * Blocks SIGTERM and SIGINT from the time the object is constructed until it is destructed. If
 * either signal was unblocked prior to construction, the destructor will unblock the signal. If
 * either signal was already blocked when the object was constructed, the destructor will leave the
 * signal blocked.
 */
class ExitSignalBlocker {
public:
    ExitSignalBlocker();

    ExitSignalBlocker(const ExitSignalBlocker&) = delete;
    ExitSignalBlocker& operator=(const ExitSignalBlocker&) = delete;

    ~ExitSignalBlocker();

    static sigset_t GetFilledSignalSet();

private:
    bool sigterm_was_blocked_{false};
    bool sigint_was_blocked_{false};
};
} // namespace MPF
