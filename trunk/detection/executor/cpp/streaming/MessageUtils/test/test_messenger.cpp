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

    std::string queue_name = "MPF_ACTIVITY_ALERTS";
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

    // Creae a Job Status messenger
    ASSERT_TRUE(msg_mgr_->IsConnected());
    JobStatusMessenger messenger(msg_mgr_, logger);
    EXPECT_FALSE(messenger.IsInitialized());

    std::string queue_name = "MPF_JOB_STATUS";
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

// TEST(MessengerTests, TestSegmentSummaryMessage) {

// }
