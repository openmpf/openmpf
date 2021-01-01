/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

#ifndef MPF_MESSENGER_H_
#define MPF_MESSENGER_H_

#include <memory>
#include <string>
#include <vector>
#include <log4cxx/logger.h>
#include <activemq/library/ActiveMQCPP.h>
#include <activemq/core/ActiveMQConnectionFactory.h>
#include <activemq/core/PrefetchPolicy.h>
#include <activemq/core/policies/DefaultPrefetchPolicy.h>
#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/MessageConsumer.h>
#include <cms/MessageProducer.h>

using std::string;

/**
 * The MPFMessageMetadata struct holds detection request data that must be included in the corresponding response. */
struct MPFMessageMetadata {

  /* Content from the incoming headers that must be returned in the outgoing headers. */
  string correlation_id;
  string bread_crumb_id;
  int split_size;
  long job_id;

  /* Content from the protocol buffer that will be returned in the response. */

  long request_id;           /* 1   required */
  long media_id;             /* 400 optional */
  int task_index;            /* 401 optional */
  string task_name;          /* 402 optional */
  int action_index;          /* 403 optional */
  string action_name;        /* 404 optional */
};

// Need to use a base class for this so that the destructors for MPFMessenger's fields get called before
// AmqLibraryManager's destructor. If ActiveMQCPP::shutdownLibrary(); was in ~MPFMessenger, then it would be
// called before MPFMessenger's ActiveMQ-related fields were destroyed. This is because in C++ the destructor body
// runs, then the destructors for each of the fields runs, then the base class destructor runs.
class AmqLibraryManager {
public:
    AmqLibraryManager();
    ~AmqLibraryManager();
};

/**
 * The MPFMessenger class encapsulates ActiveMQ and Google Protocol Buffer
 * functionality associated with the MPF Tracking Component.  It utilizes
 * version 3.8.1 of the ActiveMQ C++ API and protobufs v2.5.0.
 */
class MPFMessenger : private AmqLibraryManager {

 public:

  /**
   * Constructor
   * Establishes an ActiveMQ session and creates a consumer for DetectionRequest messages.
   * @param logger log4cxx Logger pointer passed in from the calling
   * program to enable this class to log to the same log file
   * @param broker_uri The remote address used to connect to the message * provider
   * @param request_queue The name of the queue that will be queried for * messages
   */
  explicit MPFMessenger(const log4cxx::LoggerPtr &logger, const string& broker_uri, const string& request_queue);

  /**
   * The ReceiveMessage method is invoked to receive the next available
   * DetectionRequest message.  This method also creates an ActiveMQ message
   * producer for sending a corresponding DetectionResponse message, based on
   * the ReplyTo value contained in the received message header.  As
   * currently implemented, this method blocks until a message is received.
   * @param msg_metadata MPFMessageMetadata struct to hold message header data
   * @return A byte vector containing the message body in protobuf form or an empty vector if no message is available.
   */
  std::vector<unsigned char> ReceiveMessage(MPFMessageMetadata& msg_metadata);

  /**
   * The SendMessage method is invoked to send a DetectionResponse message.
   * Note that undefined behavior may occur if this method is invoked
   * without having received and successfully unpacked a DetectionRequest
   * message.
   * @param packed_msg A byte vector containing the message body in protobuf form
   * @param msg_metadata MPFMessageMetadata struct containing data needed for the message header
   * @param job_name A string containing the job name
   */
  void SendMessage(const std::vector<unsigned char> &packed_msg, const MPFMessageMetadata &msg_metadata,
                   const string &job_name);

  static constexpr const char* RESTRICT_MEDIA_TYPES_ENV_NAME = "RESTRICT_MEDIA_TYPES";

  static std::string GetMediaTypeSelector();

 private:

  std::unique_ptr<activemq::core::ActiveMQConnectionFactory> connection_factory_;
  std::unique_ptr<cms::Connection> connection_;
  std::unique_ptr<cms::Session> session_;
  std::unique_ptr<cms::Destination> request_destination_;
  std::unique_ptr<cms::MessageConsumer> request_consumer_;
  std::unique_ptr<cms::MessageProducer> response_producer_;
  const log4cxx::LoggerPtr main_logger_;

};

#endif /* MPF_MESSENGER_H_ */
