/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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


//TODO: All code in this file is for future use and is untested.
// Not used in single process, single pipeline stage, architecture


#ifndef OPENMPF_CPP_FRAME_READER_H
#define OPENMPF_CPP_FRAME_READER_H

#include "MPFDetectionComponent.h"
#include "MPFFrameStore.h"

namespace MPF { namespace COMPONENT {

enum MPFFrameReaderError {
    FRAME_READER_SUCCESS = 0,
    FRAME_READER_NOT_INITIALIZED,
    FRAME_READER_INVALID_VIDEO_URI,
    FRAME_READER_COULD_NOT_OPEN_STREAM,
    FRAME_READER_COULD_NOT_CLOSE_STREAM,
    FRAME_READER_COULD_NOT_READ_STREAM,
    FRAME_READER_BAD_FRAME_SIZE,
    FRAME_READER_BAD_OFFSET,
    FRAME_READER_INVALID_FRAME_INTERVAL,
    FRAME_READER_MISSING_PROPERTY,
    FRAME_READER_INVALID_PROPERTY,
    FRAME_READER_PROPERTY_IS_NOT_INT,
    FRAME_READER_PROPERTY_IS_NOT_FLOAT,
    FRAME_READER_MEMORY_ALLOCATION_FAILED,
    FRAME_READER_MEMORY_MAPPING_FAILED,
    FRAME_READER_OTHER_ERROR
};

struct MPFFrameReaderJob : MPFJob {
    MPFFrameReaderJob(const std::string &job_name,
                      const std::string &stream_uri,
                      const std::string &config_pathname,
                      const MPF::COMPONENT::Properties &job_properties,
                      const MPF::COMPONENT::Properties &media_properties)
            : MPFJob(job_name, stream_uri, job_properties, media_properties) {}
};

class MPFFrameReader {

  public:

    virtual ~MPFFrameReader() { }
    virtual MPFFrameReaderError AttachToStream(
                                           const MPFFrameReaderJob &job,
                                           Properties &stream_properties) = 0;
    virtual MPFFrameReaderError OpenFrameStore(MPFFrameReaderJob &job,
                                               Properties &stream_properties) = 0;

    virtual MPFFrameReaderError CloseFrameStore() = 0;
    virtual MPFFrameReaderError ReadAndStoreFrame(MPFFrameReaderJob &job,
                                                  const size_t offset) = 0;


  protected:

    MPFFrameReader() = default;
    MPFFrameStore frame_store_;

    int current_segment_num_;
    int current_frame_index_;
};
}}

#endif //OPENMPF_CPP_FRAME_READER_H
