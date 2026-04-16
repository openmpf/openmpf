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

#include <future>

#include "Messenger.h"

namespace MPF::COMPONENT {

class SignalWatcher {
public:
    /**
     * Blocks SIGINT and SIGTERM, then starts a thread that will synchronously receive those
     * signals with sigwait. Must be called before any other threads are started because it sets
     * the signal mask and new threads inherit the signal mask. The mask must be set because a
     * signal must be blocked in all threads in order to receive it with sigwait.
     */
    explicit SignalWatcher(bool is_python_component);

    void SetInterrupter(MessengerInterrupter interrupter);

private:
    std::promise<MessengerInterrupter> interrupter_promise_;
};

} // namespace MPF::COMPONENT
