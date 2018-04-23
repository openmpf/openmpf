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
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.mitre.mpf.nms.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.joining;

@Controller
public abstract class ChannelReceiver extends ReceiverAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelReceiver.class);

    // the interface to JGroups
    private final ChannelNode msgChannel;

    private final PropertiesUtil propertiesUtil;

    // key is FQN of the service: for example, mpf1:Markup:1
    private final Map<String, ServiceDescriptor> serviceTable = new ConcurrentHashMap<>();
    // key is target (logical) host name: mpf1
    private final Map<String, NodeDescriptor> nodeTable = new ConcurrentHashMap<>();

    private ClusterChangeNotifier notifier;          // For callbacks when changing node states


    protected ChannelReceiver(PropertiesUtil propertiesUtil, ChannelNode channelNode) {
        this.propertiesUtil = propertiesUtil;
        msgChannel = channelNode;
    }

    /**
     * a notifier will have methods called when the cluster state changes
     *
     * @param notifier
     */
    public void setCallback(ClusterChangeNotifier notifier) {
        this.notifier = notifier;
    }

    public ClusterChangeNotifier getCallback() {
        return this.notifier;
    }

    /**
     * Return system state to an external requester
     */
    @Override
    public void getState(OutputStream output) throws Exception {
        LOG.debug("Responding to cluster state request");

        synchronized (serviceTable) {
            org.jgroups.util.Util.objectToStream(serviceTable, new DataOutputStream(output));
        }
        synchronized (nodeTable) {
            org.jgroups.util.Util.objectToStream(nodeTable, new DataOutputStream(output));
        }
    }

    /**
     * Request state
     *
     * @param input
     * @throws Exception
     */
    @Override
    public void setState(InputStream input) throws Exception {
        LOG.debug("Setting cluster state");

        synchronized (serviceTable) {
            Map newMap = (Map) org.jgroups.util.Util.objectFromStream(new DataInputStream(input));
            serviceTable.clear();
            serviceTable.putAll(newMap);
        }
        synchronized (nodeTable) {
            Map newMap = (Map) org.jgroups.util.Util.objectFromStream(new DataInputStream(input));
            nodeTable.clear();
            nodeTable.putAll(newMap);
        }
    }

    @Override
    public void viewAccepted(View view) {
        handleView(view, false);
    }

    private void handleView(View view, boolean forced) {
        // What is currently out there
        String participants = view.getMembers().stream()
                .map(Object::toString)
                .collect(joining(" "));
        LOG.debug("Current Participants: {}", participants);

        // First step, compare the view of node-manager members to our list of node-manager states built over time
        // The view can contain both NodeManagers AND nodes.  One must iterate through the members is this current view
        // to determine what is new and what may have gone away.
        Map<String, Boolean> managersInView = new HashMap<>();
        for (Address addr : view.getMembers()) {
            String name = addr.toString();
            LOG.info("Cluster view accepted from {}", name);
            // If it's a node manager then track it, it's name contains the machine name upon which it resides
            Pair<String, NodeTypes> hostNodeTypePair = AddressParser.parse(addr);
            if (hostNodeTypePair == null) {
                continue;
            }
            switch (hostNodeTypePair.getRight()) {
                case NodeManager:
                    String mgrHost = hostNodeTypePair.getLeft();
                    managersInView.put(mgrHost, Boolean.TRUE);

                    // Normally, managers are known in advance by masters after loading an XML configuration file
                    // It could happen that someone starts up a manager elsewhere on the network that isn't enumerated
                    // Also, we have to consider that we've got one stopping/restarting over its lifecycle
                    NodeDescriptor mgr = nodeTable.get(mgrHost);
                    if (mgr == null) {
                        mgr = new NodeDescriptor(mgrHost);
                        if (!mgr.doesHostMatch(propertiesUtil.getThisMpfNode())) { // don't warn about self
                            LOG.warn("New node-manager is online that wasn't preconfigured. Rogue?");
                        }
                        // Issue the callback
                        if (notifier != null) {
                            notifier.newManager(mgr.getHostname());
                        }
                    }
                    mgr.setLastKnownState(NodeManagerConstants.States.Running);
                    synchronized (nodeTable) {
                        nodeTable.put(mgrHost, mgr);
                    }
                    break;
                case MasterNode:
                    LOG.debug("Received view from MasterNode {}", name);
                    break;
                case ReceiverNode:
                    LOG.debug("Received view from a generic receiver node {}", name);
                    break;
                default:
                    // shouldn't be here!
                    LOG.warn("view from unknown type {}", name);
            }
        }

        // Second step. Is there anything we've historically known about that has suddenly disappeared
        synchronized (nodeTable) {
            for (Entry<String, NodeDescriptor> entry : nodeTable.entrySet()) {
                NodeManagerConstants.States currentStatus = entry.getValue().getLastKnownState();

                // Have to think hard now.  If a manager was RUNNING and then disappears, all its launched
                // children had to die with it.  Note this.
                if (managersInView.get(entry.getKey()) == null
                        && currentStatus == NodeManagerConstants.States.Running) {
                    entry.getValue().setLastKnownState(NodeManagerConstants.States.Inactive);

                    // All the nodes on that manager that had been running must be dead if the manager is gone
                    synchronized (serviceTable) {
                        for (ServiceDescriptor service : serviceTable.values()) {
                            if (service.doesHostMatch(entry.getValue().getHostname())
                                    && service.doesStateMatch(NodeManagerConstants.States.Running)) {
                                service.setLastKnownState(NodeManagerConstants.States.Inactive);
                                if (notifier != null) {
                                    notifier.serviceDown(service);
                                }
                            }
                        }
                    }
                    LOG.error("Node-manager offline on " + entry.getKey());
                    if (notifier != null) {
                        notifier.managerDown(entry.getValue().getHostname());
                    }
                }
            }
        }

        super.viewAccepted(view);

        if (notifier != null) {
            notifier.viewUpdated(forced);
        }
    }


    @Override
    public abstract void receive(Message msg);


    /**
     * Returns a set of currently <b>known</b> {@link ChannelReceiver} names
     * (logical addresses)
     * <br/>
     * This might be inactive (down) nodes.
     *
     * @return
     */
    public Set<String> getCurrentNodes() {
        // don't bother sync'ing
        return this.nodeTable.keySet();
    }

    public Set<String> getRunningNodes() {
        // no need to have conncurrent hashmap
        Set<String> rn = new HashSet<>();
        for (Entry<String, NodeDescriptor> e : this.nodeTable.entrySet()) {
            if (e.getValue().getLastKnownState() == NodeManagerConstants.States.Running) {
                rn.add(e.getKey());
            }
        }
        return rn;
    }


    public boolean isConnected() {
        return msgChannel.isConnected();
    }

    public ChannelNode getMessageChannel() {
        return msgChannel;
    }

    /**
     * 
     * @return true if no longer connected (false if still connected).
     */
    public boolean shutdown() {
        this.msgChannel.shutdown();
        return !isConnected();
    }


    protected void updateState(ServiceDescriptor service, NodeManagerConstants.States status) {
        service.setLastKnownState(status);
        msgChannel.broadcast(new ServiceStatusUpdate(service));
    }

    protected void updateState(Address address, ServiceDescriptor service, NodeManagerConstants.States status) {
        service.setLastKnownState(status);
        msgChannel.send(address, new ServiceStatusUpdate(service));
    }


    protected void updateState(NodeDescriptor nodeDesc, NodeManagerConstants.States status) {
        nodeDesc.setLastKnownState(status);
        msgChannel.broadcast(new NodeStatusUpdate(nodeDesc));
    }


    protected Map<String, ServiceDescriptor> getServiceTable() {
        return serviceTable;
    }

    protected Map<String, NodeDescriptor> getNodeTable() {
        return nodeTable;
    }

    protected ClusterChangeNotifier getNotifier() {
        return notifier;
    }

    public void startReceiving(NodeTypes nodeType, String description) {
        // build fqn
        if (null == description) {
            description = "";
        }
        if (null == nodeType) {
            nodeType = NodeTypes.ReceiverNode;
        }
        String fqn = AddressParser.createFqn(nodeType, propertiesUtil.getThisMpfNode(), description);

        LOG.debug("{} starting up", fqn);
        // this connects us to the jgroups channel defined, we are now live and ready for comm
        msgChannel.connect(fqn, this);

        handleView(msgChannel.getView(), true);
    }
}
