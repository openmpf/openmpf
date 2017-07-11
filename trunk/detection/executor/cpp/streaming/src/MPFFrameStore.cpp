#include <fcntl.h>
#include <sys/mman.h>
#include <errno.h>
#include <limits.h>
#include "MPFFrameStore.h"

void MPFFrameStore::Create(const std::string &frame_store_name,
                           const size_t buffer_size,
                           std::string &error_string) {

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
        error_string = "Error opening frame store " + frame_store_name +
                " for writing: path name exceeds PATH_MAX = " + std::tostring(PATH_MAX);
        return;
    }

#endif

    //#TODO#: Investigate whether POSIX shared memory might be better;
    //i.e., file_descriptor_ = shm_open(frame_store_name.c_str(),
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
        return;
    }

    /* Stretch the file size to the size needed */
    int result = ftruncate(storage_handle_, buffer_size);
    if (result == -1) {
        int err_num = errno;
        close(storage_handle_);
        error_string = "Failed to set the size of the file named " +
                frame_store_name + ": " + strerror(err_num);
        return;
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
        error_string = "Failed to map file named " + frame_store_name +
                ": " + strerror(err_num);
	return;
    }
    buffer_name_ = frame_store_name;
    buffer_byte_size_ = buffer_size;
}

void MPFFrameStore::Attach(const std::string &frame_store_name,
                           const size_t buffer_size,
                           std::string &error_string) {
    /* Open the file for reading. */
    storage_handle_ = open(frame_store_name.c_str(), O_RDONLY);
    if (storage_handle_ == -1) {
        int err_num = errno;
        error_string = "Failed to open file named " + file_name +
                " for reading: " + strerror(err_num);
        return;
    }

    int map_flags = PROT_READ;

    /* Now the file is ready to be mmapped.
     */
    start_addr_ = (uint8_t*)mmap(NULL, buffer_size,
                                map_flags, MAP_SHARED,
                                storage_handle_, 0);
    if (start_addr == MAP_FAILED) {
        int err_num = errno;
	close(file_descriptor_);
        error_string = "Failed to map file named " + file_name +
                ": " + strerror(err_num);
	return;
    }
    buffer_name_ = frame_store_name;
    buffer_byte_size_ = buffer_size;
}

void Close(std::string &error_string) {
    if (NULL != start_addr_) {
        if (munmap(start_addr_, buffer_byte_size_) == -1) {
            int err_num = errno;
            error_string = "Failed to unmap the file " + buffer_name_ +
                    ": " + strerror(err_num);
        }
        start_addr_ = NULL;
    }

    close(storage_handle_);

}


uint8_t* MPFFrameStore::GetFrameAddress(uint64_t offset,
                                        uint64_t frame_byte_size,
                                        std::string &error_string) {
    if ( (offset + frame_byte_size) > buffer_byte_size_ ) {
        error_string = "GetFrameAddress error: address out of range: offset " + offset + " frame_byte_size " + frame_byte_size;
        return NULL;
    }
    else {
        return start_addr_ + offset;
    }
}
