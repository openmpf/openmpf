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



#include "MPFInvalidPropertyException.h"

using std::invalid_argument;
using std::string;

namespace MPF { namespace COMPONENT {


    MPFInvalidPropertyException::MPFInvalidPropertyException(const string &propertyName,
                                           const string &reason,
                                           MPFDetectionError detectionError)
            : invalid_argument(createMessage(propertyName, reason))
            , errorCode_(detectionError) {

    }

    MPFInvalidPropertyException::MPFInvalidPropertyException(const string &propertyName, MPFDetectionError detectionError)
            : MPFInvalidPropertyException(propertyName, "", detectionError) {

    }


    string MPFInvalidPropertyException::createMessage(const string &propertyName, const string &reason) {
        return "The " + propertyName + " job property contained an invalid value. " + reason;
    }


    MPFDetectionError MPFInvalidPropertyException::getErrorCode() const {
        return errorCode_;
    }
}}
