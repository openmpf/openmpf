/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <cms/BytesMessage.h>
#include <cms/CMSException.h>
#include <cms/Destination.h>
#include <cms/Message.h>

#include "QuitReceived.h"

#include "JobReceiver.h"

namespace MPF::SUBJECT {


namespace {
    using namespace std::string_literals;

    const auto BREAD_CRUMB_ID = "breadcrumbId"s;

    const auto JOB_ID = "JobId"s;

    template <typename TFunc>
    auto GetOptional(TFunc&& func) -> std::optional<decltype(func())> {
        try {
            return std::forward<TFunc>(func)();
        }
        catch (const cms::CMSException&) {
            return {};
        }
    }

    AmqMetadata GetAmqMetadata(const cms::Message& message) {
        return {
            std::unique_ptr<cms::Destination>{message.getCMSReplyTo()->clone()},
            message.getCMSCorrelationID(),
            message.getCMSPriority(),
            GetOptional([&message]{ return message.getStringProperty(BREAD_CRUMB_ID); }),
            GetOptional([&message]{ return message.getLongProperty(JOB_ID); })
        };
    }

} // namespace

JobReceiver::JobReceiver(LoggerWrapper logger, BasicMessenger messenger)
    : logger_{std::move(logger)}
    , messenger_{std::move(messenger)} {
}


JobContext JobReceiver::GetJob() {
    while (true) {
        try {
            return TryGetJob();
        }
        catch (const QuitReceived&) {
            throw;
        }
        catch (const std::exception& e) {
            logger_.Error(
                    "An error occurred while trying to get job from ActiveMQ: ", e.what());
            messenger_.Rollback();
        }
    }
}


JobContext JobReceiver::TryGetJob() {
    auto request_message = messenger_.ReceiveMessage<cms::BytesMessage>();
    std::vector<unsigned char> message_bytes(request_message->getBodyLength());
    request_message->readBytes(message_bytes);
    mpf_buffers::SubjectTrackingJob job;
    job.ParseFromArray(message_bytes.data(), static_cast<int>(message_bytes.size()));
    std::string job_log_label = '[' + job.job_name() + "] ";
    return {
        std::move(job),
        logger_.GetLoggerContext(std::move(job_log_label)),
        GetAmqMetadata(*request_message)
    };
}


void JobReceiver::CompleteJob(
        const JobContext& job_context,
        const mpf_buffers::SubjectTrackingResult& job_result) {

    auto message = messenger_.NewMessage<cms::BytesMessage>();
    const auto& amq_metadata = job_context.amq_metadata;
    message->setCMSCorrelationID(amq_metadata.correlation_id);
    if (amq_metadata.bread_crumb_id) {
        message->setStringProperty(BREAD_CRUMB_ID, *amq_metadata.bread_crumb_id);
    }
    if (amq_metadata.job_id) {
        message->setLongProperty(JOB_ID, *amq_metadata.job_id);
    }

    std::vector<unsigned char> proto_bytes(job_result.ByteSize());
    job_result.SerializeWithCachedSizesToArray(proto_bytes.data());
    message->writeBytes(proto_bytes);

    messenger_.SendResponse(*amq_metadata.response_queue, *message, amq_metadata.cms_priority);
}

} // namespace MPF::SUBJECT
