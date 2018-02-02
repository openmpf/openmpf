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

#include <vector>

#include <gtest/gtest.h>
#include <log4cxx/logger.h>

#include <MPFDetectionComponent.h>
#include <MPFStreamingDetectionComponent.h>
#include <log4cxx/basicconfigurator.h>

#include "../ExecutorUtils.h"


using namespace MPF::COMPONENT;


log4cxx::LoggerPtr getLogger() {
    log4cxx::LoggerPtr logger = log4cxx::Logger::getRootLogger();
    logger->setLevel(log4cxx::Level::getOff());
    return logger;
}

log4cxx::LoggerPtr logger = getLogger();



MPFVideoTrack createTrack(const std::vector<int> &test_keys) {
    MPFVideoTrack track;
    for (int key : test_keys) {
        track.frame_locations.emplace(key, MPFImageLocation());
    }
    return track;
}


void assertDetectionsDropped(const std::vector<int> &test_keys, const std::vector<int> &expected_remaining_keys) {
    std::vector<MPFVideoTrack> tracks { createTrack(test_keys) };

    ExecutorUtils::FixTracks(logger, {1, 10, 19, 0, 0}, tracks);
    if (expected_remaining_keys.empty()) {
        ASSERT_TRUE(tracks.empty());
        return;
    }

    const auto &track = tracks.front();
    const auto &detections = track.frame_locations;
    ASSERT_EQ(track.start_frame, detections.begin()->first);
    ASSERT_EQ(track.stop_frame, detections.rbegin()->first);

    ASSERT_EQ(detections.size(), expected_remaining_keys.size());
    for (int expected_key : expected_remaining_keys) {
        ASSERT_EQ(1, detections.count(expected_key));
    }
}


TEST(StreamingExecutorUtilsTest, FixTracksDropsOutOfRangeDetections) {
    // All before segment
    assertDetectionsDropped({}, {});
    assertDetectionsDropped({3}, {});
    assertDetectionsDropped({9}, {});
    assertDetectionsDropped({3, 4, 5}, {});
    assertDetectionsDropped({4, 5, 9}, {});

    // All after segment
    assertDetectionsDropped({20}, {});
    assertDetectionsDropped({25}, {});
    assertDetectionsDropped({25, 30}, {});
    assertDetectionsDropped({20, 25, 30}, {});

    // Before and after segment
    assertDetectionsDropped({3, 4, 5, 9, 20, 25, 30}, {});
    assertDetectionsDropped({3, 4, 5, 25, 30}, {});


    // All in segment
    assertDetectionsDropped({10}, {10});
    assertDetectionsDropped({15}, {15});
    assertDetectionsDropped({10, 15}, {10, 15});
    assertDetectionsDropped({10, 19}, {10, 19});
    assertDetectionsDropped({10, 15, 19}, {10, 15, 19});
    assertDetectionsDropped({19}, {19});
    assertDetectionsDropped({18, 19}, {18, 19});

    // In segment and before segment
    assertDetectionsDropped({1, 3, 10}, {10});
    assertDetectionsDropped({1, 3, 9, 10}, {10});
    assertDetectionsDropped({1, 3, 14}, {14});
    assertDetectionsDropped({1, 3, 9, 14}, {14});
    assertDetectionsDropped({1, 3, 10, 12}, {10, 12});
    assertDetectionsDropped({1, 3, 14, 19}, {14, 19});


    // In segment and after segment
    assertDetectionsDropped({19, 23, 25}, {19});
    assertDetectionsDropped({19, 20, 23, 25}, {19});
    assertDetectionsDropped({12, 23, 25}, {12});
    assertDetectionsDropped({12, 20, 23, 25}, {12});
    assertDetectionsDropped({14, 19, 23, 25}, {14, 19});
    assertDetectionsDropped({10, 12, 23, 25}, {10, 12});

    // In segment, before segment, and after segment
    assertDetectionsDropped({1, 15, 20}, {15});
    assertDetectionsDropped({3, 4, 8, 14, 18, 28}, {14, 18});
    assertDetectionsDropped({3, 4, 9, 18, 28}, {18});
    assertDetectionsDropped({3, 4, 10, 18, 28}, {10, 18});
    assertDetectionsDropped({3, 4, 8, 19, 28}, {19});
    assertDetectionsDropped({3, 4, 9, 11, 19, 20}, {11, 19});
    assertDetectionsDropped({3, 4, 10, 19, 20, 28}, {10, 19});
}



void assertEmptyTracksDropped(int expected_remaining_track_count, std::vector<MPFVideoTrack> &&tracks) {
    ExecutorUtils::FixTracks(logger, {1, 10, 19, 0, 0}, tracks);

    ASSERT_EQ(tracks.size(), expected_remaining_track_count);
    for (const auto &track : tracks) {
        ASSERT_FALSE(track.frame_locations.empty());
        ASSERT_EQ(track.start_frame, track.frame_locations.begin()->first);
        ASSERT_EQ(track.stop_frame, track.frame_locations.rbegin()->first);
    }
}


TEST(StreamingExecutorUtilsTest, FixTracksDropsEmptyTracks) {
    assertEmptyTracksDropped(0, {});

    // All empty
    assertEmptyTracksDropped(0, { createTrack({}) });
    assertEmptyTracksDropped(0, { createTrack({}), createTrack({}) });

    // Empty or out of range
    assertEmptyTracksDropped(0, { createTrack({5}) });
    assertEmptyTracksDropped(0, { createTrack({}), createTrack({6}) });


    // All valid
    assertEmptyTracksDropped(1, { createTrack({10}) });
    assertEmptyTracksDropped(2, { createTrack({15}), createTrack({12, 14}) });


    // Mixed
    assertEmptyTracksDropped(3, {
            createTrack({}),
            createTrack({15}),
            createTrack({12, 14}),
            createTrack({5, 30}),
            createTrack({5, 10, 15, 23}) });

}



TEST(StreamingExecutorUtilsTest, RetryRetriesUntilTimeout) {
    using namespace std::chrono;

    int count = 0;
    auto test_func = [&] {
        count++;
        return false;
    };

    auto start_time = steady_clock::now();
    bool retry_result = ExecutorUtils::RetryWithBackOff(std::chrono::milliseconds(500), test_func);
    nanoseconds runtime = steady_clock::now() - start_time;
    ASSERT_FALSE(retry_result);

    ASSERT_EQ(count, 9);
    ASSERT_TRUE(runtime >= milliseconds(495));
    ASSERT_TRUE(runtime <= milliseconds(510));
}




TEST(StreamingExecutorUtilsTest, RetryStopsWhenFuncReturnsTrue) {
    using namespace std::chrono;

    int count = 0;
    auto test_func = [&] {
        count++;
        return count >= 2;
    };

    bool retry_result = ExecutorUtils::RetryWithBackOff(std::chrono::milliseconds(100), test_func);
    ASSERT_TRUE(retry_result);

    ASSERT_EQ(count, 2);
}




TEST(StreamingExecutorUtilsTest, RetryWorksWhenFuncTakesTime) {
    using namespace std::chrono;

    int count = 0;
    auto test_func = [&] {
        count++;
        std::this_thread::sleep_for(milliseconds(155));
        return false;
    };

    bool retry_result = ExecutorUtils::RetryWithBackOff(std::chrono::milliseconds(300), test_func);
    ASSERT_FALSE(retry_result);
    ASSERT_EQ(count, 2);
}

