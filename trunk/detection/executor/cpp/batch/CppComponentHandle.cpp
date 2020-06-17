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

#include "CppComponentHandle.h"
#include "ComponentLoadError.h"


namespace MPF { namespace COMPONENT {


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

    std::string CppComponentHandle::GetDetectionType() {
        return component_->GetDetectionType();
    }

    bool CppComponentHandle::Supports(MPFDetectionDataType data_type) {
        return component_->Supports(data_type);
    }

    std::vector<MPFVideoTrack> CppComponentHandle::GetDetections(const MPFVideoJob &job) {
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


}}