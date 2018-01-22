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

import org.apache.commons.lang3.tuple.Pair;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;

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
     * @param address : destination address or null for broadcast
     * @param object : object to send
     */
    public void send(Address address, Serializable object) {
        // send
        try {
            channel.send(address, object);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void broadcast(Serializable object) {
        send(null, object);
    }

    public Address getAddress() {
        return channel.getAddress();
    }

    public JChannel getChannel() {
        return channel;
    }

    public boolean isConnected() { return isConnected; }



    public void sendToChild(String hostname, Serializable message) {
        Address nodeAddress = getNodeAddress(hostname, NodeTypes.NodeManager);
        send(nodeAddress, message);
    }

    private Address getNodeAddress(String hostname, NodeTypes nodeType) {
        Pair<String, NodeTypes> searchPair = Pair.of(hostname, nodeType);
        return getChannel().getView().getMembers().stream()
                .filter(addr -> searchPair.equals(AddressParser.parse(addr)))
                .findAny()
		        .orElseThrow(() -> new IllegalStateException(String.format(
                        "Unable to locate node with hostname \"%s\" and type %s", hostname, nodeType)));
    }


    public void sendToMaster(Serializable message) {
        try {
            channel.send(getMasterNodeAddress(), message);
        }
        catch (Exception e) {
        	throw new IllegalStateException(e);
        }
    }


    private Address getMasterNodeAddress() {
        log.info("Attempting to locate master node address.");
        List<Address> memberAddresses = getChannel().getView().getMembers();

        String members = memberAddresses.stream()
                .map(Object::toString)
                .collect(joining("\n"));
        log.info("Current members:\n{}", members);


        List<Address> masterNodes = new ArrayList<>();
        for (Address address : memberAddresses) {
            log.info("About to parse address: {}", address);
            Pair<String, NodeTypes> hostType = AddressParser.parse(address);
            if (hostType == null) {
                log.warn("Failed to parse address: {}", address);
                continue;
            }
            log.info("Successfully parsed {}. Host is {}. Type is: {}", address, hostType.getLeft(), hostType.getRight());
            if (hostType.getRight() == NodeTypes.MasterNode) {
            	masterNodes.add(address);
            }
        }

        if (masterNodes.isEmpty()) {
            throw new IllegalStateException("Unable to locate master node.");
        }
        if (masterNodes.size() > 1) {
            log.warn("Multiple master nodes found!");
        }
        return masterNodes.get(0);
    }



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
