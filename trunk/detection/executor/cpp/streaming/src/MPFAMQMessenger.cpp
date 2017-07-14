
#include "MPFAMQMessenger.h"

using namespace std;
using namespace cms;
using activemq::library::ActiveMQCPP;
using activemq::core::ActiveMQConnectionFactory;


MPFMessengerError MPFAMQMessenger::Startup(const string &broker_name,
                                           Properties &properties) {
    try {
        // This call will generate a runtime error if it fails
        ActiveMQCPP::initializeLibrary();


        // Create an ActiveMQ ConnectionFactory
        unique_ptr<ActiveMQConnectionFactory> factory(new ActiveMQConnectionFactory(broker_name));
        
        // Set prefetch policy to 1
        // PrefetchPolicy *policy = new DefaultPrefetchPolicy();
        // policy->setQueuePrefetch(1);
        // policy->setTopicPrefetch(1);
        // connection_factory_->setPrefetchPolicy(policy);
        factory->setCloseTimeout(1);
        factory->setOptimizeAcknowledge(true);

        // Create an ActiveMQ Connection
        connection_.reset(factory->createConnection());
        connection_->start();

        // Create an ActiveMQ session
        session_.reset(connection_->createSession(Session::SESSION_TRANSACTED));

    } catch (CMSException& e) {
        LOG4CXX_ERROR(main_logger_, "CMSException in MPFAMQMessenger::Startup: " << e.getMessage() << "\n" << e.getStackTraceString());
        throw;
    } catch (std::exception& e) {
        // When thrown, this will be caught and logged by the main program
    } catch (...) {
        LOG4CXX_ERROR(main_logger_, "Unknown Exception occurred in MPFMessenger::Startup");
        throw;
    }
    initialized_ = true;
    return MPF_MESSENGER_SUCCESS;

}

MPFMessengerError 
MPFAMQMessenger::CreateReceiver(const std::string &queue_name,
                                Properties &queue_properties,
                                MPFMessageReceiver *receiver) {


}

MPFMessengerError 
MPFAMQMessenger::CreateSender(const std::string &queue_name,
                              Properties &queue_properties,
                              MPFMessageSender *sender) {}

MPFMessengerError 
MPFAMQMessenger::CloseReceiver(MPFMessageReceiver *receiver) {}

MPFMessengerError MPFAMQMessenger::CloseSender(MPFMessageSender *sender) {}

MPFMessengerError MPFAMQMessenger::Shutdown() {}

