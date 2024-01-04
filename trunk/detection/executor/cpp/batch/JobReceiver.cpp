/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

#include <utility>
#include <variant>
#include <vector>

#include "ProtobufRequestUtil.h"

#include "JobReceiver.h"

namespace MPF::COMPONENT {

namespace {
    MPFDetectionDataType GetJobType(const job_variant_t& job) {
        if (std::holds_alternative<MPFVideoJob>(job)) {
            return MPFDetectionDataType::VIDEO;
        }
        else if (std::holds_alternative<MPFImageJob>(job)) {
            return MPFDetectionDataType::IMAGE;
        }
        else if (std::holds_alternative<MPFAudioJob>(job)) {
            return MPFDetectionDataType::AUDIO;
        }
        else {
            return MPFDetectionDataType::UNKNOWN;
        }
    }

    std::string JobTypeToString(MPFDetectionDataType job_type) {
        switch (job_type) {
            case MPFDetectionDataType::VIDEO:
                return "VIDEO";
            case MPFDetectionDataType::IMAGE:
                return "IMAGE";
            case MPFDetectionDataType::AUDIO:
                return "AUDIO";
            default:
                return "GENERIC";
        }
    }
} // End anonymous namespace


JobReceiver::JobReceiver(
        LoggerWrapper logger,
        std::string_view broker_uri,
        std::string_view request_queue,
        std::string_view detection_type)
    : logger_{std::move(logger)}
    , messenger_{logger_, broker_uri, request_queue}
    , detection_type_{detection_type} {
}

JobContext JobReceiver::GetJob() {
    while (true) {
        try {
            return TryGetJob();
        }
        catch (const std::exception& e) {
            logger_.Error(
                    "An error occurred while trying to get job from ActiveMQ: ", e.what());
            messenger_.Rollback();
        }
    }
}


JobContext JobReceiver::TryGetJob() {
    auto request_message = messenger_.ReceiveMessage();
    std::vector<unsigned char> message_bytes(request_message->getBodyLength());
    request_message->readBytes(message_bytes);
    auto detection_request = ProtobufRequestUtil::ParseRequest(message_bytes);

    long job_id = request_message->getLongProperty("JobId");
    auto job_name = ProtobufRequestUtil::GetJobName(job_id, detection_request);
    auto component_job = ProtobufRequestUtil::CreateComponentJob(
            job_name, environment_job_properties_, detection_request);
    auto job_type = GetJobType(component_job);
    return {
        job_id,
        job_name,
        detection_type_,
        std::move(component_job),
        job_type,
        JobTypeToString(job_type),
        logger_.GetJobContext(job_name),
        Messenger::GetAmqMetadata(*request_message),
        ProtobufRequestUtil::GetMetadata(detection_request)
    };
}


void JobReceiver::ReportJobError(
        const JobContext& context, MPFDetectionError error_code, std::string_view explanation) {
    try {
        logger_.Error("An error occurred while running the job: ", explanation);
        messenger_.SendResponse(
            context,
            ProtobufResponseUtil::PackErrorResponse(context, error_code, explanation));
    }
    catch (const std::exception& e) {
        logger_.Error("An error occurred while attempting to report the job error: ", e.what());
        // We could not report that the job failed, so we must rollback so that the job request
        // message is not lost.
        messenger_.Rollback();
    }
}

void JobReceiver::ReportUnsupportedDataType(const JobContext& context) {
    ReportJobError(
        context, MPFDetectionError::MPF_UNSUPPORTED_DATA_TYPE,
        "The detection component does not support detection data type of "
            + context.job_type_name);
}

void JobReceiver::RejectJob() {
    messenger_.Rollback();
}

}
