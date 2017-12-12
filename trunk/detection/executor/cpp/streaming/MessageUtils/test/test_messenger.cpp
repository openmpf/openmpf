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

#include <string>
#include <gtest/gtest.h>


#include "MPFDetectionComponent.h"
#include "MPFAMQMessage.h"
#include "MPFAMQMessenger.h"

using namespace std;
using namespace MPF;
using namespace COMPONENT;


TEST(MessageManagerTest, MessageManagerLifeCycle) {
    // Create a message manager object
    log4cxx::LoggerPtr logger = log4cxx::Logger::getRootLogger();

    std::unique_ptr<AMQMessagingManager> msg_mgr(new AMQMessagingManager(logger));
    ASSERT_TRUE(NULL != msg_mgr);

    std::string broker_uri = "tcp://localhost:61616?trace=true";
    MPF::COMPONENT::Properties props;
    ASSERT_NO_THROW(msg_mgr->Connect(broker_uri, props));

    ASSERT_TRUE(msg_mgr->IsConnected());

    ASSERT_NO_THROW(msg_mgr->Start());

    ASSERT_TRUE(msg_mgr->IsStarted());

    ASSERT_NO_THROW(msg_mgr->Stop());

    EXPECT_FALSE(msg_mgr->IsStarted());

    ASSERT_NO_THROW(msg_mgr->Shutdown());

    EXPECT_FALSE(msg_mgr->IsConnected());

}  // End of TEST(MessageManager, MessageManagerLifeCycle)


class AMQMessengerTest : public ::testing::Test {
  protected:
    AMQMessengerTest() = default;

    static std::shared_ptr<AMQMessagingManager> msg_mgr_;

    static void SetUpTestCase() {
        // Create a message manager object
        log4cxx::LoggerPtr logger = log4cxx::Logger::getRootLogger();

        msg_mgr_.reset(new AMQMessagingManager(logger));

        std::string broker_uri = "tcp://localhost:61616?trace=true";
        MPF::COMPONENT::Properties props;
        msg_mgr_->Connect(broker_uri, props);

        msg_mgr_->Start();
    }

    static void TearDownTestCase() {
        msg_mgr_->Stop();
        msg_mgr_->Shutdown();
    }
};

std::shared_ptr<AMQMessagingManager> AMQMessengerTest::msg_mgr_ = NULL;

TEST_F(AMQMessengerTest, TestActivityAlertMessage) {

    log4cxx::LoggerPtr logger = log4cxx::Logger::getRootLogger();

    // Create an Activity Alert messenger
    ASSERT_TRUE(msg_mgr_->IsConnected());
    ActivityAlertMessenger messenger(msg_mgr_, logger);
    EXPECT_FALSE(messenger.IsInitialized());

    std::string queue_name = "GTEST_MPF_ACTIVITY_ALERTS";
    MPF::COMPONENT::Properties props;

    ASSERT_NO_THROW(messenger.InitQueue(queue_name, props));
    EXPECT_TRUE(messenger.IsInitialized());

    ASSERT_NO_THROW(messenger.CreateProducer());

    ASSERT_NO_THROW(messenger.CreateConsumer());

    ASSERT_TRUE(msg_mgr_->IsStarted());
    int job_id = 107;
    string job_name("activity_alert_job_" + std::to_string(job_id));
    int segment_number = 54;
    int frame_index = 321;
    long activity_time = 10059;

    MPFActivityAlertMessage src_msg(job_name, job_id, 
                                    segment_number, frame_index,
                                    activity_time);
    ASSERT_NO_THROW(messenger.SendMessage(src_msg));

    MPFActivityAlertMessage dst_msg;
    ASSERT_NO_THROW(dst_msg = messenger.GetMessage());

    EXPECT_EQ(job_id, dst_msg.job_number_);
    EXPECT_EQ(job_name, dst_msg.job_name_);
    EXPECT_EQ(segment_number, dst_msg.segment_number_);
    EXPECT_EQ(frame_index, dst_msg.frame_index_);
    EXPECT_EQ(activity_time, dst_msg.activity_time_);

    ASSERT_NO_THROW(messenger.Close());

}


TEST_F(AMQMessengerTest, TestJobStatusMessage) {

    log4cxx::LoggerPtr logger = log4cxx::Logger::getRootLogger();

    // Create a Job Status messenger
    ASSERT_TRUE(msg_mgr_->IsConnected());
    JobStatusMessenger messenger(msg_mgr_, logger);
    EXPECT_FALSE(messenger.IsInitialized());

    std::string queue_name = "GTEST_MPF_JOB_STATUS";
    MPF::COMPONENT::Properties props;

    ASSERT_NO_THROW(messenger.InitQueue(queue_name, props));
    EXPECT_TRUE(messenger.IsInitialized());

    ASSERT_NO_THROW(messenger.CreateProducer());

    ASSERT_NO_THROW(messenger.CreateConsumer());

    ASSERT_TRUE(msg_mgr_->IsStarted());
    int job_id = 14;
    string job_name("streaming_job_" + std::to_string(job_id));
    string message("Job " + job_name + " is up and running");

    MPFJobStatusMessage src_msg(job_name, job_id, message);
    ASSERT_NO_THROW(messenger.SendMessage(src_msg));

    MPFJobStatusMessage dst_msg;
    ASSERT_NO_THROW(dst_msg = messenger.GetMessage());

    EXPECT_EQ(job_id, dst_msg.job_number_);
    EXPECT_EQ(job_name, dst_msg.job_name_);
    EXPECT_EQ(message, dst_msg.status_message_);

    ASSERT_NO_THROW(messenger.Close());
}

TEST_F(AMQMessengerTest, TestSegmentSummaryMessage) {
    log4cxx::LoggerPtr logger = log4cxx::Logger::getRootLogger();

    // Create a Segment Summary messenger
    ASSERT_TRUE(msg_mgr_->IsConnected());
    SegmentSummaryMessenger messenger(msg_mgr_, logger);
    EXPECT_FALSE(messenger.IsInitialized());

    std::string queue_name = "GTEST_MPF_SEGMENT_SUMMARY";
    MPF::COMPONENT::Properties props;

    ASSERT_NO_THROW(messenger.InitQueue(queue_name, props));
    EXPECT_TRUE(messenger.IsInitialized());

    ASSERT_NO_THROW(messenger.CreateProducer());

    ASSERT_NO_THROW(messenger.CreateConsumer());

    ASSERT_TRUE(msg_mgr_->IsStarted());
    int job_id = 5000;
    string job_name("streaming_job_" + std::to_string(job_id));
    int seg_num = 45;
    int start_frame = 1000;
    int stop_frame = 1750;
    std::string type("FACE");
    MPF::COMPONENT::MPFDetectionError err = MPF_INVALID_DATAFILE_URI;
    int num_tracks = 3;
    int num_images_per_track = 2;
    // Create the segment summary tracks
    std::vector<MPF::COMPONENT::MPFVideoTrack> tracks;

    for (int i = 0; i < num_tracks; i++) {
        std::map<int, MPF::COMPONENT::MPFImageLocation> loc_map;
        for (int j = 0; j < num_images_per_track; j++) {
            MPF::COMPONENT::Properties imageprops;
            imageprops["IMAGE_PROPERTY_J1"] = std::to_string(j+1);
            imageprops["IMAGE_PROPERTY_J2"] = std::to_string(j+2);
            MPF::COMPONENT::MPFImageLocation loc(/*x_left_upper*/ j+1,
                                                 /*y_left_upper*/ j+2,
                                                 /*width*/ j+3,
                                                 /*height*/ j+4,
                                                 /*confidence*/ (j+5)*0.001,
                                                 imageprops);
            loc_map[j+1] = loc;
        }
        MPF::COMPONENT::Properties trackprops;
        trackprops["TRACK_PROPERTY_I3"] = std::to_string(i+3);
        trackprops["TRACK_PROPERTY_I4"] = std::to_string(i+4);
        MPF::COMPONENT::MPFVideoTrack new_track(/*start_frame*/ i+10,
                                                /*stop_frame*/ i+20,
                                                /*confidence*/(i+30)*0.5,
                                                trackprops);
        new_track.frame_locations = loc_map;
        tracks.push_back(new_track);
    }

    MPFSegmentSummaryMessage src_msg(job_name, job_id, seg_num,
                                     start_frame, stop_frame,
                                     type, err,tracks);
    ASSERT_NO_THROW(messenger.SendMessage(src_msg));

    MPFSegmentSummaryMessage dst_msg;
    ASSERT_NO_THROW(dst_msg = messenger.GetMessage());

    EXPECT_EQ(job_id, dst_msg.job_number_);
    EXPECT_EQ(job_name, dst_msg.job_name_);
    EXPECT_EQ(seg_num, dst_msg.segment_number_);
    EXPECT_EQ(start_frame, dst_msg.segment_start_frame_);
    EXPECT_EQ(stop_frame, dst_msg.segment_stop_frame_);
    EXPECT_EQ(type, dst_msg.detection_type_);
    EXPECT_EQ(err, dst_msg.segment_error_);

    // Check the segment summary tracks
    // First make sure we received the right number of tracks.
    ASSERT_EQ(num_tracks, dst_msg.tracks_.size());
    // Now check the contents of each track
    for (int i = 0; i < num_tracks; i++) {
        MPF::COMPONENT::MPFVideoTrack track = dst_msg.tracks_[i];
        EXPECT_EQ(i+10, track.start_frame);
        EXPECT_EQ(i+20, track.stop_frame);
        EXPECT_FLOAT_EQ((i+30)*0.5, track.confidence);
        EXPECT_EQ(std::to_string(i+3), track.detection_properties["TRACK_PROPERTY_I3"]);
        EXPECT_EQ(std::to_string(i+4), track.detection_properties["TRACK_PROPERTY_I4"]);

        // Make sure we got the right number of image locations
        ASSERT_EQ(num_images_per_track, track.frame_locations.size());
        for (int j = 0; j < num_images_per_track; j++) {
            // Check whether the image location key we are looking for is
            // in the map
            auto loc_iter = track.frame_locations.find(j+1);
            ASSERT_NE(track.frame_locations.end(), loc_iter);
            MPF::COMPONENT::MPFImageLocation loc = track.frame_locations[j+1];
            // Check the properties of this image location
            EXPECT_EQ(std::to_string(j+1), loc.detection_properties["IMAGE_PROPERTY_J1"]);
            EXPECT_EQ(std::to_string(j+2), loc.detection_properties["IMAGE_PROPERTY_J2"]);
            // Check the other members
            EXPECT_EQ(j+1, loc.x_left_upper);
            EXPECT_EQ(j+2, loc.y_left_upper);
            EXPECT_EQ(j+3, loc.width);
            EXPECT_EQ(j+4, loc.height);
            EXPECT_FLOAT_EQ((j+5)*0.001, loc.confidence);
        }
    }

    ASSERT_NO_THROW(messenger.Close());

}
