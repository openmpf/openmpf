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

#ifndef MPF_PYTHONCOMPONENTHANDLE_H
#define MPF_PYTHONCOMPONENTHANDLE_H

#include <string>
#include <vector>
#include <memory>

#include <MPFDetectionObjects.h>
#include <MPFDetectionComponent.h>

#include "LazyLoggerWrapper.h"


namespace MPF::COMPONENT {

    class PythonLogger;

    class PythonComponentHandle {
    public:
        PythonComponentHandle(const LazyLoggerWrapper<PythonLogger> &logger,
                              const std::string &lib_path);

        ~PythonComponentHandle();

        void SetRunDirectory(const std::string &run_dir);

        bool Init();

        std::string GetDetectionType();

        bool Supports(MPFDetectionDataType data_type);

        std::vector<MPFVideoTrack> GetDetections(const MPFVideoJob &job);

        std::vector<MPFImageLocation> GetDetections(const MPFImageJob &job);

        std::vector<MPFAudioTrack> GetDetections(const MPFAudioJob &job);

        std::vector<MPFGenericTrack> GetDetections(const MPFGenericJob &job);

        bool Close();

    private:
        // The pybind11 library declares everything with hidden visibility, so the pybind11 types cannot be
        // referenced in a header file. To avoid referencing any pybind11 types the
        // "Pointer to implementation" or "pImpl" pattern is used here. This also has the benefit that including
        // this header, won't cause all of the Python headers to be included.
        class impl;
        std::unique_ptr<impl> impl_;
    };


    class PythonLoggerJobContext {
    public:
        explicit PythonLoggerJobContext(std::shared_ptr<std::string> job_name_log_prefix_ptr);

        ~PythonLoggerJobContext();

    private:
        std::shared_ptr<std::string> job_name_log_prefix_ptr_;
    };


    class PythonLogger {
    public:
        PythonLogger(const std::string &log_level, const std::string &component_name);

        PythonLogger(const PythonLogger& other);

        ~PythonLogger();

        void Debug(const std::string &message);

        void Info(const std::string &message);

        void Warn(const std::string &message);

        void Error(const std::string &message);

        void Fatal(const std::string &message);

        std::shared_ptr<void> GetJobContext(const std::string& job_name);

    private:
        std::shared_ptr<std::string> job_name_log_prefix_ptr_ = std::make_shared<std::string>();

        class logger_impl;
        std::unique_ptr<logger_impl> impl_;

        static void ConfigureLogging(const std::string &log_level,
                                     const std::string &component_name,
                                     std::shared_ptr<std::string> job_name_log_prefix_ptr);

        static std::string GetLogFilePath(const std::string &component_name);
    };
}


#endif //MPF_PYTHONCOMPONENTHANDLE_H
