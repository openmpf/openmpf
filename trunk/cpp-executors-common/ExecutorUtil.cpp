/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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
#include <cstring>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <unistd.h>
#include <wordexp.h>

#include "ExecutorUtil.h"


std::optional<std::string> ExecutorUtil::GetEnv(std::string_view name) {
    const auto* env_val = std::getenv(name.data());
    if (env_val == nullptr || std::strlen(env_val) == 0) {
        return {};
    }
    else {
        return std::string{env_val};
    }
}

std::string ExecutorUtil::GetLogLevelAndSetEnvVar() {
    auto env_level = GetEnv("LOG_LEVEL");
    if (!env_level) {
        const auto* level_name = "INFO";
        setenv("LOG_LEVEL", level_name, 1);
        return level_name;
    }

    auto level_name = *env_level;
    std::transform(level_name.begin(), level_name.end(), level_name.begin(),
                   [](auto c) { return std::toupper(c); });
    if (level_name == "WARNING") {
        // Python logging accepts either WARNING or WARN, but Log4CXX requires it be WARN.
        setenv("LOG_LEVEL", "WARN", 1);
        return "WARN";
    }
    else if (level_name == "CRITICAL") {
        // Python logging accepts either CRITICAL or FATAL, but Log4CXX requires it be FATAL.
        setenv("LOG_LEVEL", "FATAL", 1);
        return "FATAL";
    }
    else if (level_name == "TRACE" || level_name == "DEBUG" || level_name == "INFO"
            || level_name == "WARN" || level_name == "ERROR" || level_name == "FATAL") {
        return level_name;
    }
    else {
        level_name = "DEBUG";
        std::cerr << "The LOG_LEVEL environment variable is set to " << *env_level
                  << " but that isn't a valid log level. Using " << level_name << " instead.\n";

        setenv("LOG_LEVEL", level_name.c_str(), 1);
        return level_name;
    }
}


bool ExecutorUtil::EqualsIgnoreCase(std::string_view s1, std::string_view s2) {
    if (s1.length() != s2.length()) {
        return false;
    }
    return std::equal(
        s1.begin(), s1.end(),
        s2.begin(),
        [](char c1, char c2) { return std::toupper(c1) == std::toupper(c2); });
}


std::string ExecutorUtil::ExpandFileName(std::string_view file_name) {
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
