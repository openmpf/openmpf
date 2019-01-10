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


#ifndef CPP_TEST_COMPONENTS_HELLOWORLD_H
#define CPP_TEST_COMPONENTS_HELLOWORLD_H

#include <string>
#include <vector>

#include <log4cxx/logger.h>

#include <MPFDetectionComponent.h>


class HelloWorld : public MPF::COMPONENT::MPFDetectionComponent {

public:

    bool Init() override;

    bool Close() override;

    MPF::COMPONENT::MPFDetectionError GetDetections(
            const MPF::COMPONENT::MPFVideoJob &job,
            std::vector<MPF::COMPONENT::MPFVideoTrack> &tracks) override;

    MPF::COMPONENT::MPFDetectionError GetDetections(
            const MPF::COMPONENT::MPFImageJob &job,
            std::vector<MPF::COMPONENT::MPFImageLocation> &locations) override;

    MPF::COMPONENT::MPFDetectionError GetDetections(
            const MPF::COMPONENT::MPFAudioJob &job,
            std::vector<MPF::COMPONENT::MPFAudioTrack> &tracks) override;

    MPF::COMPONENT::MPFDetectionError GetDetections(
            const MPF::COMPONENT::MPFGenericJob &job,
            std::vector<MPF::COMPONENT::MPFGenericTrack> &tracks) override;

    bool Supports(MPF::COMPONENT::MPFDetectionDataType data_type) override;

    std::string GetDetectionType() override;

private:
    log4cxx::LoggerPtr hw_logger_;
};


#endif //CPP_TEST_COMPONENTS_HELLOWORLD_H
