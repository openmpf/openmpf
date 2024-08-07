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

#pragma once

#include <memory>
#include <optional>
#include <string>

#include <cms/Destination.h>

#include "LoggerWrapper.h"
#include "subject.pb.h"

namespace MPF::SUBJECT {

namespace mpf_buffers = org::mitre::mpf::wfm::buffers;


struct AmqMetadata {
    std::unique_ptr<cms::Destination> response_queue;

    std::string correlation_id;

    int cms_priority;

    std::optional<std::string> bread_crumb_id;

    std::optional<long> job_id;
};


struct JobContext {
    mpf_buffers::SubjectTrackingJob job;

    LoggerContext log_context;

    AmqMetadata amq_metadata;
};


} // namespace MPF::SUBJECT
