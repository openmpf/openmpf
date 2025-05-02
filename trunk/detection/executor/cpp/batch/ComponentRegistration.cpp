/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2025 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2025 The MITRE Corporation                                       *
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

#include <iterator>
#include <filesystem>
#include <fstream>
#include <optional>
#include <string>

#include <cms/CMSException.h>

#include "BatchExecutorUtil.h"
#include "ComponentLoadError.h"

#include "ComponentRegistration.h"

namespace fs = std::filesystem;

namespace {

bool ShouldSkipRegistration() {
    auto env_val = BatchExecutorUtil::GetEnv("DISABLE_COMPONENT_REGISTRATION");
    return env_val && *env_val != "0" && !BatchExecutorUtil::EqualsIgnoreCase(*env_val, "false");
}


std::optional<fs::path> GetDescriptorFromEnvVar() {
    auto env_path = BatchExecutorUtil::GetEnv("DESCRIPTOR_PATH");
    if (!env_path) {
        return {};
    }
    else if (fs::exists(*env_path)) {
        return *env_path;
    }
    else {
        throw ComponentLoadError{
                R"(The "DESCRIPTOR_PATH" environment variable is set to ")"
                + *env_path + "\", but that file does not exist."};
    }
}


std::optional<fs::path> GetDescriptorFromComponentName() {
    auto component_name = BatchExecutorUtil::GetEnv("COMPONENT_NAME");
    if (!component_name) {
        return {};
    }
    fs::path mpf_home = BatchExecutorUtil::GetEnv("MPF_HOME").value_or("/opt/mpf");
    auto descriptor_path = mpf_home / "plugins" / *component_name / "descriptor/descriptor.json";
    if (fs::exists(descriptor_path)) {
        return descriptor_path;
    }
    else {
        return {};
    }
}

fs::path FindOnlyDescriptor() {
    fs::path mpf_home = BatchExecutorUtil::GetEnv("MPF_HOME").value_or("/opt/mpf");
    auto plugins_dir = mpf_home / "plugins";

    std::optional<fs::path> first_match;
    for (const auto& entry : fs::directory_iterator{plugins_dir}) {
        auto descriptor_path = entry.path() / "descriptor/descriptor.json";
        if (!fs::exists(descriptor_path)) {
            continue;
        }
        if (!first_match) {
            first_match = descriptor_path;
            continue;
        }
        if (!fs::equivalent(*first_match, descriptor_path)) {
            throw ComponentLoadError{
                    "Multiple descriptor files were found. Set the \"DESCRIPTOR_PATH\" "
                    "environment variable to the path of the descriptor that should be used."};
        }
    }
    if (first_match) {
        return *first_match;
    }
    else {
        throw ComponentLoadError{
                "Could not find a descriptor file. Set the \"DESCRIPTOR_PATH\" environment "
                "variable to the path of the descriptor file that should be used."};
    }
}


fs::path GetDescriptorPath() {
    if (auto env_path = GetDescriptorFromEnvVar()) {
        return *env_path;
    }
    else if (auto path = GetDescriptorFromComponentName()) {
        return *path;
    }
    else {
        return FindOnlyDescriptor();
    }
}

std::string GetDescriptor() {
    auto path = GetDescriptorPath();
    std::ifstream file{path};
    file.exceptions(std::ifstream::failbit | std::ifstream::badbit);
    return { std::istreambuf_iterator<char>{file}, {} };
}


} // end anonymous namespace


void MPF::COMPONENT::RegisterComponent(const LoggerWrapper& logger, Messenger& messenger) {
    if (ShouldSkipRegistration()) {
        return;
    }

    logger.Info("Starting component registration.");
    auto response = messenger.SendTextRequestResponse(
            "MPF.DETECTION_COMPONENT_REGISTRATION", GetDescriptor());
    bool was_successful = false;
    try {
        was_successful = response->getBooleanProperty("success");
    }
    catch (const cms::CMSException&) {
        // was_successful should remain false.
    }

    std::optional<std::string> opt_detail;
    try {
        opt_detail = response->getStringProperty("detail");
    }
    catch (const cms::CMSException&)  {
        // opt_detail should remain empty.
    }

    if (was_successful) {
        if (opt_detail) {
            logger.Info("Successfully registered component. Response from server: ", *opt_detail);
        }
        else {
            logger.Info("Successfully registered component.");
        }
    }
    else {
        if (opt_detail) {
            throw ComponentLoadError{"Registration failed with response: " + *opt_detail};
        }
        else {
            throw ComponentLoadError{"Registration failed with no details."};
        }
    }
}
