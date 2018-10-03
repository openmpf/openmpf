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

#ifndef MPF_AMQ_MESSENGER_H_
#define MPF_AMQ_MESSENGER_H_

#include <memory>
#include <string>

#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/MessageConsumer.h>
#include <cms/MessageProducer.h>
#include <log4cxx/logger.h>

#include "MPFAMQMessage.h"

namespace MPF {

class MPFMessagingConnection {
  public:

    explicit MPFMessagingConnection(const JobSettings &job_settings);
    
    ~MPFMessagingConnection();
    cms::Connection* operator->() {
        return connection_.get();
    }

  private:

    static std::unique_ptr<cms::Connection> Connect(const std::string &broker_uri);
    std::unique_ptr<cms::Connection> connection_;
};



template <typename MSG_CONVERTER>
class AMQMessageConsumer {
 public:
    AMQMessageConsumer(AMQMessagingConnection &msg_connection,
                       const log4cxx::LoggerPtr &logger,
                       std::string &queue_name) 
            : logger_(logger)
            , session_(msg_connection->createSession())
            , msg_queue_(session_->createQueue(queue_name))
            , consumer_(session_->createConsumer(msg_queue_.get())) {}

    ~AMQMessageConsumer() = default;


    typename MSG_CONVERTER::msg_type GetMessage() {
        try {
            std::unique_ptr<cms::Message> cmsMsg(consumer_->receive());
            return converter_.fromCMSMessage(*cmsMsg);
        }
        catch (cms::CMSException& e) {
            std::string err_str = "CMSException caught in AMQMessenger::GetMessage: " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
        catch (std::exception& e) {
            std::string err_str = "std::exception caught in AMQInputMessenger::GetMessage: " + std::string(e.what());
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
    }


    typename MSG_CONVERTER::msg_type GetMessageNoWait() {
        try {
            std::unique_ptr<cms::Message> cmsMsg(consumer_->receiveNoWait());
            if (NULL != cmsMsg) {
                return converter_.fromCMSMessage(*cmsMsg);
            }
            else {
                return MSG_CONVERTER::msg_type;
            }
        }
        catch (cms::CMSException& e) {
            std::string err_str = "CMSException caught in AMQMessenger::GetMessageNoWait: " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
        catch (std::exception& e) {
            std::string err_str = "std::exception caught in AMQInputMessenger::GetMessageNoWait: " + std::string(e.what());
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
    }

  private:
    log4cxx::LoggerPtr logger_;
    std::unique_ptr<cms::Session> session_;
    std::unique_ptr<cms::Destination> msg_queue_;
    std::unique_ptr<cms::MessageConsumer> consumer_;
    MSG_CONVERTER converter_;
};


template <typename MSG_CONVERTER>
class AMQMessageProducer {
 public:
    AMQMessageProducer(AMQMessagingConnection &msg_connection,
                       const log4cxx::LoggerPtr &logger,
                       std::string &queue_name) 
            : logger_(logger)
            , session_(msg_connection->createSession())
            , msg_queue_(session_->createQueue(queue_name))
            , producer_(session_->createProducer(msg_queue_.get())) {}

    ~AMQMessageProducer() = default;


    void SendMessage(typename MSG_CONVERTER::msg_type &mpfMessage) {
        try {
            std::unique_ptr<cms::BytesMessage> cmsMsg(session_->createBytesMessage());
            converter_.toCMSMessage(mpfMessage, *cmsMsg);
            producer_->send(cmsMsg.get());
        }
        catch (cms::CMSException& e) {
            std::string err_str = "CMSException caught in AMQMessenger::SendMessage: " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
        catch (std::exception& e) {
            std::string err_str = "std::exception caught in AMQInputMessenger::SendMessage: " + std::string(e.what());
            MPFMessageError err = MESSENGER_PUT_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
    }

  private:
    log4cxx::LoggerPtr logger_;
    std::unique_ptr<cms::Session> session_;
    std::unique_ptr<cms::Destination> msg_queue_;
    std::unique_ptr<cms::MessageProducer> producer_;
    MSG_CONVERTER converter_;
};

typedef AMQMessenger<AMQJobStatusConverter> JobStatusMessenger;
typedef AMQMessenger<AMQActivityAlertConverter> ActivityAlertMessenger;
typedef AMQMessenger<AMQSegmentSummaryConverter> SegmentSummaryMessenger;
typedef AMQMessenger<AMQSegmentReadyConverter> SegmentReadyMessenger;
typedef AMQMessenger<AMQFrameReadyConverter> FrameReadyMessenger;
typedef AMQMessenger<AMQReleaseFrameConverter> ReleaseFrameMessenger;




} // namespace MPF

#endif // MPF_AMQ_MESSENGER_H_
