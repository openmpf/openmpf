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

#include <gtest/gtest.h>
#include <memory>
#include "../PythonComponentHandle.h"

using namespace MPF::COMPONENT;


log4cxx::LoggerPtr get_logger() {
    log4cxx::LoggerPtr logger = log4cxx::Logger::getRootLogger();
    logger->setLevel(log4cxx::Level::getOff());
    return logger;
}


PythonComponentHandle get_test_component() {
    return PythonComponentHandle(get_logger(), "test_python_components/test_component.py");
}

PythonComponentHandle get_generic_only_component() {
    return PythonComponentHandle(get_logger(), "test_python_components/generic_only_component.py");
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

    std::vector<MPFGenericTrack> results;
    ASSERT_EQ(MPF_UNSUPPORTED_DATA_TYPE, py_component.GetDetections(job, results));
    ASSERT_TRUE(results.empty());
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
    ASSERT_EQ(py_component.GetDetectionType(), "TEST DETECTION TYPE");

    MPFImageJob job("Test Job Name", "path/to/media",
                    { { "job prop 1" , "job val 1" }, job_echo_pair },
                    { { "media prop 1" , "media val 1" }, media_echo_pair });

    std::vector<MPFImageLocation> results;
    auto rc = py_component.GetDetections(job, results);

    ASSERT_EQ(rc, MPF_DETECTION_SUCCESS);

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

    std::vector<MPFVideoTrack> results;
    auto rc = py_component.GetDetections(job, results);

    ASSERT_EQ(rc, MPF_DETECTION_SUCCESS);

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

    std::vector<MPFAudioTrack> results;
    auto rc = py_component.GetDetections(job, results);

    ASSERT_EQ(rc, MPF_DETECTION_SUCCESS);

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

    std::vector<MPFGenericTrack> results;
    auto rc = py_component.GetDetections(job, results);

    ASSERT_EQ(rc, MPF_DETECTION_SUCCESS);

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

    std::vector<MPFAudioTrack> results;
    auto rc = py_component.GetDetections(job, results);

    ASSERT_EQ(rc, MPF_DETECTION_SUCCESS);
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

    std::vector<MPFImageLocation> results;
    auto rc = py_component.GetDetections(job, results);
    ASSERT_EQ(rc, MPF_DETECTION_SUCCESS);
    ASSERT_EQ(results.size(), 1);

    assert_image_locations_equal(ff_location, results.at(0));
}


TEST(PythonComponentHandleTest, TestVideoFeedForward) {
    PythonComponentHandle py_component = get_test_component();

    MPFVideoTrack ff_track(0, 10, .2, { {"prop1", "val1"}, {"prop2", "val2"} });
    ff_track.frame_locations.emplace(0, MPFImageLocation(1, 2, 3, 4, .5));
    ff_track.frame_locations.emplace(5, MPFImageLocation(6, 7, 8, 9, .1, { {"hello", "world"} }));

    MPFVideoJob job("Test Job", "path/to/media", 0, 20, ff_track, {}, {});

    std::vector<MPFVideoTrack> results;
    auto rc = py_component.GetDetections(job, results);
    ASSERT_EQ(rc, MPF_DETECTION_SUCCESS);
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

    std::vector<MPFGenericTrack> results;
    auto rc = py_component.GetDetections(job, results);
    ASSERT_EQ(rc, MPF_DETECTION_SUCCESS);
    ASSERT_EQ(results.size(), 1);

    const auto &returned_track = results.at(0);

    ASSERT_FLOAT_EQ(ff_track.confidence, returned_track.confidence);
    ASSERT_EQ(ff_track.detection_properties, returned_track.detection_properties);
}


TEST(PythonComponentHandleTest, TestDetectionExceptionTranslation) {
    PythonComponentHandle py_component = get_test_component();
    MPFDetectionError test_error = MPFDetectionError::MPF_INVALID_PROPERTY;

    MPFImageJob job("Test", "path/to/data", { {"raise_exception", std::to_string(test_error)} }, {});

    std::vector<MPFImageLocation> results;
    auto rc = py_component.GetDetections(job, results);

    ASSERT_EQ(rc, test_error);
}
