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

#ifndef MPF_MESSENGER_H_
#define MPF_MESSENGER_H_

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
  long time_elapsed;

  /* Content from the protocol buffer that will be returned in the response. */

  long request_id;           /* 1   required */
  long media_id;             /* 400 optional */
  int stage_index;           /* 401 optional */
  string stage_name;         /* 402 optional */
  int action_index;          /* 403 optional */
  string action_name;        /* 404 optional */
};

/**
 * The MPFMessenger class encapsulates ActiveMQ and Google Protocol Buffer
 * functionality associated with the MPF Tracking Component.  It utilizes
 * version 3.8.1 of the ActiveMQ C++ API and protobufs v2.5.0.
 */
class MPFMessenger {

 public:

  /**
   * Constructor
   * @param logger log4cxx Logger pointer passed in from the calling
   * program to enable this class to log to the same log file
   */
  explicit MPFMessenger(
    const log4cxx::LoggerPtr &logger);

  /**
   * Destructor
   */
  ~MPFMessenger();

  /**
   * The Startup method establishes an ActiveMQ session and creates a consumer
   * for TrackRequest messages.  This method must be called once after the
   * constructor but before any other methods are invoked.
   * @param broker_uri The remote address used to connect to the message
   * provider
   * @param request_queue The name of the queue that will be queried for
   * messages
   */
  void Startup(
    const string& broker_uri,
    const string& request_queue);

  /**
    * The Shutdown method frees memory used by ActiveMQ during program
    * execution. It is called by the destructor.
    */
  void Shutdown();

  /**
   * The ReceiveMessage method is invoked to receive the next available
   * TrackRequest message.  This method also creates an ActiveMQ message
   * producer for sending a corresponding TrackResponse message, based on
   * the ReplyTo value contained in the received message header.  As
   * currently implemented, this method blocks until a message is received.
   * The calling program is responsible for deleting the returned byte array.
   * @param msg_metadata Pointer to a MPFMessageMetadata struct to hold message header data
   * @param msg_body_length Pointer to an integer to hold the size in bytes of
   * the returned message body
   * @return A byte array containing the message body in protobuf form or
   * NULL if an error occurs
   */
  unsigned char* ReceiveMessage(
    MPFMessageMetadata* msg_metadata,
    int* msg_body_length);

  /**
   * The SendMessage method is invoked to send a TrackResponse message.
   * Note that undefined behavior may occur if this method is invoked
   * without having received and successfully unpacked a TrackRequest
   * message.
   * @param packed_msg A byte array containing the message body in protobuf
   * form
   * @param msg_metadata A pointer to a MPFMessageMetadata struct containing data needed for the
   * message header
   * @param packed_length An integer containing the size in bytes of the
   * message body
   */
  void SendMessage(const unsigned char* const packed_msg, const MPFMessageMetadata* const msg_metadata,
                   const int packed_length, const string job_name);

 private:

  bool initialized;
  activemq::core::ActiveMQConnectionFactory* connection_factory_; /**< Pointer to the created ActiveMQ Connection Factory*/
  cms::Connection* connection_;                                   /**< Pointer to the created ActiveMQ Connection */
  cms::Session* session_;                                         /**< Pointer to the created ActiveMQ Session */
  cms::Destination* request_destination_;                         /**< Pointer to the ActiveMQ TrackRequest Destination */
  cms::MessageConsumer* request_consumer_;                        /**< Pointer to the created ActiveMQ TrackRequest Consumer */
  cms::MessageProducer* response_producer_;                       /**< Pointer to the created ActiveMQ TrackResponse Producer */
  const log4cxx::LoggerPtr &main_logger_;                               /**< log4cxx Logger pointer passed in from the calling program */

};

#endif /* MPF_MESSENGER_H_ */
