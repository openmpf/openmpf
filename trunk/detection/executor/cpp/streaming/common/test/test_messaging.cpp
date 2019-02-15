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

#include <string>
#include <thread> // for std::this_thread::sleep_for()
#include <chrono> // for std::chrono::milliseconds()
#include <gtest/gtest.h>

#include "detection.pb.h"

#include "JobSettings.h"
#include "MPFMessage.h"
#include "MPFMessagingConnection.h"
#include "BasicAmqMessageReader.h"
#include "BasicAmqMessageSender.h"

using namespace std;
using namespace MPF;
using namespace COMPONENT;
using namespace std::chrono;



TEST(MessageTests, MessagingConnectionTest) {
    // Create a MessagingConnection object
    std::string ini_path("./data/test.ini");
    JobSettings settings = JobSettings::FromIniFile(ini_path);

    MPFMessagingConnection *msg_mgr = new MPFMessagingConnection(settings);

    ASSERT_TRUE(NULL != msg_mgr->GetConnection());
    ASSERT_TRUE(NULL != msg_mgr->GetSession());

    delete msg_mgr;

}  // End of TEST(MessageTests, MessagingConnectionTest)



class StreamingMessagingTest : public ::testing::Test {
  protected:
    StreamingMessagingTest() = default;

    static std::unique_ptr<MPFMessagingConnection> msg_mgr_;
    static std::unique_ptr<BasicAmqMessageReader<MPFFrameReadyMessage> > frame_ready_reader_;
    static std::unique_ptr<BasicAmqMessageReader<MPFSegmentReadyMessage> > segment_ready_reader_;
    static std::unique_ptr<BasicAmqMessageReader<MPFFrameReadyMessage> > release_frame_reader_;

    static std::unique_ptr<BasicAmqMessageSender<MPFSegmentReadyMessage>> segment_ready_sender_;
    static std::unique_ptr<BasicAmqMessageSender<MPFFrameReadyMessage>> frame_ready_sender_;
    static std::unique_ptr<BasicAmqMessageSender<MPFFrameReadyMessage>> release_frame_sender_;
    static std::unique_ptr<BasicAmqMessageSender<MPFJobStatusMessage>> job_status_sender_;
    static std::unique_ptr<BasicAmqMessageSender<MPFActivityAlertMessage>> activity_alert_sender_;
    static std::unique_ptr<BasicAmqMessageSender<MPFSegmentSummaryMessage>> summary_report_sender_;

    static MPF::COMPONENT::JobSettings settings_;

    static void SetUpTestCase() {
        msg_mgr_.reset(new MPFMessagingConnection(settings_));
        frame_ready_reader_.reset(new BasicAmqMessageReader<MPFFrameReadyMessage>(
            settings_.frame_ready_queue_stage1,
            *msg_mgr_.get()));
        segment_ready_reader_.reset(new BasicAmqMessageReader<MPFSegmentReadyMessage>(
            settings_.segment_ready_queue_stage1,
            *msg_mgr_.get()));
        release_frame_reader_.reset(new BasicAmqMessageReader<MPFFrameReadyMessage>(
            settings_.release_frame_queue,
            *msg_mgr_.get()));

        segment_ready_sender_.reset(new BasicAmqMessageSender<MPFSegmentReadyMessage>(settings_.segment_ready_queue_stage1,
                                                                                      *msg_mgr_.get()));
        frame_ready_sender_.reset(new BasicAmqMessageSender<MPFFrameReadyMessage>(settings_.frame_ready_queue_stage1,
                                                                                  *msg_mgr_.get()));
        release_frame_sender_.reset(new BasicAmqMessageSender<MPFFrameReadyMessage>(settings_.release_frame_queue,
                                                                                    *msg_mgr_.get()));
        job_status_sender_.reset(new BasicAmqMessageSender<MPFJobStatusMessage>(settings_.job_status_queue,
                                                                                *msg_mgr_.get()));
        activity_alert_sender_.reset(new BasicAmqMessageSender<MPFActivityAlertMessage>(settings_.activity_alert_queue,
                                                                                        *msg_mgr_.get()));
        summary_report_sender_.reset(new BasicAmqMessageSender<MPFSegmentSummaryMessage>(settings_.summary_report_queue,
                                                                                         *msg_mgr_.get()));
}

    static void TearDownTestCase() {}

    long getTimestamp() {
        return duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count();
    }
};

std::unique_ptr<BasicAmqMessageReader<MPFFrameReadyMessage> > StreamingMessagingTest::frame_ready_reader_ = NULL;
std::unique_ptr<BasicAmqMessageReader<MPFSegmentReadyMessage> > StreamingMessagingTest::segment_ready_reader_ = NULL;
std::unique_ptr<BasicAmqMessageReader<MPFFrameReadyMessage> > StreamingMessagingTest::release_frame_reader_ = NULL;

std::unique_ptr<BasicAmqMessageSender<MPFSegmentReadyMessage> > StreamingMessagingTest::segment_ready_sender_ = NULL;
std::unique_ptr<BasicAmqMessageSender<MPFFrameReadyMessage> > StreamingMessagingTest::frame_ready_sender_ = NULL;
std::unique_ptr<BasicAmqMessageSender<MPFFrameReadyMessage> > StreamingMessagingTest::release_frame_sender_ = NULL;
std::unique_ptr<BasicAmqMessageSender<MPFJobStatusMessage> > StreamingMessagingTest::job_status_sender_ = NULL;
std::unique_ptr<BasicAmqMessageSender<MPFActivityAlertMessage> > StreamingMessagingTest::activity_alert_sender_ = NULL;
std::unique_ptr<BasicAmqMessageSender<MPFSegmentSummaryMessage> > StreamingMessagingTest::summary_report_sender_ = NULL;

std::unique_ptr<MPFMessagingConnection> StreamingMessagingTest::msg_mgr_ = NULL;
MPF::COMPONENT::JobSettings StreamingMessagingTest::settings_ = JobSettings::FromIniFile("./data/test.ini");



TEST_F(StreamingMessagingTest, TestActivityAlertMessage) {

    // The BasicAmqMessageReader doesn't read any of the "status" messages;
    // those are only ever read by the WFM. So, to make sure that the
    // message was sent properly, create a consumer for it here.

    std::shared_ptr<cms::Session> session(msg_mgr_->GetSession());
    std::unique_ptr<cms::Queue> queue(session->createQueue(settings_.activity_alert_queue));
    std::unique_ptr<cms::MessageConsumer> consumer(session->createConsumer(queue.get()));

    int frame_number = 10;
    long timestamp = getTimestamp();
    // Test activity alert message
    MPFActivityAlertMessage alert_msg(settings_.job_id, frame_number, timestamp);
    ASSERT_NO_THROW( activity_alert_sender_->SendMsg(alert_msg) );

    std::unique_ptr<cms::Message> msg;
    ASSERT_NO_THROW( msg.reset(consumer->receive()) );
    ASSERT_EQ(msg->getLongProperty("JOB_ID"), settings_.job_id);
    ASSERT_EQ(msg->getIntProperty("FRAME_INDEX"), frame_number);
    ASSERT_EQ(msg->getLongProperty("ACTIVITY_DETECTION_TIMESTAMP"), timestamp);

    
}

TEST_F(StreamingMessagingTest, TestJobStatusMessage) {

    std::shared_ptr<cms::Session> session(msg_mgr_->GetSession());
    std::unique_ptr<cms::Queue> queue(session->createQueue(settings_.job_status_queue));
    std::unique_ptr<cms::MessageConsumer> consumer(session->createConsumer(queue.get()));

    // Test the job status message
    std::string status_msg("TestJobStatus");
    long timestamp = getTimestamp();
    MPFJobStatusMessage job_msg(settings_.job_id, status_msg, timestamp);
    ASSERT_NO_THROW(job_status_sender_->SendMsg(job_msg));

    std::unique_ptr<cms::Message> msg;
    ASSERT_NO_THROW( msg.reset(consumer->receive()) );
    ASSERT_EQ(msg->getLongProperty("JOB_ID"), settings_.job_id);
    ASSERT_EQ(msg->getStringProperty("JOB_STATUS"), status_msg);
    ASSERT_EQ(msg->getLongProperty("STATUS_CHANGE_TIMESTAMP"), timestamp);

}

TEST_F(StreamingMessagingTest, TestSegmentReadyMessage) {

    int segment_number = 51;
    int width = 100;
    int height = 250;
    int cvType = 7;
    int cvBytes = 3;

    MPFSegmentReadyMessage send_msg(settings_.job_id, segment_number, width, height, cvType, cvBytes);
    ASSERT_NO_THROW(segment_ready_sender_->SendMsg(send_msg));

    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    MPFSegmentReadyMessage rcv_msg;
    bool got_msg;
    ASSERT_NO_THROW( got_msg = segment_ready_reader_->GetMsgNoWait(rcv_msg) );
    ASSERT_TRUE(got_msg);
    ASSERT_EQ(rcv_msg.job_id, settings_.job_id);
    ASSERT_EQ(rcv_msg.segment_number, segment_number);
    ASSERT_EQ(rcv_msg.frame_width, width);
    ASSERT_EQ(rcv_msg.frame_height, height);
    ASSERT_EQ(rcv_msg.cvType, cvType);
    ASSERT_EQ(rcv_msg.bytes_per_pixel, cvBytes);
}


TEST_F(StreamingMessagingTest, TestFrameReadyMessage) {

    int index = 72;
    int segment_number = 29;
    long timestamp = getTimestamp();

    std::string selector = "SEGMENT_NUMBER = " + std::to_string(segment_number);
    ASSERT_NO_THROW(frame_ready_reader_.reset(new
        BasicAmqMessageReader<MPFFrameReadyMessage>(settings_.frame_ready_queue_stage1,
                                                    selector, *msg_mgr_.get())));

    MPFFrameReadyMessage send_msg(settings_.job_id, segment_number, index, timestamp, true);
    ASSERT_NO_THROW(frame_ready_sender_->SendMsg(send_msg));

    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    MPFFrameReadyMessage rcv_msg;
    bool got_msg;
    ASSERT_NO_THROW( got_msg = frame_ready_reader_->GetMsgNoWait(rcv_msg) );
    ASSERT_TRUE(got_msg);
    ASSERT_EQ(rcv_msg.job_id, settings_.job_id);
    ASSERT_EQ(rcv_msg.segment_number, segment_number);
    ASSERT_EQ(rcv_msg.frame_index, index);
    ASSERT_EQ(rcv_msg.frame_timestamp, timestamp);
    ASSERT_TRUE(rcv_msg.process_this_frame);

}

TEST_F(StreamingMessagingTest, TestReleaseFrameMessage) {

    int index = 72117;
    int segment_num = 445;
    long timestamp = getTimestamp();

    MPFFrameReadyMessage send_msg(settings_.job_id, segment_num, index, timestamp, true);
    ASSERT_NO_THROW(release_frame_sender_->SendMsg(send_msg));

    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    MPFFrameReadyMessage rcv_msg;
    bool got_msg;
    ASSERT_NO_THROW( got_msg = release_frame_reader_->GetMsgNoWait(rcv_msg) );
    ASSERT_TRUE(got_msg);
    ASSERT_EQ(rcv_msg.job_id, settings_.job_id);
    ASSERT_EQ(rcv_msg.frame_index, index);
    ASSERT_EQ(rcv_msg.segment_number, segment_num);
    ASSERT_EQ(rcv_msg.frame_timestamp, timestamp);
    ASSERT_TRUE(rcv_msg.process_this_frame);

}

// A function to convert the contents of a StreamingDetectionResponse
// protobuf to an MPFSegmentSummaryMessage. This would normally be
// done only in the WFM, so this particular function is for test
// purposes only.
typedef org::mitre::mpf::wfm::buffers::StreamingDetectionResponse PbStreamingResponse;

MPFSegmentSummaryMessage convertToSegmentSummaryMsg(const cms::Message &msg) {
    // Unpack the Video Track info from the protobuf in the cms message.
    std::vector<MPF::COMPONENT::MPFVideoTrack> mpfTracks;
    int seg_num = -1;
    int start_frame = -1;
    int stop_frame = -1;
    std::string detection_type;
    std::string err_str = {};
    std::unordered_map<int, long> times;

    const cms::BytesMessage &bytes_msg = dynamic_cast<const cms::BytesMessage&>(msg);
    int length = bytes_msg.getBodyLength();
    std::unique_ptr<unsigned char[]> contents(new unsigned char[length]());
    if (NULL != contents) {
        bytes_msg.readBytes(contents.get(), length);
        PbStreamingResponse response;
        response.ParseFromArray(static_cast<const void *>(contents.get()), length);
        seg_num = response.segment_number();
        start_frame = response.segment_start_frame();
        stop_frame = response.segment_stop_frame();
        detection_type = response.detection_type();
        err_str = response.error();
        for (auto &msg_track : response.video_tracks()) {
            MPF::COMPONENT::MPFVideoTrack mpf_track;
            mpf_track.start_frame = msg_track.start_frame();
            times[start_frame] = msg_track.start_time();
            mpf_track.stop_frame = msg_track.stop_frame();
            times[stop_frame] = msg_track.stop_time();
            mpf_track.confidence = msg_track.confidence();
            // Copy the frame locations
            for (auto &loc : msg_track.detections()) {
                MPF::COMPONENT::Properties tmp_props;
                for (auto prop : loc.detection_properties()) {
                    tmp_props[prop.key()] = prop.value();
                }
                MPF::COMPONENT::MPFImageLocation tmp_loc(
                    loc.x_left_upper(),
                    loc.y_left_upper(),
                    loc.width(),
                    loc.height(),
                    loc.confidence(),
                    tmp_props);

                mpf_track.frame_locations[loc.frame_number()] = tmp_loc;
            }
            // Copy the track properties
            for (auto &prop : msg_track.detection_properties()) {
                mpf_track.detection_properties[prop.key()] = prop.value();
            }
            // Add the track to the track vector
            mpfTracks.push_back(mpf_track);
        }
    }

    return MPFSegmentSummaryMessage(msg.getLongProperty("JOB_ID"),
                                    seg_num, start_frame, stop_frame,
                                    detection_type, err_str,
                                    mpfTracks, times);
}



TEST_F(StreamingMessagingTest, TestSegmentSummaryMessage) {
    int seg_num = 45;
    int frame_index = 4599;
    std::string type("FACE");

    // Create the segment summary tracks
    int num_tracks = 3;
    int num_images_per_track = 2;
    std::vector<MPF::COMPONENT::MPFVideoTrack> orig_tracks;
    int start_frame = seg_num*settings_.segment_size;
    int stop_frame = start_frame + 10;
    std::unordered_map<int, long> frame_timestamps;
    for (int i = 0; i < settings_.segment_size; i++) {
        frame_timestamps[start_frame+i] = getTimestamp();
    }

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
            loc_map[start_frame + j] = loc;
        }
        MPF::COMPONENT::Properties trackprops;
        trackprops["TRACK_PROPERTY_I3"] = std::to_string(i+3);
        trackprops["TRACK_PROPERTY_I4"] = std::to_string(i+4);
        MPF::COMPONENT::MPFVideoTrack new_track(/*start_frame*/ start_frame + i,
                                                /*stop_frame*/ stop_frame + i,
                                                /*confidence*/(i+30)*0.5,
                                                trackprops);
        new_track.frame_locations = loc_map;
        orig_tracks.push_back(new_track);
    }

    MPFSegmentSummaryMessage send_msg(settings_.job_id, seg_num, seg_num*settings_.segment_size,
                                      frame_index, type, "", orig_tracks, frame_timestamps);
    ASSERT_NO_THROW(summary_report_sender_->SendMsg(send_msg));

    // The BasicAmqMessageReader doesn't read the summary report messages;
    // those are only ever read by the WFM. So, to make sure that the
    // message was sent properly, create a consumer for it here.

    std::shared_ptr<cms::Session> session(msg_mgr_->GetSession());
    std::unique_ptr<cms::Queue> queue(session->createQueue(settings_.summary_report_queue));
    std::unique_ptr<cms::MessageConsumer> consumer(session->createConsumer(queue.get()));

    std::unique_ptr<cms::Message> cms_msg;

    ASSERT_NO_THROW(cms_msg.reset(consumer->receive()));
    ASSERT_NE(nullptr, cms_msg.get());

    MPFSegmentSummaryMessage dst_msg = convertToSegmentSummaryMsg(*cms_msg);

    EXPECT_EQ(dst_msg.job_id, settings_.job_id);
    EXPECT_EQ(dst_msg.segment_number, seg_num);
    EXPECT_EQ(dst_msg.segment_start_frame, seg_num*settings_.segment_size);
    EXPECT_EQ(dst_msg.segment_stop_frame, frame_index);
    EXPECT_EQ(dst_msg.detection_type, type);
    EXPECT_TRUE(dst_msg.segment_error.empty());

    // Check the segment summary tracks
    // First make sure we received the right number of tracks.
    ASSERT_EQ(num_tracks, dst_msg.tracks.size());
    // Now check the contents of each track
    int track_idx = 0;

    for (auto track : dst_msg.tracks) {
        auto orig_track = orig_tracks[track_idx];
        EXPECT_EQ(track.start_frame, orig_track.start_frame);
        EXPECT_EQ(track.stop_frame, orig_track.stop_frame);
        EXPECT_FLOAT_EQ(track.confidence, orig_track.confidence);
        EXPECT_EQ(track.detection_properties["TRACK_PROPERTY_I3"], orig_track.detection_properties["TRACK_PROPERTY_I3"]);
        EXPECT_EQ(track.detection_properties["TRACK_PROPERTY_I4"], orig_track.detection_properties["TRACK_PROPERTY_I4"]);

        // Make sure we got the right number of image locations
        ASSERT_EQ(num_images_per_track, track.frame_locations.size());
        for(int j = 0; j < num_images_per_track; j++) {
            auto loc_iter = track.frame_locations.find(start_frame + j);
            ASSERT_NE(loc_iter, track.frame_locations.end());
            auto orig_loc_iter = orig_track.frame_locations.find(start_frame + j);
            ASSERT_NE(orig_loc_iter, orig_track.frame_locations.end());
            auto loc = loc_iter->second;
            auto orig_loc = orig_loc_iter->second;
            // Check the properties of this image location
            EXPECT_EQ(loc.detection_properties["IMAGE_PROPERTY_J1"],
                      orig_loc.detection_properties["IMAGE_PROPERTY_J1"]);
            EXPECT_EQ(loc.detection_properties["IMAGE_PROPERTY_J2"],
                      orig_loc.detection_properties["IMAGE_PROPERTY_J2"]);
            // Check the other members
            EXPECT_EQ(loc.x_left_upper, orig_loc.x_left_upper);
            EXPECT_EQ(loc.y_left_upper, orig_loc.y_left_upper);
            EXPECT_EQ(loc.width, orig_loc.width);
            EXPECT_EQ(loc.height, orig_loc.height);
            EXPECT_FLOAT_EQ(loc.confidence, orig_loc.confidence);
        }
        track_idx++;
    }

}

