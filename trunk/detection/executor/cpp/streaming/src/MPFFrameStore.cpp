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

#include <fcntl.h>
#include <sys/mman.h>
#include <errno.h>
#include <limits.h>
#include <cstring>
#include <unistd.h>
#include <sys/types.h>
#include "MPFFrameStore.h"

using namespace MPF;
using namespace std;


int MPFFrameStore::Create(const string &frame_store_name,
                          const size_t buffer_size,
                          string &error_string) {

    if (initialized_) {
        // already done; no need to do anything more
        return 0;
    }

    /* Open the file for writing.
     *  - Create the file if it doesn't exist.
     *  - Truncate it to 0 size if it already exists. If the file is
     *  being reused, this WILL ERASE anything already in the file.
     *
     * Note: "O_WRONLY" mode is not sufficient when mmaping.
     */
#ifdef PATH_MAX
    int name_len = strlen(frame_store_name.c_str());
    if ((name_len + 1) > PATH_MAX) {
        storage_handle_ = -1;
        error_string = "Error opening frame store " + frame_store_name +
                " for writing: path name exceeds PATH_MAX = " + to_string(PATH_MAX);
        return -1;
    }

#endif

    //#TODO#: Investigate whether POSIX shared memory might be better;
    //i.e., storage_handle_ = shm_open(frame_store_name.c_str(),
    //O_RDWR | O_CREAT, (mode_t)0644);
    // Note: requires addition of call to shm_unlink() when closing
    //down. 
    storage_handle_ = open(frame_store_name.c_str(),
                            O_RDWR | O_CREAT | O_TRUNC,
                            (mode_t)0644);
    if (storage_handle_ == -1) {
        int err_num = errno;
        error_string = "Failed to open frame store " + frame_store_name +
                " for writing: " + strerror(err_num);
        return err_num;
    }

    /* Stretch the file size to the size needed */
    int result = ftruncate(storage_handle_, buffer_size);
    if (result == -1) {
        int err_num = errno;
        close(storage_handle_);
        storage_handle_ = -1;
        error_string = "Failed to set the size of the file named " +
                frame_store_name + ": " + strerror(err_num);
        return err_num;
    }
    
    int map_flags = PROT_READ | PROT_WRITE;

    /* Now the file is ready to be mmapped.
     */
    start_addr_ = (uint8_t*)mmap(NULL, buffer_size,
                                 map_flags, MAP_SHARED,
                                 storage_handle_, 0);
    if (start_addr_ == MAP_FAILED) {
        int err_num = errno;
	close(storage_handle_);
        storage_handle_ = -1;
        error_string = "Failed to map file named " + frame_store_name +
                ": " + strerror(err_num);
	return err_num;
    }
    buffer_name_ = frame_store_name;
    buffer_byte_size_ = buffer_size;
    initialized_ = true;
    return 0;
}

int MPFFrameStore::Attach(const string &frame_store_name,
                          const size_t buffer_size,
                          string &error_string) {
    if (initialized_) {
        // already done; no need to do anything more
        return 0;
    }

    /* Open the file for reading. */
    storage_handle_ = open(frame_store_name.c_str(), O_RDONLY);
    if (storage_handle_ == -1) {
        int err_num = errno;
        error_string = "Failed to open file named " + frame_store_name +
                " for reading: " + strerror(err_num);
        return err_num;
    }

    int map_flags = PROT_READ;

    /* Now the file is ready to be mmapped.
     */
    start_addr_ = (uint8_t*)mmap(NULL, buffer_size,
                                map_flags, MAP_SHARED,
                                storage_handle_, 0);
    if (start_addr_ == MAP_FAILED) {
        int err_num = errno;
	close(storage_handle_);
        storage_handle_ = -1;
        error_string = "Failed to map file named " + frame_store_name +
                ": " + strerror(err_num);
	return err_num;
    }
    buffer_name_ = frame_store_name;
    buffer_byte_size_ = buffer_size;
    initialized_ = true;
    return 0;
}

int MPFFrameStore::Close(string &error_string) {

    if (!initialized_) {
        // nothing to do
        return 0;
    }
    int err_num = 0;
    initialized_ = false;
    if (NULL != start_addr_) {
        if (munmap(start_addr_, buffer_byte_size_) == -1) {
            err_num = errno;
            error_string = "Failed to unmap the file " + buffer_name_ +
                    ": " + strerror(err_num);
        }
        start_addr_ = NULL;
    }

    // Even if the call to munmap failed, we should close the
    // storage_handle_ anyway, since further calls to this function
    // without changing the value of start_addr_ will also fail,
    // leaving the user with no way to close the storage_handle_.
    if (close(storage_handle_) == -1) {
        err_num = errno;
        error_string = error_string + "/nFailed to close the file descriptor for "
                + buffer_name_ + ": " + strerror(err_num);
    }
    storage_handle_ = -1;
    return err_num;
}


uint8_t* MPFFrameStore::GetFrameAddress(uint64_t offset,
                                        uint64_t frame_byte_size,
                                        string &error_string) {
    if (!initialized_) {
        // Accidentally called before Create/Attach
        error_string = "GetFrameAddress error: FrameStore has not been initialized";
        return NULL;
    }
    
    if ( (offset + frame_byte_size) > buffer_byte_size_ ) {
        error_string = "GetFrameAddress error: address out of range: offset "
                + to_string(offset)
                + " frame_byte_size "
                + to_string(frame_byte_size);
        return NULL;
    }
    if (storage_handle_ < 0) {
        error_string = "GetFrameAddress error: attempt to get storage address in an uninitialized or previously closed storage buffer.";
        return NULL;
    }

    return start_addr_ + offset;
}
