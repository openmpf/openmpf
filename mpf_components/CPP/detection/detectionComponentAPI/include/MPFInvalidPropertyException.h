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



#ifndef MPF_CPP_COMPONENTS_INVALIDPROPERTY_H
#define MPF_CPP_COMPONENTS_INVALIDPROPERTY_H

#include <stdexcept>
#include "detection_base.h"

namespace MPF { namespace COMPONENT {

class MPFInvalidPropertyException : public std::invalid_argument {

  public:

    MPFInvalidPropertyException(const std::string &propertyName,
                           const std::string &reason = "",
                           MPFDetectionError detectionError = MPF_INVALID_PROPERTY);

    MPFInvalidPropertyException(const std::string &propertyName, MPFDetectionError detectionError);

    MPFDetectionError getErrorCode() const;

  private:
    const MPFDetectionError errorCode_;

    static std::string createMessage(const std::string &propertyName,
                                     const std::string &reason);

};

}}


#endif //MPF_CPP_COMPONENTS_INVALIDPROPERTY_H
