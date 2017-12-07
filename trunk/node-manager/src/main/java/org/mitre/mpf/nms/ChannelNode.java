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

package org.mitre.mpf.nms;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Light-weight access class for connecting to a JGroups channel
 */
@Component
public class ChannelNode {

    private static final Logger log = LoggerFactory.getLogger(ChannelNode.class);

    private final NodeManagerProperties properties;

    private JChannel channel;
    private boolean isConnected;

    @Autowired
    public ChannelNode(NodeManagerProperties properties) {
    	this.properties = properties;
    }


    public void connect(String nodeName, Receiver receiver) {
        try {
	        channel = new JChannel(properties.getJGroupsConfig().getURL());
	        channel.setName(nodeName);
            channel.connect(properties.getChannelName());
            channel.setReceiver(receiver);
            channel.getState(null, 10000);
            isConnected = true;
        } catch (Exception e) {
        	log.error("Exception thrown when trying to create and configure the JGroups channel", e);
        }
    }

    public void setReceiver(Receiver receiver) {
    	//the receiver should already be set in ChannelNode
    	if(channel.getReceiver() == null) {
	    	try {    		
	      		channel.setReceiver(receiver); //causes the warning %s already set
	    	} catch(Exception e) {
	    		log.error("Failed to the set the JChannel receiver, could be an invalid hostname, please check earlier log statements.", e);
	    	}
    	}
    }

    /**
     *
     * @param address : dest address or NULL for bcast
     * @param object : object to send (any serializeable)
     */
    public void send(Address address, Object object) {
        // send
        try {
            channel.send(address, object);
        } catch (Exception e) {
            log.error("Failed to send to channel because of an exception.", e);
        }
    }

    public void broadcast(Object object) {
        send(null, object);
    }

    public Address getAddress() {
        return channel.getAddress();
    }

    public JChannel getChannel() {
        return channel;
    }

    public boolean isConnected() { return isConnected; }

    /**
     * Shut down the baseNode cleanly
     */
    public void shutdown() {
        log.debug("Shutdown requested.");
        channel.disconnect();
        channel.close();
        isConnected = false;
        log.debug("Channel closed.");
    }
}
