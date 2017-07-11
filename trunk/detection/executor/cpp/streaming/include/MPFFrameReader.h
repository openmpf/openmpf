/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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



#ifndef OPENMPF_CPP_FRAME_READER_H
#define OPENMPF_CPP_FRAME_READER_H

#include "MPFComponentInterface.h"
#include "MPFMessageQueue.h"
#include "MPFFrameStore.h"

namespace MPF { namespace COMPONENT {

enum MPFFrameReaderError {
    MPF_FRAME_READER_SUCCESS = 0,
    MPF_FRAME_READER_NOT_INITIALIZED,
    MPF_INVALID_VIDEO_URI,
    MPF_COULD_NOT_OPEN_STREAM,
    MPF_COULD_NOT_READ_STREAM,
    MPF_BAD_FRAME_SIZE,
    MPF_INVALID_FRAME_INTERVAL,
    MPF_MISSING_PROPERTY,
    MPF_INVALID_PROPERTY,
    MPF_PROPERTY_IS_NOT_INT,
    MPF_PROPERTY_IS_NOT_FLOAT,
    MPF_MEMORY_ALLOCATION_FAILED,
    MPF_MEMORY_MAPPING_FAILED,
    MPF_FRAME_READER_FAILED,
    MPF_OTHER_FRAME_READER_ERROR
};

struct MPFFrameReaderJob : MPFJob {

    MPFFrameReaderJob(const std::string &job_name,
                      const std::string &stream_uri,
                      const MPF::COMPONENT::Properties &job_properties,
                      const MPF::COMPONENT::Properties &media_properties)
            : MPFJob(job_name, data_uri, job_properties, media_properties) {}
};

class MPFFrameReader {

  public:

    virtual ~MPFFrameReader() { }

    virtual void SetSegmentReadyQueue1(MPFMessageQueue* segment_ready_queue) {
        segment_ready_queue1_ = segment_ready_queue;
    }
    virtual MPFMessageQueue *GetSegmentReadyQueue1() {
        return segment_ready_queue1_;
    }
    virtual void SetSegmentReadyQueue2(MPFMessageQueue* segment_ready_queue) {
        segment_ready_queue2_ = segment_ready_queue;
    }
    virtual MPFMessageQueue *GetSegmentReadyQueue2() {
        return segment_ready_queue2_;
    }
    virtual void SetFrameReadyQueue(MPFMessageQueue* frame_ready_queue) {
        frame_ready_queue_ = frame_ready_queue1;
    }
    virtual MPFMessageQueue *GetFrameReadyQueue() {
        return frame_ready_queue_;
    }
    virtual void SetReleaseFrameQueue(MPFMessageQueue* release_frame_queue) {
        release_frame_queue_ = release_frame_queue;
    }
    virtual MPFMessageQueue *GetReleaseFrameQueue() {
        return release_frame_queue_;
    }
    virtual void SetJobStatusQueue(MPFMessageQueue* job_status_queue) {
        job_status_queue_ = job_status_queue;
    }
    virtual MPFMessageQueue *GetJobStatusQueue() {
        return job_status_queue_;
    }
    virtual MPFFrameReaderError AttachToStream(
                                           const MPFFrameReaderJob &job,
                                           Properties &stream_properties) = 0;
    virtual MPFFrameReaderError OpenFrameStore(MPFFrameReaderJob &job,
                                               Properties &stream_properties) = 0;

    virtual MPFFrameStore GetFrameStore() {
        return frame_buffer_;
    }
    virtual MPFFrameReaderError CloseFrameStore() = 0;
    virtual MPFFrameReaderError Run(MPFFrameReaderJob &job) = 0;

  protected:

    MPFFrameReader() = default;
    MPFMessageQueue* segment_ready_queue1_;
    MPFMessageQueue* segment_ready_queue2_;
    MPFMessageQueue* frame_ready_queue_;
    MPFMessageQueue* release_frame_queue_;
    MPFMessageQueue* job_status_queue_;
    MPFFrameStore frame_store_;
};
}}

#define MPF_FRAMEREADER_CREATOR(name) \
  extern "C" MPF::COMPONENT::MPFFrameReader* frame_reader_creator() { \
      return new (name);                                  \
  }

#define MPF_FRAMEREADER_DELETER() \
  extern "C" MPF::COMPONENT::MPFFrameReader* frame_reader_deleter(MPFFrameReader *frame_reader_P_) { \
    delete frame_reader_P_; \
  }

#endif //OPENMPF_CPP_FRAME_READER_H
