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
import org.jgroups.*;
import org.jgroups.protocols.TCPPING;
import org.jgroups.stack.IpAddress;
import org.jgroups.stack.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

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
            channel.setReceiver(receiver);
            channel.connect(properties.getChannelName());
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
        log.info("Shutdown requested.");
        channel.disconnect();
        channel.close();
        isConnected = false;
        log.info("Channel closed.");
    }


    public void updateInitialHosts(List<String> hosts, List<Integer> ports) {
        Protocol protocol = channel.getProtocolStack().findProtocol(TCPPING.class);
        if (protocol == null) {
            log.error("Error: Cannot find TCPPING protocol. Cannot update JGroups initial host list."); // DEBUG
            return;
        }

        TCPPING tcpping = (TCPPING) protocol;

        // NOTE: A TCPPING port_range of 10 for host 10.0.2.100 will result in the following initial host entries:
        // { 10.0.2.100:7800, 10.0.2.100:7801, 10.0.2.100:7802, 10.0.2.100:7803, 10.0.2.100:7804, 10.0.2.100:7805,
        //   10.0.2.100:7806, 10.0.2.100:7807, 10.0.2.100:7808, 10.0.2.100:7809, 10.0.2.100:7810 }
        int portRange = tcpping.getPortRange();

        List<IpAddress> ipAddressList = new ArrayList<>();
        for (int i = 0; i < hosts.size(); i++) {
            for (int j = 0; j <= portRange; j++) {
                try {
                    IpAddress ipAddress = new IpAddress(hosts.get(i), ports.get(i) + j);
                    ipAddressList.add(ipAddress);
                } catch (UnknownHostException e) {
                    log.error("Unknown host: \"" + hosts.get(i) + ":" + (ports.get(i) + j) + "\".");
                }
            }
        }

        List<PhysicalAddress> initialHostList = tcpping.getInitialHosts();
        log.info("TCPPING initial host list: " + initialHostList); // DEBUG

        // Only add to the initial hosts list. Removing from it may cause strange behaviors.

        ipAddressList.removeAll(initialHostList); // only new nodes are left
        initialHostList.addAll(ipAddressList);    // add only new nodes

        // NOTE: JGroups peer discovery is handled by MERGE2 at a period that is randomized
        // between min_interval and max_interval. New nodes will not be immediately available.
    }
}
