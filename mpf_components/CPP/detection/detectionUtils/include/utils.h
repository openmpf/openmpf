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

#ifndef TRUNK_DETECTION_COMMON_UTILS_UTILS_H_
#define TRUNK_DETECTION_COMMON_UTILS_UTILS_H_

#include <string>
#include <log4cxx/logger.h>

#include <opencv2/core/core.hpp>
#include <opencv2/objdetect/objdetect.hpp>
#include <opencv2/video/tracking.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/calib3d/calib3d.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>

#include <boost/lexical_cast.hpp>

#include "detection_base.h"

namespace Utils {

cv::Mat ConvertToGray(const cv::Mat &image);


/**
 * Exception dispatcher pattern from https://isocpp.org/wiki/faq/exceptions#throw-without-an-object
 * Catches the current exception, logs a message, and returns an appropriate error code.
 * @param job The job that caused the exception
 * @param logger The logger that will be used to show the message.
 * @return The error code.
 */
MPF::COMPONENT::MPFDetectionError HandleDetectionException(const MPF::COMPONENT::MPFVideoJob &job,
                                                           log4cxx::LoggerPtr logger);

MPF::COMPONENT::MPFDetectionError HandleDetectionException(const MPF::COMPONENT::MPFImageJob &job,
                                                           log4cxx::LoggerPtr logger);

MPF::COMPONENT::MPFDetectionError HandleDetectionException(const MPF::COMPONENT::MPFAudioJob &job,
                                                           log4cxx::LoggerPtr logger);


}

#endif  // TRUNK_DETECTION_COMMON_UTILS_UTILS_H_
