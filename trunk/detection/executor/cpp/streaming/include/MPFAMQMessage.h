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

#ifndef MPF_AMQ_MESSAGE_H_
#define MPF_AMQ_MESSAGE_H_

#include <memory>

#include <cms/TextMessage.h>

#include "MPFMessage.h"


namespace MPF {

struct AMQSegmentReadyMessage : MPFSegmentReadyMessage, cms::Message {

    AMQSegmentReadyMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint32_t seg_num)
            : MPFSegmentReadyMessage(job_name, job_number, seg_num) {}
};

struct AMQFrameReadyMessage : MPFFrameReadyMessage, cms::Message {

    AMQFrameReadyMessage(const std::string &job_name,
                         const uint32_t job_number,
                         const uint32_t seg_num,
                         const uint32_t index,
                         const uint64_t offset)
            : MPFFrameReadyMessage(job_name, job_number, seg_num, index, offset) {}
};  

struct AMQReleaseFrameMessage : MPFReleaseFrameMessage, cms::Message {

    AMQReleaseFrameMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint64_t offset)
            : MPFReleaseFrameMessage(job_name, job_number, offset) {}
};

struct AMQJobStatusMessage : MPFJobStatusMessage, cms::Message {

    AMQJobStatusMessage(const std::string &job_name,
                        const uint32_t job_number,
                        const std::string &msg)
            : MPFJobStatusMessage(job_name, job_number, msg) {}
};

struct AMQSegmentSummaryMessage : MPFSegmentSummaryMessage, cms::BytesMessage {

    AMQSegmentSummaryMessage(const std::string &job_name,
                             const uint32_t job_number,
                             int seg_num,
                             const std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks)
            : MPFSegmentSummaryMessage(job_name, job_number, seg_num, tracks) {}
};

struct AMQVideoWrittenMessage : MPFVideoWrittenMessage, cms::Message {

    AMQVideoWrittenMessage(const std::string &job_name,
                           const uint32_t job_number,
                           const uint32_t seg_num,
                           const std::string &path)
            : MPFVideoWrittenMessage(job_name, job_number, seg_num, path) {}
};

} // namespace MPF

#endif // MPF_AMQ_MESSAGE_H_
