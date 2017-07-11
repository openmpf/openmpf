/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

#ifndef MPF_MESSAGE_QUEUE_H_
#define MPF_MESSAGE_QUEUE_H_

#include <string>

// For definition of Properties
#include "MPFComponentInterface.h"

namespace MPF {
 
struct MPFMessage {
    Properties msg_properties;
  protected:
    MPFMessage(const MPF::COMPONENT::Properties &p) 
            : msg_properties(p) {}
};

// So that the other processing components can attach to the Frame
// Storage Buffer, the SegmentReady message needs to include
// parameters needed to do that in the Properties.

struct MPFSegmentReadyMessage : MPFMessage {

    int segment_number;
    MPFSegmentReadyMessage(const int num, const MPF::COMPONENT::Properties &props)
            : MPFMessage(props),
              segment_number(num) {}
};

struct MPFFrameReadyMessage : MPFMessage {

    int segment_number;
    int frame_index;
    unsigned long frame_offset_bytes;
    MPFFrameReadyMessage(const int num,
                         const int index,
                         const unsigned long offset,
                         const MPF::COMPONENT::Properties &props) 
            : MPFMessage(props),
              segment_number(num),
              frame_index(index),
              frame_offset_bytes(offset) {}
};  

struct MPFReleaseFrameMessage : MPFMessage {

    unsigned long frame_offset_bytes;
    MPFReleaseFrameMessage(const unsigned long offset,
                         const MPF::COMPONENT::Properties &props) 
            : MPFMessage(props),
              frame_offset_bytes(offset) {}
};

struct MPFJobStatusMessage : MPFMessage {

    MPFJobStatusMessage(const MPF::COMPONENT::Properties &props) 
            : MPFMessage(props) {}
};

enum MPFMessageQueueError {
    MPF_MESSAGE_QUEUE_SUCCESS = 0,
    MPF_MESSAGE_QUEUE_NOT_STARTED,
    MPF_MESSAGE_QUEUE_NOT_CONNECTED,
    MPF_INVALID_BROKER_NAME,
    MPF_INVALID_QUEUE_NAME,
    MPF_MESSAGE_SEND_FAILED,
    MPF_MESSAGE_RECEIVE_FAILED,
    MPF_MESSAGE_QUEUE_TIMED_OUT,
    MPF_OTHER_MESSAGE_QUEUE_ERROR
};

class MPFMessageQueue {

 public:

    virtual ~MPFMessageQueue() = default;

    MPFMessageQueueError Startup(const std::string &broker_name) = 0;
    MPFMessageQueueError Connect(const std::string &queue_name) = 0;
    MPFMessageQueueError Disconnect() = 0;
    MPFMessageQueueError Shutdown() = 0;

    MPFMessageQueueError SendMessage(MPFMessage *msg) = 0;
    MPFMessageQueueError ReceiveMessage(MPFMessage *msg) = 0;
    MPFMessageQueueError TryReceiveMessage(MPFMessage *msg) = 0;

    std::string GetName() { return queueName_; }

  protected:
    MPFMessageQueue() = default;
    std::string queue_name_;

};

} // namespace MPF

#define MPF_MESSAGEQUEUE_CREATOR(name) \
  extern "C" MPF::COMPONENT::MPFMessageQueue* msg_queue_creator() { \
      return new (name);                                  \
  }

#define MPF_MESSAGEQUEUE_DELETER() \
  extern "C" MPF::COMPONENT::MPFMessageQueue* msg_queue_deleter(MPFMessageQueue *msg_queue_P_) { \
    delete msg_queue_P_; \
  }

#endif // MPF_MESSAGE_QUEUE_H_
