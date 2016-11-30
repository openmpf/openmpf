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

#include "opencv2/core/core.hpp"

#include "detectionComponentUtils.h"
#include "MPFInvalidPropertyException.h"

using std::string;
using std::pair;
using std::exception;
using std::invalid_argument;

using namespace MPF;
using namespace COMPONENT;

namespace DetectionComponentUtils {

    string DetectionDataTypeToString(MPFDetectionDataType dataType) {
        switch (dataType) {
            case MPFDetectionDataType::VIDEO:
                return "video";
            case MPFDetectionDataType::IMAGE:
                return "image";
            case MPFDetectionDataType::AUDIO:
                return "audio";
            default:
                return "unknown data type";

        }
    }

    pair<MPFDetectionError, string> HandleDetectionException(MPFDetectionDataType dataType) {

        MPFDetectionError errorCode;
        string errorMsg;
        const string &dataTypeName = DetectionDataTypeToString(dataType);

        try {
            throw;
        }
        catch (const MPFInvalidPropertyException &ex) {
            errorCode = ex.getErrorCode();
            errorMsg = "An exception occurred while trying to get detections from " + dataTypeName + ": " + ex.what();
        }
        catch (const cv::Exception &ex) {
            errorCode = MPFDetectionError::MPF_DETECTION_FAILED;
            errorMsg = "OpenCV raised an exception while trying to get detections from " + dataTypeName + ": " + ex.what();
        }
        catch (const exception &ex) {
            errorCode = MPFDetectionError::MPF_OTHER_DETECTION_ERROR_TYPE;
            errorMsg = "An exception occurred while trying to get detections from " + dataTypeName + ": " + ex.what();
        }
        catch (...) {
            errorCode = MPFDetectionError::MPF_OTHER_DETECTION_ERROR_TYPE;
            errorMsg = "An unknown error occurred while trying to get detections from " + dataTypeName + ".";
        }
        return std::make_pair(errorCode, errorMsg);
    }


    template <>
    bool GetProperty<bool>(const MPF::COMPONENT::Properties &props,
                           const std::string &key,
                           const bool defaultValue) {
        const string &propValue = GetProperty<string>(props, key, "");
        if (propValue.empty()) {
            return defaultValue;
        }
        if (propValue == "1") {
            return true;
        }

        static const string trueString = "TRUE";
        return std::equal(trueString.begin(), trueString.end(), propValue.begin(), [](char trueChar, char propChar) {
            return trueChar == std::toupper(propChar);
        });
    }

}


