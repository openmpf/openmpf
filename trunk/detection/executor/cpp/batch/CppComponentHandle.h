/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

#ifndef MPF_CPPCOMPONENTHANDLE_H
#define MPF_CPPCOMPONENTHANDLE_H

#include <string>
#include <vector>

#include <log4cxx/logger.h>

#include <DlClassLoader.h>
#include <MPFDetectionComponent.h>
#include <MPFDetectionObjects.h>


namespace MPF { namespace COMPONENT {

    class CppComponentHandle {
    public:
        explicit CppComponentHandle(const std::string &lib_path);

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
        DlClassLoader<MPFDetectionComponent> component_;
    };


    class CppLogger {
    public:
        explicit CppLogger(const std::string &app_dir);

        void Debug(const std::string &message);

        void Info(const std::string &message);

        void Warn(const std::string &message);

        void Error(const std::string &message);

        void Fatal(const std::string &message);

    private:
        log4cxx::LoggerPtr logger_;
    };
}}


#endif //MPF_CPPCOMPONENTHANDLE_H
