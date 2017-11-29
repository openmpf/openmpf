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

//TODO: This file contains simple test code for testing the ActiveMQ
// messaging. The frame reader component itself is not used in single
// process, single pipeline stage, architecture.


#include <iostream>
#include <string>
#include <sys/types.h>
#include <sys/time.h>
#include <cstdlib>
#include <unistd.h>
#include <dlfcn.h>  // for dlopen(), dlsym(), etc.

#include <log4cxx/logger.h>
#include <log4cxx/xml/domconfigurator.h>
#include <log4cxx/simplelayout.h>
#include <log4cxx/consoleappender.h>
#include <log4cxx/fileappender.h>
#include <log4cxx/logmanager.h>
#include <log4cxx/level.h>

#include <boost/property_tree/ptree.hpp>
#include <boost/property_tree/ini_parser.hpp>

#include <QDir>
#include <QMap>
#include <QHash>
#include <QFile>
#include <QObject>
#include <QString>
#include <QStringList>
#include <QCoreApplication>
#include <QTime>

#include "MPFDetectionComponent.h"
#include "OcvFrameReader.h"
#include "MPFAMQMessage.h"
#include "MPFAMQMessenger.h"
#include "MPFFrameStore.h"

using std::exception;
using std::string;
using std::map;
using std::vector;

using namespace MPF;
using namespace COMPONENT;
namespace pt = boost::property_tree;

string GetFileName(const string& s) {
    size_t i = s.rfind('/', s.length());
    if (i != string::npos) {
        return s.substr(i+1, s.length()-i);
    }
    return s;
}


template<typename T>
void getArg(pt::ptree &Ptree,
            const string &prop_tree_path,
            const T &defaultValue,
            T &arg);

template<typename T>
int getArg(pt::ptree &Ptree,
           const string &prop_tree_path,
           T &arg);

/**
 * This is the main program for the Streaming Video Frame Reader.
 * The program runs indefinitely, until it receives a command on the
 * standard input from the Node Manager Master. Expected commands are:
 *                "pause": the user has canceled the job, so wait for
 *                         the quit command
 *                "quit": tear down all resources and exit.
 * @return An integer representing the exit status of the program
 *         Exit status codes are defined as follows:
 *                EXIT_SUCCESS = exited after receiving a "quit" command
                  EXIT_FAILURE = exited due to unrecoverable processing error.
 */
int main(int argc, char* argv[]) {

    // Create a Core Application so that we can access the path to the executable,
    // since the config and log files are found relative to that path.
    QCoreApplication *this_app = new QCoreApplication(argc, argv);
    string app_dir = (this_app->applicationDirPath()).toStdString();
    delete this_app;

    log4cxx::xml::DOMConfigurator::configure(app_dir + "/Log4cxxConfig.xml");
    log4cxx::LoggerPtr logger = log4cxx::Logger::getLogger("org.mitre.mpf.framereader");

    int job_id;
    string job_name;
    string broker_uri;
    pt::ptree jobArgs;

    string config_file(argv[1]);
    std::cout << "config file = " << config_file << std::endl;
    try {
        pt::ini_parser::read_ini(config_file, jobArgs);
    } catch (const pt::ini_parser::ini_parser_error &e) {
        std::cout << "Unable to parse config file named " << config_file << ": " <<e.what() << std::endl;
        LOG4CXX_ERROR(logger, "Unable to parse config file named " << config_file << ": " <<e.what());
        return 5; // EIO = I/O error
    }

    int rc = getArg<int>(jobArgs, "job_id", job_id);
    if (rc) {
        std::cout << "couldn't read property" << job_id << std::endl;
        return rc;
    }

    job_name = "Job " + std::to_string(job_id);
    LOG4CXX_INFO(logger, "JOB NAME = " << job_name);
    
    Properties job_properties;

    job_properties["JOB_NAME"] = job_name;

    const string default_broker_uri = "failover://(tcp://localhost:61616?jms.prefetchPolicy.all=1)?startupMaxReconnectAttempts=1";
    getArg<string>(jobArgs, "broker_uri", default_broker_uri, broker_uri);
    LOG4CXX_DEBUG(logger, "BROKER = " << broker_uri);

    //    OcvFrameReader Freader(logger);

    // Create the messenger object

    std::shared_ptr<AMQMessagingManager> msg_mgr(new AMQMessagingManager(logger));
    try {
        msg_mgr->Connect(broker_uri, job_properties);
    }
    catch (MPFMessageException &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to connect to the activemq broker");
        exit;
    }

    AMQMessenger messenger(msg_mgr, logger);

    // Initialize the frame ready message queue
    string queue_name;
    rc = getArg<string>(jobArgs, "queue_name", queue_name);
    if (rc) return rc;
    LOG4CXX_INFO(logger, "QUEUE NAME = " << queue_name);

    Properties queue_props;
    try {
        messenger.InitQueue(queue_name, queue_props);
    }
    catch (MPFMessageException &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to initialize the queue");
        exit;
    }

    try {
        messenger.CreateProducer();
    }
    catch (MPFMessageException &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to create the producer");
        exit;
    }

    try {
        std::cout << __LINE__ << ": CreateConsumer" << std::endl;
        messenger.CreateConsumer();
    }
    catch (MPFMessageException &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to create the consumer");
        exit;
    }

    try {
        msg_mgr->Start();
    }
    catch (MPFMessageException &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to start activemq connection");
        exit;
    }

    try {
        std::unique_ptr<AMQFrameReadyMessage> msg(new AMQFrameReadyMessage(job_name,job_id, 1, 2, 3));
        std::cout << __LINE__ << ": PutMessage" << std::endl;
        messenger.PutMessage<AMQFrameReadyMessage>(std::move(msg));
    }
    catch (MPFMessageException &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to connect to send the message");
        exit;
    }

    try {
        std::cout << __LINE__ << ": GetMessage" << std::endl;
        std::unique_ptr<AMQFrameReadyMessage> frame_ready_message = messenger.GetMessage<AMQFrameReadyMessage>();
        std::cout << "Message received:"
                  << "\n  job name: " << frame_ready_message->job_name_
                  << "\n  job id: " << frame_ready_message->job_number_
                  << "\n segment number: " << frame_ready_message->segment_number_
                  << "\n frame index: " << frame_ready_message->frame_index_
                  << "\n frame offset bytes: " << frame_ready_message->frame_offset_bytes_
                  << std::endl;
    }
    catch (MPFMessageException &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to send the message: MPFMessageException caught");
        std::cout << "MPFMessageException caught: " << e.what() << std::endl;
        exit;
    }
    catch (std::exception &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to send the message: std::exception caught");
        exit;
    }

    try {
        msg_mgr->Stop();
    }
    catch (MPFMessageException &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to stop activemq connection");
        exit;
    }

    try {
        messenger.Close();
    }
    catch (MPFMessageException &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to close the messenger");
        exit;
    }
    try {
        msg_mgr->Shutdown();
    }
    catch (MPFMessageException &e) {
        LOG4CXX_ERROR(logger, job_name <<  ": Failed to shut down activemq connection");
        exit;
    }


    // Close the logger
    log4cxx::LogManager::shutdown();
    return 0;
}
    



// This version does not allow a default value and returns an error if
// the property is not found
template<typename T>
int getArg(pt::ptree &Ptree,
           const string &prop_tree_path,
            T &arg) {

    try {
        arg = Ptree.get<T>(prop_tree_path);
    } catch (const pt::ptree_error &e) {
        std::cout << "Could not read configuration property\""
                      << prop_tree_path
                      << "\" : " << e.what();
        return 22; // EINVAL = invalid argument
    }
    return 0;
}


template<typename T>
void getArg(pt::ptree &Ptree,
            const string &prop_tree_path,
            const T &defaultValue,
            T &arg) {

    try {
        arg = Ptree.get<T>(prop_tree_path);
    } catch (const pt::ptree_error &e) {
        arg = defaultValue;
    }
}


