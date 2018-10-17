/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

#include <iostream>
#include <log4cxx/basicconfigurator.h>
#include <log4cxx/xml/domconfigurator.h>

#include "ExecutorErrors.h"
#include "ExecutorUtils.h"
#include "JobSettings.h"
#include "OcvFrameReader.h"


int main(int argc, char* argv[]) {
    if (argc != 2) {
        std::cerr << "Usage: " << argv[0] << " <ini_path>" << std::endl;
        return static_cast<int>(MPF::COMPONENT::ExitCode::INVALID_COMMAND_LINE_ARGUMENTS);
    }

    // Set up the logging
    std::string app_dir = MPF::COMPONENT::ExecutorUtils::GetAppDir();
    std::string config_file = app_dir + "/../config/Log4cxx-framereader-Config.xml";
    log4cxx::xml::DOMConfigurator::configure(config_file);
    log4cxx::LoggerPtr logger = log4cxx::Logger::getLogger("org.mitre.mpf.framereader");
    if (logger->getAllAppenders().empty()) {
        log4cxx::BasicConfigurator::configure();
        LOG4CXX_WARN(logger, "Unable to load log configuration file at "
                     << config_file << ". Logging to standard out instead.");
    }

    // Read the settings and create the streaming video job
    LOG4CXX_INFO(logger, "Loading job settings from: " << argv[1]);

    try {
        MPF::COMPONENT::JobSettings settings = MPF::COMPONENT::JobSettings::FromIniFile(argv[1]);

        std::string job_name = "Streaming Job #" + std::to_string(settings.job_id);
        LOG4CXX_INFO(logger, "Initializing " << job_name);

        std::string log_prefix = "[" + job_name + "] ";
        MPF::COMPONENT::MPFStreamingVideoJob job(job_name, app_dir, 
                                                 settings.job_properties,
                                                 settings.media_properties);

        // Establish the messaging connection
        MPF::MPFMessagingConnection connection(settings);

        MPF::COMPONENT::OcvFrameReader frame_reader(logger, log_prefix, connection, job, std::move(settings));
        return static_cast<int>(frame_reader.RunJob());
    }
    catch (const cms::CMSException &ex) {
        LOG4CXX_ERROR(logger, "Exiting due to message broker error: " << ex.what());
        return static_cast<int>(MPF::COMPONENT::ExitCode::MESSAGE_BROKER_ERROR);
    }
    catch (const MPF::COMPONENT::FatalError &ex) {
        LOG4CXX_ERROR(logger, "Exiting due to error: " << ex.what());
        return static_cast<int>(ex.GetExitCode());
    }
    catch (const std::exception &ex) {
        LOG4CXX_ERROR(logger, "Exiting due to error: " << ex.what());
        return static_cast<int>(MPF::COMPONENT::ExitCode::UNEXPECTED_ERROR);
    }
    catch (...) {
        LOG4CXX_ERROR(logger, "Exiting due to error.");
        return static_cast<int>(MPF::COMPONENT::ExitCode::UNEXPECTED_ERROR);
    }
}
