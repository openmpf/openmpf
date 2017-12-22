/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

#include <map>
#include <string>

#include <boost/property_tree/ptree.hpp>
#include <boost/property_tree/ini_parser.hpp>

#include "JobSettings.h"


std::map<std::string, std::string> convert_to_map(const boost::property_tree::ptree &property_tree) {
    std::map<std::string, std::string> result;
    for (const auto &tree_element : property_tree) {
        result[tree_element.first] = tree_element.second.data();
    }
    return result;
};


JobSettings JobSettings::FromIniFile(const std::string &ini_path) {
    boost::property_tree::ptree ini_settings;
    boost::property_tree::ini_parser::read_ini(ini_path, ini_settings);
    const boost::property_tree::ptree &job_config = ini_settings.get_child("Job Config");
    return {
            .job_id = job_config.get<int>("jobId"),
            .stream_uri = job_config.get<std::string>("streamUri"),
            .segment_size = job_config.get<int>("segmentSize"),
            .stall_timeout = job_config.get<long>("stallTimeout"),
            .stall_alert_threshold = job_config.get<long>("stallAlertThreshold"),
            .component_name = job_config.get<std::string>("componentName"),
            .component_lib_path = job_config.get<std::string>("componentLibraryPath"),
            .message_broker_uri = job_config.get<std::string>("messageBrokerUri"),
            .job_status_queue = job_config.get<std::string>("jobStatusQueue"),
            .activity_alert_queue = job_config.get<std::string>("activityAlertQueue"),
            .summary_report_queue = job_config.get<std::string>("summaryReportQueue"),
            .job_properties = convert_to_map(ini_settings.get_child("Job Properties", {})),
            .media_properties = convert_to_map(ini_settings.get_child("Media Properties", {})),
    };
}
