/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

#include "BatchExecutorUtil.h"

#include <algorithm>
#include <unistd.h>


std::map<std::string, std::string> BatchExecutorUtil::get_environment_job_properties() {
    static std::string property_prefix = "MPF_PROP_";

    std::map<std::string, std::string> properties;
    for (int i = 0; environ[i] != nullptr; i++) {
        std::string env_pair = environ[i];
        // Filter out items that are too short so std::equal isn't checking past the end of the string.
        if (env_pair.size() <= property_prefix.size()) {
            continue;
        }

        bool env_var_has_prefix = std::equal(property_prefix.begin(), property_prefix.end(),
                                             env_pair.begin());
        if (!env_var_has_prefix) {
            continue;
        }

        size_t equals_pos = env_pair.find('=');
        // Don't process environment variables named "MPF_PROP_".
        if (equals_pos <= property_prefix.size()) {
            continue;
        }

        size_t prop_name_length = equals_pos - property_prefix.size();
        properties.emplace(
                env_pair.substr(property_prefix.size(), prop_name_length),
                env_pair.substr(equals_pos + 1));
    }
    return properties;
}
