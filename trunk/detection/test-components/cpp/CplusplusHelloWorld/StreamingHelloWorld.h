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


#ifndef MPF_STREAMINGHELLOWORLD_H
#define MPF_STREAMINGHELLOWORLD_H

#include <vector>
#include <string>

#include <opencv2/core.hpp>
#include <log4cxx/logger.h>

#include <MPFComponentInterface.h>
#include <MPFStreamingDetectionComponent.h>


class StreamingHelloWorld : public MPF::COMPONENT::MPFStreamingDetectionComponent {

public:
    explicit StreamingHelloWorld(const MPF::COMPONENT::MPFStreamingVideoJob &job);

    ~StreamingHelloWorld() override;

    std::string GetDetectionType() override;

    void BeginSegment(const MPF::COMPONENT::VideoSegmentInfo &segment_info) override;

    bool ProcessFrame(const cv::Mat &frame, int frame_number) override;

    std::vector<MPF::COMPONENT::MPFVideoTrack> EndSegment() override;

private:
    log4cxx::LoggerPtr hw_logger_;

    std::string job_name_;

    double confidence_threshold_;

    std::vector<MPF::COMPONENT::MPFVideoTrack> segment_detections_;

    static log4cxx::LoggerPtr GetLogger(const std::string &run_directory);
};


#endif //MPF_STREAMINGHELLOWORLD_H
