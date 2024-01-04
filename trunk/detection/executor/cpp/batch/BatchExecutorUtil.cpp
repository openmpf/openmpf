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

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <iomanip>
#include <sstream>
#include <stdexcept>

#include <unistd.h>
#include <wordexp.h>

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

bool BatchExecutorUtil::EqualsIgnoreCase(std::string_view s1, std::string_view s2) {
    if (s1.length() != s2.length()) {
        return false;
    }
    return std::equal(s1.begin(), s1.end(), s2.begin(),
                      [](char c1, char c2)
                      { return std::toupper(c1) == std::toupper(c2); });
}


std::string BatchExecutorUtil::ExpandFileName(std::string_view file_name) {
    std::ostringstream quoted_ss;
    // Adds leading and trailing quotes to prevent word splitting by wordexp. Also escapes quotes
    // that were already in the file name, so that wordexp treats them as literal quotes.
    quoted_ss << std::quoted(file_name);
    auto quoted_file_name = quoted_ss.str();

    wordexp_t exp_ctx;
    int rc = wordexp(quoted_file_name.data(), &exp_ctx, 0);
    if (rc == 0) {
        std::string expanded_path{*exp_ctx.we_wordv};
        wordfree(&exp_ctx);
        return expanded_path;
    }
    auto error_msg = "An error occurred while trying to expand \"" + std::string{file_name} + "\"";
    switch (rc) {
        case WRDE_BADCHAR:
            error_msg += ": Illegal occurrence of an unescaped character from the set: \\n, |, &, ;, <, >, (, ), {, }.";
            break;
        case WRDE_BADVAL:
            error_msg += ": An undefined shell variable was referenced.";
            break;
        case WRDE_SYNTAX:
            error_msg += ": Shell syntax error, such as unbalanced parentheses or unmatched quotes.";
            break;
        default:
            error_msg += '.';
    }
    throw std::invalid_argument{error_msg};
}


std::optional<std::string> BatchExecutorUtil::GetEnv(std::string_view name) {
    auto env_val = std::getenv(name.data());
    if (env_val == nullptr || std::strlen(env_val) == 0) {
        return {};
    }
    else {
        return std::string{env_val};
    }
}
