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

#ifndef MPF_AMQ_MESSENGER_H_
#define MPF_AMQ_MESSENGER_H_

#include <memory>

#include <cms/Connection.h>
#include <cms/Session.h>
#include <cms/MessageConsumer.h>
#include <cms/MessageProducer.h>
#include <log4cxx/logger.h>

#include "MPFMessenger.h"
#include "MPFAMQMessage.h"

namespace MPF {

class AMQMessagingManager : MPFMessagingManager {
  public:

    AMQMessagingManager(const log4cxx::LoggerPtr &logger)
            : connected_(false), started_(false), logger_(logger) {}
    virtual ~AMQMessagingManager() = default;

    void Connect(const std::string &broker_name,
                 const MPF::COMPONENT::Properties &properties) override;
    void Start() override;
    void Stop() override;
    void Shutdown() override;

    bool isConnected() { return connected_; }
    bool isStarted() { return started_; }
    cms::Connection *getConnection() { return connection_.get(); }

  private:
    bool connected_;
    bool started_;
    std::unique_ptr<cms::Connection> connection_;
    log4cxx::LoggerPtr logger_;

};


class AMQMessenger {
 public:
    AMQMessenger(std::shared_ptr<AMQMessagingManager> &manager,
                 const log4cxx::LoggerPtr &logger)
            : initialized_(false), mesg_mgr_(manager), logger_(logger) {}

    ~AMQMessenger() = default;

    // Initialize the queue: starts the session
    void InitQueue(const std::string &queue_name,
                   const MPF::COMPONENT::Properties &queue_properties);

    // Usually, the user would call one or the other of these, not both.
    void CreateConsumer();
    void CreateProducer();

    template <typename MSGTYPE>
    std::unique_ptr<MSGTYPE> GetMessage();
    template <typename MSGTYPE>
    std::unique_ptr<MSGTYPE> GetMessage(const uint32_t timeout_msec);
    template <typename MSGTYPE>
    std::unique_ptr<MSGTYPE> TryGetMessage();
    template <typename MSGTYPE>
    void PutMessage(std::unique_ptr<MSGTYPE> msg);

    // Closes the session and any consumers or producers.
    void Close();

  private:
    bool initialized_;
    std::shared_ptr<AMQMessagingManager> mesg_mgr_;
    std::unique_ptr<cms::Session> session_;
    std::unique_ptr<cms::Destination> msg_queue_;
    std::unique_ptr<cms::MessageConsumer> consumer_;
    std::unique_ptr<cms::MessageProducer> producer_;
    log4cxx::LoggerPtr logger_;
};




template <class MSGTYPE>
std::unique_ptr<MSGTYPE> AMQMessenger::GetMessage() {

    std::unique_ptr<MSGTYPE> new_msg(new MSGTYPE);
    if (mesg_mgr_->isConnected() && mesg_mgr_->isStarted()) {
        try {
            new_msg->Receive(consumer_->receive());
        }
        catch (cms::CMSException& e) {
            std::string err_str = "CMSException caught in AMQMessenger::GetMessage: " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
        catch (std::exception& e) {
            std::string err_str = "CMSException caught in AMQInputMessenger::GetMessage" + std::string(e.what());
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
    return new_msg;
}

template <typename MSGTYPE>
std::unique_ptr<MSGTYPE> AMQMessenger::GetMessage(const uint32_t timeout_msec) {

    std::unique_ptr<MSGTYPE> new_msg;
    if (mesg_mgr_->isConnected() && mesg_mgr_->isStarted()) {
        try {
            new_msg->Receive(consumer_->receive(timeout_msec));
        }
        catch (cms::CMSException& e) {
            std::string err_str = "CMSException caught in AMQMessenger::GetMessage(timeout): " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
        catch (std::exception& e) {
            std::string err_str = "CMSException caught in AMQInputMessenger::GetMessage(timeout)" + std::string(e.what());
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
    }
    else {
        MPFMessageError err = MESSENGER_NOT_CONNECTED;
        std::string err_str = "AMQMessenger::GetMessage(timeout): Messenger not connected or not started";
        MPFMessageException exc(err_str.c_str(), err);
        throw(exc);
    }
    return new_msg;
}

template <typename MSGTYPE>
std::unique_ptr<MSGTYPE> AMQMessenger::TryGetMessage() {
    std::unique_ptr<MSGTYPE> new_msg;
    if (mesg_mgr_->isConnected() && mesg_mgr_->isStarted()) {
        try {
            new_msg->Receive(consumer_->receiveNoWait());
        }
        catch (cms::CMSException& e) {
            std::string err_str = "CMSException caught in AMQMessenger::TryGetMessage: " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
        catch (std::exception& e) {
            std::string err_str = "CMSException caught in AMQInputMessenger::TryGetMessage" + std::string(e.what());
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
    }
    else {
        MPFMessageError err = MESSENGER_NOT_CONNECTED;
        std::string err_str = "AMQMessenger::TryGetMessage: Messenger not connected or not started";
        MPFMessageException exc(err_str.c_str(), err);
        throw(exc);
    }
    return new_msg;
}

template <typename MSGTYPE>
void AMQMessenger::PutMessage(std::unique_ptr<MSGTYPE> send_msg) {

    if (mesg_mgr_->isConnected() && mesg_mgr_->isStarted()) {
        try {
            send_msg->InitMessage(session_.get());
            producer_->send(send_msg->GetMessagePtr());
        }
        catch (cms::CMSException& e) {
            std::string err_str = "CMSException caught in AMQMessenger::PutMessage(): " + e.getMessage() + "\n" + e.getStackTraceString();
            MPFMessageError err = MESSENGER_GET_MESSAGE_FAILURE;
            MPFMessageException exc(err_str.c_str(), err);
            throw(exc);
        }
        catch (std::exception& e) {
            std::string err_str = "CMSException caught in AMQInputMessenger::PutMessage()" + std::string(e.what());
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
    return;
}


} // namespace MPF

#endif // MPF_AMQ_MESSENGER_H_
