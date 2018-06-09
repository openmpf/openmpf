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

package org.mitre.mpf.nms;

import org.apache.commons.lang3.tuple.Pair;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.mitre.mpf.nms.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Light-weight access class for connecting to a JGroups channel
 */
@Component
public class ChannelNode {

    private static final Logger log = LoggerFactory.getLogger(ChannelNode.class);

    private final PropertiesUtil propertiesUtil;

    private JChannel channel;
    private boolean isConnected;

    @Autowired
    public ChannelNode(PropertiesUtil propertiesUtil) {
    	this.propertiesUtil = propertiesUtil;
    }


    public void connect(String nodeName, Receiver receiver) {
        try {
	        channel = new JChannel(propertiesUtil.getJGroupsConfig().getURL());
	        channel.setName(nodeName);
            channel.setReceiver(receiver);
            channel.connect(propertiesUtil.getChannelName());
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

    public View getView() {
        return channel.getView();
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void sendToChild(String hostname, Serializable message) {
        Address nodeAddress = getNodeAddress(hostname, NodeTypes.NodeManager);
        send(nodeAddress, message);
    }

    private Address getNodeAddress(String hostname, NodeTypes nodeType) {
        Pair<String, NodeTypes> searchPair = Pair.of(hostname, nodeType);
        return getView().getMembers().stream()
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
        List<Address> memberAddresses = getView().getMembers();
        for (Address address : memberAddresses) {
            Pair<String, NodeTypes> hostType = AddressParser.parse(address);
            if (hostType != null && hostType.getRight() == NodeTypes.MasterNode) {
                return address;
            }
        }
        throw new IllegalStateException("Unable to locate master node.");
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


    public Set<String> getAvailableNodes() {

        View view = channel.getView();
        if (view == null) {
            throw new IllegalStateException("Error: JGroups channel has not connected yet. Cannot retrieve JGroups host list.");
        }

        List<Address> memberAddresses = view.getMembers();

        Set<String> availableNodes = new TreeSet<>(); // ordered
        for (Address member : memberAddresses) {
            Pair<String, NodeTypes> pair = AddressParser.parse(member);
            if (pair != null && pair.getRight() == NodeTypes.NodeManager) {
                availableNodes.add(pair.getLeft());
            }
        }

        return availableNodes;
    }
}
