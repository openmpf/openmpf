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

#include <log4cxx/mdc.h>
#include <log4cxx/xml/domconfigurator.h>

#include "ComponentLoadError.h"

#include "CppComponentHandle.h"


namespace MPF::COMPONENT {

    CppComponentHandle::CppComponentHandle(const std::string &lib_path)
    try : component_(lib_path, "component_creator", "component_deleter")
    {

    }
    catch (const std::exception &ex) {
        throw ComponentLoadError(ex.what());
    }

    void CppComponentHandle::SetRunDirectory(const std::string &run_dir) {
        component_->SetRunDirectory(run_dir);
    }

    bool CppComponentHandle::Init() {
        return component_->Init();
    }

    bool CppComponentHandle::Supports(MPFDetectionDataType data_type) {
        return component_->Supports(data_type);
    }

    std::vector<MPFVideoTrack> CppComponentHandle::GetDetections(const MPFVideoJob &job) {
        return component_->GetDetections(job);
    }

    std::vector<MPFVideoTrack> CppComponentHandle::GetDetections(const MPFAllVideoTracksJob &job) {
        return component_->GetDetections(job);
    }

    std::vector<MPFImageLocation> CppComponentHandle::GetDetections(const MPFImageJob &job) {
        return component_->GetDetections(job);
    }

    std::vector<MPFAudioTrack> CppComponentHandle::GetDetections(const MPFAudioJob &job) {
        return component_->GetDetections(job);
    }

    std::vector<MPFGenericTrack> CppComponentHandle::GetDetections(const MPFGenericJob &job) {
        return component_->GetDetections(job);
    }

    bool CppComponentHandle::Close() {
        return component_->Close();
    }



    CppLogger::CppLogger(std::string_view app_dir) {
        log4cxx::xml::DOMConfigurator::configure(
            std::string{app_dir} + "/../config/Log4cxxConfig.xml");
        logger_ = log4cxx::Logger::getLogger("org.mitre.mpf.detection");
    }

    void CppLogger::Debug(std::string_view message) {
        LOG4CXX_DEBUG(logger_, message);
    }

    void CppLogger::Info(std::string_view message) {
        LOG4CXX_INFO(logger_, message);
    }

    void CppLogger::Warn(std::string_view message) {
        LOG4CXX_WARN(logger_, message);
    }

    void CppLogger::Error(std::string_view message) {
        LOG4CXX_ERROR(logger_, message);
    }

    void CppLogger::Fatal(std::string_view message) {
        LOG4CXX_FATAL(logger_, message);
    }

    void CppLogger::SetJobName(std::string_view job_name) {
        log4cxx::MDC::remove("job");
        log4cxx::MDC::put("job", std::string{job_name});
    }
}
