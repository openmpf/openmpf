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


#ifndef MPF_BASICAMQMESSAGEREADER_H
#define MPF_BASICAMQMESSAGEREADER_H

#include <memory>
#include <string>
#include <unordered_map>

#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/MessageProducer.h>

#include "MPFMessage.h"

#include "JobSettings.h"


namespace MPF {

class BasicAmqMessageReader {

  public:

    BasicAmqMessageReader(const MPF::COMPONENT::JobSettings &job_settings,
                          std::shared_ptr<cms::Connection> connection_ptr);

    BasicAmqMessageReader &CreateFrameReadyConsumer(const long segment_number);
    MPFSegmentReadyMessage GetSegmentReadyMsg();
    MPFFrameReadyMessage GetFrameReadyMsg();
    MPFReleaseFrameMessage GetReleaseFrameMsg();
    bool GetReleaseFrameMsgNoWait(MPFReleaseFrameMessage &msg);

  private:

    const long job_id_;
    const int segment_size_;

    std::shared_ptr<cms::Connection> connection_;
    std::unique_ptr<cms::Session> session_;
    // We need to create a new consumer for each segment, but the
    // queue can be created ahead of time.
    std::unique_ptr<cms::Queue> frame_ready_queue_;

    std::unique_ptr<cms::MessageConsumer> segment_ready_consumer_;
    std::unique_ptr<cms::MessageConsumer> frame_ready_consumer_;
    std::unique_ptr<cms::MessageConsumer> release_frame_consumer_;

    std::unique_ptr<cms::MessageConsumer>
    CreateConsumer(const std::string &queue_name, cms::Session &session);

};
}

#endif //MPF_BASICAMQMESSAGESENDER_H
