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

#pragma once

#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include <cms/Destination.h>

#include <MPFDetectionComponent.h>

#include "BatchExecutorUtil.h"
#include "JobContext.h"
#include "Messenger.h"
#include "MPFMessageUtils.h"
#include "ProtobufRequestUtil.h"
#include "ProtobufResponseUtil.h"

namespace MPF::COMPONENT {

template <typename Logger>
class JobReceiver {

public:
    JobReceiver(
            Logger& logger, std::string_view broker_uri, std::string_view request_queue,
            std::string_view detection_type)
        : logger_{logger}
        , messenger_{logger, broker_uri, request_queue}
        , detection_type_{detection_type} {
    }

    JobContext GetJob() {
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

    template <typename TResp>
    void CompleteJob(const JobContext& context, const TResp& results) {
        try {
            auto response_bytes = ProtobufResponseUtil::PackResponse(context, results);
            messenger_.SendResponse(context, response_bytes);
        }
        catch (const std::exception& e) {
            logger_.Error("An error occurred while attempting to send job results: ", e.what());
            messenger_.Rollback();
        }
    }


    void ReportJobError(
            const JobContext& context, MPFDetectionError error_code,
            std::string_view explanation) {
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


    void ReportUnsupportedDataType(const JobContext& context) {
        std::string error_message
                = "The detection component does not support detection data type of ";
        switch (context.job_type) {
            case MPFDetectionDataType::VIDEO:
                error_message += "VIDEO";
                break;
            case MPFDetectionDataType::IMAGE:
                error_message += "IMAGE";
                break;
            case MPFDetectionDataType::AUDIO:
                error_message += "AUDIO";
                break;
            default:
                error_message += "GENERIC";
                break;
        }
        ReportJobError(context, MPFDetectionError::MPF_UNSUPPORTED_DATA_TYPE, error_message);
    }


private:
    Properties environment_job_properties_ = BatchExecutorUtil::get_environment_job_properties();
    Logger logger_;
    Messenger<Logger> messenger_;
    std::string detection_type_;

    JobContext TryGetJob() {
        auto request_message = messenger_.ReceiveMessage();
        std::vector<unsigned char> message_bytes(request_message->getBodyLength());
        request_message->readBytes(message_bytes);
        auto detection_request = ProtobufRequestUtil::ParseRequest(message_bytes);

        long job_id = request_message->getLongProperty("JobId");
        auto job_name = ProtobufRequestUtil::GetJobName(job_id, detection_request);
        return {
            job_id,
            job_name,
            detection_type_,
            std::unique_ptr<cms::Destination>{request_message->getCMSReplyTo()->clone()},
            request_message->getCMSPriority(),
            request_message->getStringProperty("CorrelationId"),
            request_message->getStringProperty("breadcrumbId"),
            request_message->getIntProperty("SplitSize"),
            detection_request,
            ProtobufRequestUtil::CreateComponentJob(
                    job_name, environment_job_properties_, detection_request),
            logger_.GetJobContext(job_name)
        };
    }
};
}
