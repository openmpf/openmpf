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


#ifndef MPF_QUITWATCHER_H
#define MPF_QUITWATCHER_H

#include <atomic>


namespace MPF { namespace COMPONENT {

    class QuitWatcher {
    public:
        QuitWatcher();

        bool IsTimeToQuit();

        bool HasError();

    private:
        std::atomic_bool is_time_to_quit_;
        std::atomic_bool has_error_;

        static void WatchForQuit(std::atomic_bool &is_time_to_quit,  std::atomic_bool &has_error);

        static void WatchStdIn(std::atomic_bool &is_time_to_quit);
    };

}}



#endif //MPF_QUITWATCHER_H
