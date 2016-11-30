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

#include "utils.h"
#include "MPFInvalidPropertyException.h"
#include "detectionComponentUtils.h"

using std::string;
using std::map;
using std::pair;
using std::exception;
using std::invalid_argument;

using namespace MPF;
using namespace COMPONENT;


namespace Utils {

cv::Mat ConvertToGray(const cv::Mat &image) {
    cv::Mat gray;
    if (image.channels() == 3) {
        cv::cvtColor(image, gray, CV_BGR2GRAY);
    }
    else if (image.channels() == 1) {
        gray = image.clone();
    }
    else {
        printf("--(!)Error reading frames \n");
    }

    return gray;
}



namespace {
MPFDetectionError HandleDetectionExceptionInner(const string &job_name, MPFDetectionDataType  dataType,
                                                log4cxx::LoggerPtr logger)  {

    const auto &errorInfo = DetectionComponentUtils::HandleDetectionException(dataType);
    LOG4CXX_ERROR(logger, "[" << job_name << "] " << errorInfo.second);
    return errorInfo.first;
}

}


MPFDetectionError HandleDetectionException(const MPFVideoJob &job, log4cxx::LoggerPtr logger) {
    return HandleDetectionExceptionInner(job.job_name, MPFDetectionDataType::VIDEO, logger);
}

MPFDetectionError HandleDetectionException(const MPFImageJob &job, log4cxx::LoggerPtr logger) {
    return HandleDetectionExceptionInner(job.job_name, MPFDetectionDataType::IMAGE,  logger);
}

MPFDetectionError HandleDetectionException(const MPFAudioJob &job, log4cxx::LoggerPtr logger) {
    return HandleDetectionExceptionInner(job.job_name, MPFDetectionDataType::AUDIO,  logger);
}
}

