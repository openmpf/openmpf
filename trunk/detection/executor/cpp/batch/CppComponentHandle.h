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

# pragma once

#include <string>
#include <string_view>
#include <vector>

#include <log4cxx/logger.h>

#include <DlClassLoader.h>
#include <MPFDetectionComponent.h>
#include <MPFDetectionObjects.h>

#include "LoggerWrapper.h"


namespace MPF::COMPONENT {

    class CppComponentHandle {
    public:
        explicit CppComponentHandle(const std::string &lib_path);

        void SetRunDirectory(const std::string &run_dir);

        bool Init();

        bool Supports(MPFDetectionDataType data_type);

        std::vector<MPFVideoTrack> GetDetections(const MPFVideoJob &job);

        std::vector<MPFVideoTrack> GetDetections(const MPFAllVideoTracksJob &job);

        std::vector<MPFImageLocation> GetDetections(const MPFImageJob &job);

        std::vector<MPFAudioTrack> GetDetections(const MPFAudioJob &job);

        std::vector<MPFGenericTrack> GetDetections(const MPFGenericJob &job);

        bool Close();

    private:
        DlClassLoader<MPFDetectionComponent> component_;
    };


    class CppLogger : public ILogger {
    public:
        explicit CppLogger(std::string_view app_dir);

        void Debug(std::string_view message) override;

        void Info(std::string_view message) override;

        void Warn(std::string_view message) override;

        void Error(std::string_view message) override;

        void Fatal(std::string_view message) override;

        void SetJobName(std::string_view job_name) override;

    private:
        log4cxx::LoggerPtr logger_;
    };
}
