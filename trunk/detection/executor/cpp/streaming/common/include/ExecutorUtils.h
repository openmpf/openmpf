/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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


#ifndef MPF_EXECUTORUTILS_H
#define MPF_EXECUTORUTILS_H

#include <algorithm>
#include <chrono>
#include <limits>
#include <string>
#include <thread>
#include <libgen.h>

#include <MPFDetectionComponent.h>
#include <MPFStreamingDetectionComponent.h>
#include <log4cxx/logger.h>


namespace MPF { namespace COMPONENT { namespace ExecutorUtils {

    constexpr double LOWEST_CONFIDENCE_THRESHOLD = std::numeric_limits<double>::lowest();

    void DropLowConfidenceDetections(double confidence_threshold, std::vector<MPFVideoTrack> &tracks);


    void DropOutOfSegmentDetections(log4cxx::LoggerPtr &logger, const VideoSegmentInfo &segment,
                                    std::vector<MPFVideoTrack> &tracks);

    // std::chrono::steady_clock::duration is implementation defined.
    // (In libstdc++.so.6.0.19 it is std::chrono::nanoseconds.)
    // We use std::chrono::steady_clock::duration for the sleep times (since that is the most precise time unit)
    // to avoid oversleeping.
    typedef std::chrono::steady_clock::duration sleep_duration_t;

    constexpr sleep_duration_t MAX_BACK_OFF = std::chrono::duration_cast<sleep_duration_t>(std::chrono::seconds(30));


    template <typename TDurRep, typename TDurPeriod, typename TRetryFunc, typename TSleeper>
    bool RetryWithBackOff(const std::chrono::duration<TDurRep, TDurPeriod> &max_duration, TRetryFunc retry_func,
                          TSleeper sleep_func) {
        using namespace std::chrono;
        auto start_time = steady_clock::now();
        auto end_time = start_time + max_duration;

        sleep_duration_t back_off_duration = duration_cast<sleep_duration_t>(milliseconds(1));

        while (true) {
            sleep_duration_t time_left = end_time - steady_clock::now();
            if (time_left <= sleep_duration_t::zero()) {
                return false;
            }

            auto sleep_time = std::min(time_left, back_off_duration);
            sleep_func(sleep_time);

            if (retry_func()) {
                return true;
            }
            back_off_duration = std::min(back_off_duration * 2, MAX_BACK_OFF);
        }
    }


    template <typename TDurRep, typename TDurPeriod, typename TFunc>
    bool RetryWithBackOff(const std::chrono::duration<TDurRep, TDurPeriod> &max_duration, TFunc func) {
        return RetryWithBackOff(max_duration, func,
                                std::this_thread::sleep_for<sleep_duration_t::rep, sleep_duration_t::period>);
    }


    template <typename TRetryFunc, typename TSleeper>
    void RetryWithBackOff(TRetryFunc retry_func, TSleeper sleep_func) {
        using namespace std::chrono;

        sleep_duration_t back_off_duration = duration_cast<sleep_duration_t>(milliseconds(1));
        while (true) {
            sleep_func(back_off_duration);
            if (retry_func()) {
                return;
            }
            back_off_duration = std::min(back_off_duration * 2, MAX_BACK_OFF);
        }
    }


    template <typename TRetryFunc>
    void RetryWithBackOff(TRetryFunc retry_func) {
        RetryWithBackOff(retry_func, std::this_thread::sleep_for<sleep_duration_t::rep, sleep_duration_t::period>);
    };

    std::string GetAppDir();

}}}


#endif //MPF_EXECUTORUTILS_H
