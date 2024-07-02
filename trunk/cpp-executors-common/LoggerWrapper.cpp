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

#include "LoggerWrapper.h"


LoggerContext::LoggerContext(
        std::string_view new_ctx_msg,
        std::shared_ptr<std::string> logger_ctx_ref,
        std::shared_ptr<ILogger> logger_impl)
    : logger_ctx_ref_{std::move(logger_ctx_ref)}
    , previous_ctx_msg_{std::move(*logger_ctx_ref_)}
    , logger_impl_{std::move(logger_impl)}
{
    *logger_ctx_ref_ = new_ctx_msg;
    logger_impl_->SetContextMessage(new_ctx_msg);
}


LoggerContext::~LoggerContext() {
    // logger_ctx_ref_ will be null when this instance was passed to a move constructor.
    if (logger_ctx_ref_ != nullptr) {
        *logger_ctx_ref_ = std::move(previous_ctx_msg_);
        logger_impl_->SetContextMessage(*logger_ctx_ref_);
    }
}
