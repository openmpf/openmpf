/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

#include "MPFMessenger.h"
#include "MPFAMQMessage.h"

// TODO: For future use.
// TODO: Refactor to use the Resource Acquisition Is Initialization (RAII) design pattern. See http://en.cppreference.com/w/cpp/language/raii
namespace MPF {

class AMQMessagingManager : MPFMessagingManager {
  public:

    explicit AMQMessagingManager(const log4cxx::LoggerPtr &logger)
            : connected_(false), started_(false), logger_(logger) {}
    virtual ~AMQMessagingManager() = default;

    void Connect(const std::string &broker_name,
                 const MPF::COMPONENT::Properties &properties) override;
    void Start() override;
    void Stop() override;
    void Shutdown() override;

    bool IsConnected() { return connected_; }
    bool IsStarted() { return started_; }
    cms::Connection *GetConnection() { return connection_.get(); }

  private:
    bool connected_;
    bool started_;
    std::unique_ptr<cms::Connection> connection_;
    log4cxx::LoggerPtr logger_;

};

template <typename MSG_CONVERTER>
class AMQMessenger {
 public:
    AMQMessenger(std::shared_ptr<AMQMessagingManager> &manager,
                 const log4cxx::LoggerPtr &logger)
            : initialized_(false), mesg_mgr_(manager), logger_(logger) {}

    ~AMQMessenger() = default;

    // Initialize the queue
    void InitQueue(const std::string &queue_name,
                   const MPF::COMPONENT::Properties &queue_properties) {

        if (mesg_mgr_->IsConnected()) {
            try {
                session_.reset(mesg_mgr_->GetConnection()->createSession());
                msg_queue_.reset(session_->createQueue(queue_name));
                initialized_ = true;
            } catch (cms::CMSException& e) {
                std::string msg = "CMSException caught in AMQMessenger::InitQueue: " + e.getMessage() + "\n" + e.getStackTraceString();
                MPFMessageError err = MESSENGER_INIT_QUEUE_FAILURE;
                MPFMessageException exc(msg.c_str(), err);
                throw(exc);
            }
        }
        else {
            MPFMessageError err = MESSENGER_NOT_CONNECTED;
            std::string msg = "AMQMessenger::InitQueue: MessageManager not connected";
            MPFMessageException exc(msg.c_str(), err);
            throw(exc);
        }
    }


    // Usually, the user would call CreateConsumer() or CreateProducer(), not both.
    void CreateConsumer() {
        if (mesg_mgr_->IsConnected()) {
            if (initialized_) {
                try {
                    consumer_.reset(session_->createConsumer(msg_queue_.get()));
                } catch (cms::CMSException& e) {
                    std::string err_msg = "CMSException caught in AMQMessenger::CreateConsumer: " + e.getMessage() + "\n" + e.getStackTraceString();
                    MPFMessageError err = MESSENGER_CREATE_CONSUMER_FAILURE;
                    MPFMessageException exc(err_msg.c_str(), err);
                    throw(exc);
                }
            }
            else {
                MPFMessageError err = MESSENGER_QUEUE_NOT_INITIALIZED;
                std::string err_msg = "AMQMessenger::CreateConsumer: Queue not initialized";
                MPFMessageException exc(err_msg.c_str(), err);
                throw(exc);
            }
        }
        else {
            MPFMessageError err = MESSENGER_NOT_CONNECTED;
            std::string err_msg = "AMQMessenger::CreateConsumer: Message Manager not connected";
            MPFMessageException exc(err_msg.c_str(), err);
            throw(exc);
        }
    }

    void CreateProducer() {
        if (mesg_mgr_->IsConnected()) {
            if (initialized_) {
                try {
                    producer_.reset(session_->createProducer(msg_queue_.get()));
                } catch (cms::CMSException& e) {
                    std::string err_msg = "CMSException caught in AMQMessenger::CreateProducer: " + e.getMessage() + "\n" + e.getStackTraceString();
                    MPFMessageError err = MESSENGER_CREATE_PRODUCER_FAILURE;
                    MPFMessageException exc(err_msg.c_str(), err);
                    throw(exc);
                }
            }
            else {
                MPFMessageError err = MESSENGER_QUEUE_NOT_INITIALIZED;
                std::string msg = "AMQMessenger::CreateProducer: Queue not initialized";
                MPFMessageException exc(msg.c_str(), err);
                throw(exc);
            }
        }
        else {
            MPFMessageError err = MESSENGER_NOT_CONNECTED;
            std::string msg = "AMQMessenger::CreateConsumer: Message Manager not connected";
            MPFMessageException exc(msg.c_str(), err);
            throw(exc);
        }
    }


    typename MSG_CONVERTER::msg_type GetMessage() {
        if (mesg_mgr_->IsConnected() && mesg_mgr_->IsStarted()) {
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
        else {
            MPFMessageError err = MESSENGER_NOT_CONNECTED;
            std::string err_str = "AMQMessenger::GetMessage: Messenger not connected or not started";
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
    }


    typename MSG_CONVERTER::msg_type GetMessageNoWait() {
        if (mesg_mgr_->IsConnected() && mesg_mgr_->IsStarted()) {
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
        else {
            MPFMessageError err = MESSENGER_NOT_CONNECTED;
            std::string err_str = "AMQMessenger::GetMessage: Messenger not connected or not started";
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
    }


    void SendMessage(typename MSG_CONVERTER::msg_type &mpfMessage) {
        if (mesg_mgr_->IsConnected() && mesg_mgr_->IsStarted()) {
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
        else {
            MPFMessageError err = MESSENGER_NOT_CONNECTED;
            std::string err_str = "AMQMessenger::PutMessage(): Messenger not connected or not started";
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }

    }

    // Closes the session and any consumers or producers.
    void Close() {

        if ((initialized_) && (NULL != session_)) {
            try {
                consumer_.release();
                producer_.release();
                msg_queue_.release();
                session_->close();
                session_.release();
            } catch (cms::CMSException& e) {
                std::string err_msg = "CMSException caught in AMQMessenger::Close: " + e.getMessage() + "\n" + e.getStackTraceString();
                MPFMessageError err = MESSENGER_CLOSE_FAILURE;
                MPFMessageException exc(err_msg.c_str(), err);
                throw(exc);
            }
        }   
    }

    bool IsInitialized() { return initialized_; }
    

  private:
    bool initialized_;
    std::shared_ptr<AMQMessagingManager> mesg_mgr_;
    std::unique_ptr<cms::Session> session_;
    std::unique_ptr<cms::Destination> msg_queue_;
    std::unique_ptr<cms::MessageConsumer> consumer_;
    std::unique_ptr<cms::MessageProducer> producer_;
    MSG_CONVERTER converter_;
    log4cxx::LoggerPtr logger_;
};

typedef AMQMessenger<AMQJobStatusConverter> JobStatusMessenger;
typedef AMQMessenger<AMQActivityAlertConverter> ActivityAlertMessenger;
typedef AMQMessenger<AMQSegmentSummaryConverter> SegmentSummaryMessenger;




} // namespace MPF

#endif // MPF_AMQ_MESSENGER_H_
