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

#include <array>
#include <getopt.h>
#include <iterator>
#include <filesystem>
#include <fstream>
#include <vector>
#include <utility>
#include <string_view>
#include <sstream>

#include <boost/property_tree/ptree.hpp>
#include <boost/property_tree/json_parser.hpp>

#include "ExecutorUtil.h"

#include "ExecutorConfig.h"

namespace fs = std::filesystem;

using namespace std::string_literals;

namespace MPF::SUBJECT {

namespace {
    struct CliArgs {
        std::string amq_uri;
        std::string descriptor_path;
        std::string language;
    };

    CliArgs ParseCliArgs(int argc, char* argv[]);
    std::string GetBrokerUriFromEnv();
    std::string GetLanguageFromEnv();
    std::string ReadDescriptorString(const CliArgs& cli_args, std::string_view mpf_home);
    std::pair<std::string, std::string> GetNameAndLib(const std::string& descriptor_string);
    std::string GetQueueName(std::string_view component_name);
} // namespace



ExecutorConfig GetConfig(int argc, char* argv[]) {
    auto cli_args = ParseCliArgs(argc, argv);
    if (cli_args.amq_uri.empty()) {
        cli_args.amq_uri = GetBrokerUriFromEnv();
    }
    if (cli_args.language.empty())  {
        cli_args.language = GetLanguageFromEnv();
    }

    auto mpf_home = ExecutorUtil::GetEnv("MPF_HOME").value_or("/opt/mpf");
    auto descriptor_string = ReadDescriptorString(cli_args, mpf_home);
    auto [name, lib] = GetNameAndLib(descriptor_string);
    auto queue_name = GetQueueName(name);
    bool is_python = ExecutorUtil::EqualsIgnoreCase(cli_args.language, "python");
    if (!is_python) {
        throw InvalidConfigurationError{"Only Python components are currently supported."};
    }
    return {
        std::move(cli_args.amq_uri),
        std::move(queue_name),
        std::move(descriptor_string),
        std::move(name),
        is_python,
        std::move(lib),
        ExecutorUtil::GetLogLevelAndSetEnvVar()
    };
}


namespace {
    fs::path FindDescriptor(const fs::path& mpf_home);
    fs::path ExpandPathIfNeeded(const fs::path& raw_path);

    CliArgs ParseCliArgs(int argc, char* argv[]) {
        std::array long_options {
            option{"amq-uri", required_argument, nullptr, 'a'},
            option{"descriptor-path", required_argument, nullptr, 'd'},
            option{"language", required_argument, nullptr, 'l'},
            option{nullptr, 0, nullptr, 0}
        };

        CliArgs args;
        while (true) {
            int option_index = -1;
            switch (getopt_long(argc, argv, "a:d:l:", long_options.data(), &option_index)) {
                case -1:
                    // Reached the end of the argument list.
                    return args;
                case 'a':
                    args.amq_uri = optarg;
                    break;
                case 'd':
                    args.descriptor_path = optarg;
                    break;
                case 'l':
                    args.language = optarg;
                    break;
                case '?':
                    if (option_index >= 0
                            && long_options.at(option_index).has_arg == required_argument) {
                        throw InvalidConfigurationError{
                                "The \""s + argv[optind - 1] + "\" flag requires an argument"};
                    }
                    else {
                        throw InvalidConfigurationError{
                                "Unkown command line option: "s + argv[optind - 1]};
                    }
                default:
                    throw InvalidConfigurationError{
                        "getopt_long failed to parse command line arguments."};
            }
        }
    }

    std::string GetBrokerUriFromEnv() {
        if (auto env_broker_uri = ExecutorUtil::GetEnv("ACTIVE_MQ_BROKER_URI")) {
            return *env_broker_uri;
        }
        if (auto host = ExecutorUtil::GetEnv("ACTIVE_MQ_HOST")) {
            return "failover:(tcp://" + *host + ":61616)?maxReconnectAttempts=13&startupMaxReconnectAttempts=21";
        }
        throw InvalidConfigurationError{
                "ACTIVE_MQ_BROKER_URI, ACTIVE_MQ_HOST, and -a where not set."};
    }

    std::string GetLanguageFromEnv() {
        if (auto env = ExecutorUtil::GetEnv("COMPONENT_LANGUAGE")) {
            return *env;
        }
        else {
            throw InvalidConfigurationError{"COMPONENT_LANGUAGE was not set."};
        }
    }


    std::string ReadDescriptorString(const CliArgs& cli_args, std::string_view mpf_home) {
        auto descriptor_path = cli_args.descriptor_path.empty()
                ? FindDescriptor(mpf_home)
                : ExpandPathIfNeeded(cli_args.descriptor_path);
        try {
            std::ifstream input_stream{descriptor_path};
            input_stream.exceptions(std::ifstream::failbit | std::ifstream::badbit);
            return {std::istreambuf_iterator{input_stream}, {}};
        }
        catch (const std::ios_base::failure& e) {
            throw InvalidConfigurationError{
                "Failed to read descriptor from \"" + descriptor_path.string() + "\" due to: "
                + e.what() };
        }
    }


    fs::path FindDescriptor(const fs::path& mpf_home) {
        auto plugins_dir = mpf_home / "plugins";
        std::vector<fs::path> descriptors;
        for (const auto& dir_entry : fs::directory_iterator{plugins_dir}) {
            if (!dir_entry.is_directory()) {
                continue;
            }
            auto descriptor_path = dir_entry.path() / "descriptor/descriptor.json";
            if (fs::exists(descriptor_path)) {
                descriptors.push_back(std::move(descriptor_path));
            }
        }

        if (descriptors.size() == 1) {
            return std::move(descriptors.front());
        }
        if (descriptors.empty()) {
            throw InvalidConfigurationError{"Unable to find descriptor"};
        }

        bool all_same = std::all_of(
            descriptors.begin() + 1,
            descriptors.end(),
            [&first = descriptors.front()](const auto& path) {
                return fs::equivalent(path, first);
            });
        if (all_same) {
            return std::move(descriptors.front());
        }
        throw InvalidConfigurationError{"Found multiple descriptors"};
    }


    fs::path ExpandPathIfNeeded(const fs::path& raw_path) {
        if (fs::exists(raw_path)) {
            return raw_path;
        }
        fs::path expanded = ExecutorUtil::ExpandFileName(raw_path.string());
        if (fs::exists(expanded)) {
            return expanded;
        }
        throw InvalidConfigurationError{"Descriptor did not exist at: " + raw_path.string()};
    }


    std::pair<std::string, std::string> GetNameAndLib(const std::string& descriptor_string) {
        namespace pt = boost::property_tree;
        pt::ptree descriptor;
        std::istringstream ss{descriptor_string};
        pt::json_parser::read_json(ss, descriptor);
        try {
            return {
                descriptor.get<std::string>("componentName"),
                descriptor.get<std::string>("componentLibrary")};
        }
        catch (const pt::ptree_bad_path& e) {
            throw InvalidConfigurationError{
                    "The descriptor did not contain: " + e.path<pt::ptree::path_type>().dump()};
        }
    }

    std::string GetQueueName(std::string_view component_name) {
        auto queue_name = "MPF.SUBJECT_"s;
        for (char c : component_name) {
            queue_name.push_back(static_cast<char>(std::toupper(c)));
        }
        queue_name += "_REQUEST";
        return queue_name;
    }
} // namespace
} // namespace MPF::SUBJECT
