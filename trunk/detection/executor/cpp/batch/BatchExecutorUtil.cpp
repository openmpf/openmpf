/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

#include <string_view>

#include <unistd.h>

#include "BatchExecutorUtil.h"


std::map<std::string, std::string> BatchExecutorUtil::get_environment_job_properties() {
    static std::string_view property_prefix = "MPF_PROP_";

    std::map<std::string, std::string> properties;
    for (auto env_ptr = environ; *env_ptr; env_ptr++) {
        std::string_view env_pair = *env_ptr;
        if (env_pair.size() > property_prefix.size()
                && property_prefix == env_pair.substr(0, property_prefix.size())
                // // Don't process environment variables named "MPF_PROP_".
                && env_pair[property_prefix.size()] != '=') {
            env_pair.remove_prefix(property_prefix.size());
            size_t equals_pos = env_pair.find('=');
            properties.emplace(env_pair.substr(0, equals_pos), env_pair.substr(equals_pos + 1));
        }
    }
    return properties;
}
