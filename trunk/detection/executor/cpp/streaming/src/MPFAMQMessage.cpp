#include "MPFAMQMessage.h"


MPFAMPFReceiver::MPFAMPFReceiver(const std::string queue_name) {
    queue_name_ = queue_name;
    request_destination_
