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

#include <utility>
#include <variant>
#include <vector>

#include "ProtobufRequestUtil.h"

#include "JobReceiver.h"

namespace MPF::COMPONENT {

namespace {
    using job_type_info_t = std::pair<MPFDetectionDataType, const char*>;

    template <typename>
    constexpr job_type_info_t job_type_info{MPFDetectionDataType::UNKNOWN, "GENERIC"};

    template <>
    constexpr job_type_info_t job_type_info<MPFVideoJob>{MPFDetectionDataType::VIDEO, "VIDEO"};

    template <>
    constexpr job_type_info_t job_type_info<MPFMultiTrackVideoJob>{MPFDetectionDataType::VIDEO, "VIDEO"};

    template <>
    constexpr job_type_info_t job_type_info<MPFImageJob>{MPFDetectionDataType::IMAGE, "IMAGE"};

    template <>
    constexpr job_type_info_t job_type_info<MPFAudioJob>{MPFDetectionDataType::AUDIO, "AUDIO"};
} // End anonymous namespace


JobReceiver::JobReceiver(
        LoggerWrapper logger,
        std::string_view broker_uri,
        std::string_view request_queue)
    : logger_{std::move(logger)}
    , messenger_{logger_, broker_uri, request_queue} {
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
    auto [job_type, type_name] = std::visit([](const auto& job) {
        return job_type_info<std::decay_t<decltype(job)>>;
    }, component_job);
    return {
        job_id,
        job_name,
        std::move(component_job),
        job_type,
        type_name,
        {},
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
    if(context.job_type == MPFDetectionDataType::UNKNOWN) {
        ReportJobError(
            context, MPFDetectionError::MPF_UNSUPPORTED_DATA_TYPE,
            "The detection component does not support detection data type of '"
                + context.job_type_name + "'. The MIME_TYPE was detected to be '"
                + context.get_mime_type() + "', which was classified as '"
                + context.job_type_name + "' because it isn't an image, video, or audio file. "
                + "The component does not support any '"
                + context.job_type_name + "' media.");
    } else {
        ReportJobError(
            context, MPFDetectionError::MPF_UNSUPPORTED_DATA_TYPE,
            "The detection component does not support detection data type of '"
                + context.get_mime_type() + "'.");
    }
}

void JobReceiver::RejectJob() {
    messenger_.Rollback();
}

}
