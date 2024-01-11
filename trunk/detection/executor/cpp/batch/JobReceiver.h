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

#include <exception>
#include <string>
#include <string_view>

#include <MPFDetectionObjects.h>

#include "BatchExecutorUtil.h"
#include "JobContext.h"
#include "Messenger.h"
#include "ProtobufResponseUtil.h"
#include "LoggerWrapper.h"


namespace MPF::COMPONENT {

class JobReceiver {

public:
    JobReceiver(
            LoggerWrapper logger, std::string_view broker_uri, std::string_view request_queue);

    JobContext GetJob();

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
            std::string_view explanation);

    void ReportUnsupportedDataType(const JobContext& context);

    void RejectJob();

private:
    Properties environment_job_properties_ = BatchExecutorUtil::GetEnvironmentJobProperties();

    LoggerWrapper logger_;

    Messenger messenger_;

    JobContext TryGetJob();
};
}
