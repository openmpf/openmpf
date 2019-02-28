/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

//TODO: The MPFFrameStore class is for future use and is untested.
// Not used in single process, single pipeline stage, architecture


#ifndef MPF_FRAME_STORE_H_
#define MPF_FRAME_STORE_H_

#include <string>

namespace MPF {

class MPFFrameStore {

 public:

    MPFFrameStore() = default;
    ~MPFFrameStore() { std::string err; Close(err); };

    // Creates and attaches to the storage for frame data.
    // This method must only be used by a single process of the group
    // that will share the frame storage. This is typically the
    // frame producer. Its return value is 0 if the method is
    // successful. If an error occurs, it returns a non-zero value and
    // the error_string parameter will contain an explanation of the
    // error.
    int Create(const std::string &frame_store_name,
               const size_t buffer_size,
               std::string &error_string);
    // Attaches to the storage for frame data. All other processes
    // that will share the frame storage must use this method. This is
    // typically the consumers of the frame data. Its return value is
    // 0 if the method is successful. If an error occurs, it returns a
    // non-zero value and the error_string parameter will contain an
    // explanation of the error.
    int Attach(const std::string &frame_store_name,
               const size_t buffer_size,
               std::string &error_string);
    int Close(std::string &error_string);

    // The frame byte size is supplied along with the offset so that
    // the FrameStore can check that the address computed plus the
    // size does not overflow the buffer. If it does, the returned
    // address will be null, and the error_string will be set.
    uint8_t* GetFrameAddress(size_t offset,
                             size_t frame_byte_size,
                             std::string &error_string);

  private:
    bool initialized_ = false;
    std::string buffer_name_;
    int storage_handle_;
    uint8_t* start_addr_;
    size_t buffer_byte_size_;
};

} // namespace MPF

#endif // MPF_FRAME_STORE_H_
