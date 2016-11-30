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

#ifndef DETECTION_COMPONENT_API_UTILS_H
#define DETECTION_COMPONENT_API_UTILS_H

#include <string>

#include <boost/lexical_cast.hpp>

#include "detection_base.h"

namespace DetectionComponentUtils {

template <typename T>
T GetProperty(const MPF::COMPONENT::Properties &props,
              const std::string &key,
              const T defaultValue) {
    auto iter = props.find(key);
    if (iter == props.end()) {
        return defaultValue;
    }
    else {
        T tmp;
        try {
            tmp = boost::lexical_cast<T>(iter->second);
        }
        catch (const boost::bad_lexical_cast &e) {
            return defaultValue;
        }
        return tmp;
    }
}

/**
 * Specialization of GetProperty to support converting the string "true" to a boolean.
 * This specialization is necessary because boost::lexical_cast only considers the string "1" to be true.
 * @param props
 * @param key
 * @param defaultValue
 * @return true if the property value is "true" (case-insensitive) or "1"; false otherwise
 */
template<>
bool GetProperty(const MPF::COMPONENT::Properties &props,
                 const std::string &key,
                 const bool defaultValue);


std::string DetectionDataTypeToString(MPF::COMPONENT::MPFDetectionDataType dataType);

/**
 * Exception dispatcher pattern from https://isocpp.org/wiki/faq/exceptions#throw-without-an-object
 * Catches the current exception and returns a pair containing the error code and error message
 * @param dataType Type of the input data that caused the exception
 * @return Appropriate error code and message for handled exception
 */
std::pair<MPF::COMPONENT::MPFDetectionError, std::string>
HandleDetectionException(MPF::COMPONENT::MPFDetectionDataType dataType);

}

#endif  // DETECTION_COMPONENT_API_UTILS_H
