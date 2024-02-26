/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

#include <chrono>
#include <cstdlib>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include <log4cxx/logger.h>
#include <gtest/gtest.h>
#include <MPFDetectionObjects.h>
#include <MPFDetectionComponent.h>
#include <MPFDetectionException.h>

#include "../PythonComponentHandle.h"
#include "../Messenger.h"
#include "../BatchExecutorUtil.h"
#include "../LoggerWrapper.h"
#include "../HealthCheck.h"

using namespace MPF::COMPONENT;


log4cxx::LoggerPtr get_logger() {
    log4cxx::LoggerPtr logger = log4cxx::Logger::getRootLogger();
    logger->setLevel(log4cxx::Level::getOff());
    return logger;
}


void init_python_path() {
    static bool initialized = false;
    if (initialized) {
        return;
    }
    using BatchExecutorUtil::GetEnv;

    std::string python_path = GetEnv("PYTHONPATH").value_or("");
    python_path += ':';

    std::string home_dir = GetEnv("HOME").value_or("/home/mpf");
    python_path += home_dir + "/openmpf-projects/openmpf-python-component-sdk/detection/api:";
    python_path += home_dir + "/mpf-sdk-install/python/site-packages:";

    if (auto mpf_sdk_install = GetEnv("MPF_SDK_INSTALL_PATH")) {
        python_path += *mpf_sdk_install + "/python/site-packages:";
    }

    std::string mpf_home = GetEnv("MPF_HOME").value_or("/opt/mpf");
    python_path += mpf_home + "/python/site-packages";

    setenv("PYTHONPATH", python_path.c_str(), true);

    initialized = true;
}


PythonComponentHandle get_component(const std::string &file_name) {
    init_python_path();
    return PythonComponentHandle(
            LoggerWrapper{"DEBUG", std::make_unique<PythonLogger>("DEBUG", "TestComponent")},
            "test_python_components/" + file_name);
}

PythonComponentHandle get_test_component() {
    return get_component("test_component.py");
}

PythonComponentHandle get_generic_only_component() {
    return get_component("generic_only_component.py");
}


TEST(PythonComponentHandleTest, TestSupportsCheck) {
    PythonComponentHandle py_component = get_test_component();
    ASSERT_TRUE(py_component.Supports(IMAGE));
    ASSERT_TRUE(py_component.Supports(VIDEO));
    ASSERT_TRUE(py_component.Supports(AUDIO));
    ASSERT_FALSE(py_component.Supports(UNKNOWN));

    PythonComponentHandle generic_only_component = get_generic_only_component();
    ASSERT_TRUE(generic_only_component.Supports(UNKNOWN));
    ASSERT_FALSE(generic_only_component.Supports(IMAGE));
    ASSERT_FALSE(generic_only_component.Supports(VIDEO));
    ASSERT_FALSE(generic_only_component.Supports(AUDIO));
}


TEST(PythonComponentHandleTest, TestUnsupportedJobType) {
    PythonComponentHandle py_component = get_test_component();

    ASSERT_FALSE(py_component.Supports(UNKNOWN));
    MPFGenericJob job("Test", "fake/path", {}, {});

    try {
        py_component.GetDetections(job);
        FAIL() << "Expected MPFDetectionException to be thrown.";
    }
    catch (const MPFDetectionException &ex) {
        ASSERT_EQ(MPF_UNSUPPORTED_DATA_TYPE, ex.error_code);
    }
}


constexpr auto job_echo_pair = std::make_pair("ECHO_JOB", "I can access job properties");
constexpr auto media_echo_pair = std::make_pair("ECHO_MEDIA", "I can access media properties");


// Make sure maps get converted between Python and C++ properly
void assert_has_echo_properties(const Properties &properties) {
    ASSERT_EQ(properties.at(job_echo_pair.first), job_echo_pair.second);
    ASSERT_EQ(properties.at(media_echo_pair.first), media_echo_pair.second);
}


TEST(PythonComponentHandleTest, TestImageJob) {
    PythonComponentHandle py_component = get_test_component();
    MPFImageJob job("Test Job Name", "path/to/media",
                    { { "job prop 1" , "job val 1" }, job_echo_pair },
                    { { "media prop 1" , "media val 1" }, media_echo_pair });

    std::vector<MPFImageLocation> results = py_component.GetDetections(job);

    ASSERT_EQ(results.size(), 2);

    ASSERT_EQ(results[0].detection_properties.at("METADATA"), "extra info for first result");
    ASSERT_EQ(results[1].detection_properties.at("METADATA"), "extra info for second result");

    for (const auto &result : results) {
        assert_has_echo_properties(result.detection_properties);
    }
}


TEST(PythonComponentHandleTest, TestVideoJob) {
    PythonComponentHandle py_component = get_test_component();
    MPFVideoJob job("Test Job", "path/to/media", 0, 10,
                    { job_echo_pair }, { media_echo_pair });

    std::vector<MPFVideoTrack> results = py_component.GetDetections(job);

    ASSERT_EQ(results.size(), 2);

    const auto &track1 = results.at(0);
    assert_has_echo_properties(track1.detection_properties);
    assert_has_echo_properties(track1.frame_locations.at(0).detection_properties);
    assert_has_echo_properties(track1.frame_locations.at(1).detection_properties);

    const auto &track2 = results.at(1);
    assert_has_echo_properties(track2.detection_properties);
    assert_has_echo_properties(track2.frame_locations.at(3).detection_properties);
}


TEST(PythonComponentHandleTest, TestAudioJob) {
    PythonComponentHandle py_component = get_test_component();
    MPFAudioJob job("Test Job", "path/to/media", 0, -1,
                    { job_echo_pair }, { media_echo_pair });

    std::vector<MPFAudioTrack> results = py_component.GetDetections(job);

    ASSERT_EQ(results.size(), 2);

    const auto &track1 = results.at(0);
    assert_has_echo_properties(track1.detection_properties);
    ASSERT_FLOAT_EQ(track1.confidence, 0.75);
    ASSERT_EQ(track1.start_time, 0);
    ASSERT_EQ(track1.stop_time, 10);

    const auto &track2 = results.at(1);
    assert_has_echo_properties(track2.detection_properties);
    ASSERT_FLOAT_EQ(track2.confidence, 1);
    ASSERT_EQ(track2.start_time, 10);
    ASSERT_EQ(track2.stop_time, 20);
}


TEST(PythonComponentHandleTest, TestGenericJob) {
    PythonComponentHandle py_component = get_generic_only_component();

    MPFGenericJob job("Test Job", "path/to/media",
                      { job_echo_pair }, { media_echo_pair });

    std::vector<MPFGenericTrack> results = py_component.GetDetections(job);

    ASSERT_EQ(results.size(), 2);

    const auto& track1 = results.at(0);
    ASSERT_FLOAT_EQ(track1.confidence, 1);
    assert_has_echo_properties(track1.detection_properties);

    const auto& track2 = results.at(1);
    ASSERT_FLOAT_EQ(track2.confidence, 2);
    assert_has_echo_properties(track2.detection_properties);
}



TEST(PythonComponentHandleTest, TestAudioFeedForward) {
    PythonComponentHandle py_component = get_test_component();

    MPFAudioTrack ff_track(1, 2, .75, { {"prop1", "val1"}, {"prop2", "val2"} });
    MPFAudioJob job("Test Job", "path/to/media", 0, 100, ff_track, { }, {});

    std::vector<MPFAudioTrack> results = py_component.GetDetections(job);

    ASSERT_EQ(results.size(), 1);

    const auto &returned_track = results.at(0);

    ASSERT_EQ(ff_track.start_time, returned_track.start_time);
    ASSERT_EQ(ff_track.stop_time, returned_track.stop_time);
    ASSERT_FLOAT_EQ(ff_track.confidence, returned_track.confidence);
    ASSERT_EQ(ff_track.detection_properties, returned_track.detection_properties);
}


void assert_image_locations_equal(const MPFImageLocation &loc1, const MPFImageLocation &loc2) {
    ASSERT_EQ(loc1.x_left_upper, loc2.x_left_upper);
    ASSERT_EQ(loc1.y_left_upper, loc2.y_left_upper);
    ASSERT_EQ(loc1.width, loc2.width);
    ASSERT_EQ(loc1.height, loc2.height);
    ASSERT_FLOAT_EQ(loc1.confidence, loc2.confidence);
    ASSERT_EQ(loc1.detection_properties, loc2.detection_properties);
}


TEST(PythonComponentHandleTest, TestImageFeedForward) {
    PythonComponentHandle py_component = get_test_component();

    MPFImageLocation ff_location(1, 2, 3, 4, .5, { {"prop1", "val1"}, {"prop2", "val2"} });
    MPFImageJob job("Test Job", "path/to/media", ff_location, {}, {});

    std::vector<MPFImageLocation> results = py_component.GetDetections(job);
    ASSERT_EQ(results.size(), 1);

    assert_image_locations_equal(ff_location, results.at(0));
}


TEST(PythonComponentHandleTest, TestVideoFeedForward) {
    PythonComponentHandle py_component = get_test_component();

    MPFVideoTrack ff_track(0, 10, .2, { {"prop1", "val1"}, {"prop2", "val2"} });
    ff_track.frame_locations.emplace(0, MPFImageLocation(1, 2, 3, 4, .5));
    ff_track.frame_locations.emplace(5, MPFImageLocation(6, 7, 8, 9, .1, { {"hello", "world"} }));

    MPFVideoJob job("Test Job", "path/to/media", 0, 20, ff_track, {}, {});

    std::vector<MPFVideoTrack> results = py_component.GetDetections(job);
    ASSERT_EQ(results.size(), 1);

    const auto &returned_track = results.at(0);
    ASSERT_EQ(ff_track.start_frame, returned_track.start_frame);
    ASSERT_EQ(ff_track.stop_frame, returned_track.stop_frame);
    ASSERT_FLOAT_EQ(ff_track.confidence, returned_track.confidence);
    ASSERT_EQ(ff_track.detection_properties, returned_track.detection_properties);
    ASSERT_EQ(ff_track.frame_locations.size(), returned_track.frame_locations.size());

    for (const auto &pair : returned_track.frame_locations) {
        assert_image_locations_equal(ff_track.frame_locations.at(pair.first), pair.second);
    }
}


TEST(PythonComponentHandleTest, TestGenericFeedForward) {
    PythonComponentHandle py_component = get_generic_only_component();

    MPFGenericTrack ff_track(1, { {"prop1", "val1"}, {"prop2", "val2"} });
    MPFGenericJob job("Test Job", "path/to/media", ff_track, {}, {});

    std::vector<MPFGenericTrack> results = py_component.GetDetections(job);
    ASSERT_EQ(results.size(), 1);

    const auto &returned_track = results.at(0);

    ASSERT_FLOAT_EQ(ff_track.confidence, returned_track.confidence);
    ASSERT_EQ(ff_track.detection_properties, returned_track.detection_properties);
}


TEST(PythonComponentHandleTest, TestDetectionExceptionTranslation) {
    PythonComponentHandle py_component = get_test_component();
    MPFDetectionError test_error = MPFDetectionError::MPF_INVALID_PROPERTY;

    MPFImageJob job("Test", "path/to/data", { {"raise_exception", std::to_string(test_error)} }, {});

    try {
        std::vector<MPFImageLocation> results = py_component.GetDetections(job);
        FAIL() << "Expected MPFDetectionException to be thrown.";
    }
    catch (const MPFDetectionException &ex) {
        ASSERT_EQ(ex.error_code, test_error);
    }
}


TEST(TestRestrictMediaTypes, CanCreateRestrictMediaTypeSelector) {
    auto restrict_media_types = Messenger::RESTRICT_MEDIA_TYPES_ENV_NAME;
    auto initial_value =  std::getenv("RESTRICT_MEDIA_TYPES");

    unsetenv(restrict_media_types);
    ASSERT_EQ(std::nullopt, Messenger::GetMediaTypeSelector());

    setenv(restrict_media_types, "", true);
    ASSERT_EQ(std::nullopt, Messenger::GetMediaTypeSelector());

    setenv(restrict_media_types, ",", true);
    ASSERT_EQ(std::nullopt, Messenger::GetMediaTypeSelector());

    setenv(restrict_media_types, "VIDEO", true);
    ASSERT_EQ("MediaType in ('VIDEO')", Messenger::GetMediaTypeSelector());

    setenv(restrict_media_types, "VIDEO, IMAGE", true);
    ASSERT_EQ("MediaType in ('VIDEO', 'IMAGE')", Messenger::GetMediaTypeSelector());

    setenv(restrict_media_types, "VIDEO,,IMAGE", true);
    ASSERT_EQ("MediaType in ('VIDEO', 'IMAGE')", Messenger::GetMediaTypeSelector());

    setenv(restrict_media_types, " VIDEO,  IMaGe ,  audio,", true);
    ASSERT_EQ("MediaType in ('VIDEO', 'IMAGE', 'AUDIO')", Messenger::GetMediaTypeSelector());

    setenv(restrict_media_types, "HELLO", true);
    ASSERT_THROW(Messenger::GetMediaTypeSelector(), std::invalid_argument);

    setenv(restrict_media_types, "VIDEO, HELLO", true);
    ASSERT_THROW(Messenger::GetMediaTypeSelector(), std::invalid_argument);

    if (initial_value == nullptr) {
        unsetenv(restrict_media_types);
    }
    else {
        setenv(restrict_media_types, initial_value, true);
    }
}


TEST(BatchExecutorUtil, get_environment_job_properties) {
    setenv("MPF_PROP_PROP1", "VALUE1", 1);
    setenv("MPF_PROP_PROP2", "VALUE2", 1);
    setenv("NOT A PROPERTY", "ASDF", 1);
    setenv("MPF_PROP BAD_PROP", "ASDF", 1);
    setenv("MPF_PROP_", "ASDF", 1);

    std::map<std::string, std::string> expected {
        {"PROP1", "VALUE1"},
        {"PROP2", "VALUE2"}
    };

    ASSERT_EQ(expected, BatchExecutorUtil::GetEnvironmentJobProperties());
}


TEST(BatchExecutorUtil, TestExpandWord) {
    setenv("MPF_TEST_VAR", "Hello", 1);
    unsetenv("MPF_TEST_MISSING_VAR");
    auto input = "$MPF_TEST_VAR, ${MPF_TEST_MISSING_VAR:-world}!";
    ASSERT_EQ("Hello, world!", BatchExecutorUtil::ExpandFileName(input));
}


struct TestComponent {
    int num_detections;

    explicit TestComponent(int num_detections = 0) : num_detections{num_detections} {
    }

    std::vector<MPFImageLocation> GetDetections(const MPFImageJob& job) {
        // Use a file path we know will exist, because the health check will fail to initialize
        // the media file does not exist.
        EXPECT_EQ(job.data_uri, "test-health-check.ini");
        EXPECT_FALSE(job.has_feed_forward_location);

        EXPECT_EQ(job.job_properties.size(), 2);
        EXPECT_EQ(job.job_properties.at("job prop1"), "job value1");
        EXPECT_EQ(job.job_properties.at("job prop2"), "job value2");

        EXPECT_EQ(job.media_properties.size(), 2);
        EXPECT_EQ(job.media_properties.at("media prop1"), "media value1");
        EXPECT_EQ(job.media_properties.at("media prop2"), "media value=2");

        return std::vector<MPFImageLocation>(num_detections);
    }

    std::vector<MPFVideoTrack> GetDetections(const MPFVideoJob&) {
        ADD_FAILURE() << "Wrong job type.";
        throw std::runtime_error{"Wrong job type."};
    }

    std::vector<MPFAudioTrack> GetDetections(const MPFAudioJob&) {
        ADD_FAILURE() << "Wrong job type.";
        throw std::runtime_error{"Wrong job type."};
    }

    std::vector<MPFGenericTrack> GetDetections(const MPFGenericJob&) {
        ADD_FAILURE() << "Wrong job type.";
        throw std::runtime_error{"Wrong job type."};
    }
};

struct FailureCounter {
    int counter = 0;

    using clock_t = std::chrono::steady_clock;
    clock_t::time_point last_failure_time;

    void operator()() {
        counter++;
        last_failure_time = clock_t::now();
    }
};


TEST(HealthCheckTest, TestHealthCheck) {
    init_python_path();

    setenv("HEALTH_CHECK", "ENABLED", 1);
    setenv("HEALTH_CHECK_RETRY_MAX_ATTEMPTS", "2", 1);

    LoggerWrapper logger{"DEBUG", std::make_unique<PythonLogger>("DEBUG", "TestComponent")};

    HealthCheck health_check{logger, "test-health-check.ini"};
    FailureCounter failure_counter;

    TestComponent test_component{4};
    ASSERT_TRUE(health_check.Check(test_component, failure_counter));
    ASSERT_EQ(0, failure_counter.counter);

    test_component.num_detections = 1;
    ASSERT_FALSE(health_check.Check(test_component, failure_counter));
    ASSERT_EQ(1, failure_counter.counter);

    ASSERT_THROW({health_check.Check(test_component, failure_counter);}, FailedHealthCheck);
    ASSERT_EQ(2, failure_counter.counter);
}


TEST(HealthCheckTest, TestHealthCheckTimeout) {
    using namespace std::literals::chrono_literals;
    init_python_path();

    setenv("HEALTH_CHECK", "ENABLED", 1);
    setenv("HEALTH_CHECK_TIMEOUT", "1", 1);
    setenv("HEALTH_CHECK_RETRY_MAX_ATTEMPTS", "2", 1);

    LoggerWrapper logger{"DEBUG", std::make_unique<PythonLogger>("DEBUG", "TestComponent")};
    HealthCheck health_check{logger, "test-health-check.ini"};
    FailureCounter failure_counter;

    TestComponent test_component{4};
    ASSERT_TRUE(health_check.Check(test_component, failure_counter));
    ASSERT_EQ(0, failure_counter.counter);

    test_component.num_detections = 1;
    ASSERT_TRUE(health_check.Check(test_component, failure_counter))
        << "While the health check is in the cooldown period, it should report the result of the last check.";
    ASSERT_EQ(0, failure_counter.counter);

    // Wait until cool down period is over.
    std::this_thread::sleep_for(1s);

    using clock_t = FailureCounter::clock_t;
    auto time_before_failed_check = clock_t::now();
    ASSERT_FALSE(health_check.Check(test_component, failure_counter));
    ASSERT_GE(clock_t::now() - time_before_failed_check, 1s)
        << "When a health check fails, the call to HealthCheck::Check should wait the cool down period before returning.";
    ASSERT_EQ(1, failure_counter.counter);
    // The call to check should take much less than 900ms, but we do not want the test to fail
    // because it was run on a machine with a high load. Any value less than 1s is sufficient to
    // to verify the cooldown period was not used.
    ASSERT_LE(failure_counter.last_failure_time - time_before_failed_check, 900ms)
        << "When a health check fails, the call to HealthCheck::Check should not wait before calling the failure callback.";

    auto time_before_final_check = clock_t::now();
    ASSERT_THROW({health_check.Check(test_component, failure_counter);}, FailedHealthCheck);
    auto time_in_final_health_check = clock_t::now() - time_before_final_check;
    ASSERT_LE(time_in_final_health_check, 900ms)
        << "The call to HealthCheck::Check should not wait the cooldown period before throwing.";
    ASSERT_EQ(2, failure_counter.counter);
}



TEST(TestLogger, CanSetAndRestoreJobName) {
    auto logger = std::make_shared<PythonLogger>("DEBUG", "TestComponent");
    auto job_name = std::make_shared<std::string>();

    ASSERT_EQ("", *job_name);
    {
        auto ctx = JobLogContext("job1", job_name, logger);
        ASSERT_EQ("job1", *job_name);
        {
            auto ctx2 = JobLogContext("job2", job_name, logger);
            ASSERT_EQ("job2", *job_name);
            {
                auto ctx3 = JobLogContext("job3", job_name, logger);
                ASSERT_EQ("job3", *job_name);
            }
            ASSERT_EQ("job2", *job_name);
        }
        ASSERT_EQ("job1", *job_name);
    }

    ASSERT_EQ("", *job_name);
    {
        auto ctx = JobLogContext("job1", job_name, logger);
        ASSERT_EQ("job1", *job_name);
        {
            auto ctx2 = JobLogContext("job2", job_name, logger);
            ASSERT_EQ("job2", *job_name);
            {
                auto ctx3 = JobLogContext("job3", job_name, logger);
                ASSERT_EQ("job3", *job_name);
            }
            ASSERT_EQ("job2", *job_name);
            {
                auto ctx4 = JobLogContext("job4", job_name, logger);
                ASSERT_EQ("job4", *job_name);
            }
            ASSERT_EQ("job2", *job_name);
        }
        ASSERT_EQ("job1", *job_name);
    }
    ASSERT_EQ("", *job_name);
}
