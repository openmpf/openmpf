/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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
import org.mitre.mpf.nms.NodeManagerConstants.States;
import org.mitre.mpf.nms.streaming.messages.LaunchStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StopStreamingJobMessage;
import org.mitre.mpf.nms.util.SleepUtil;
import org.mitre.mpf.nms.xml.NodeManager;
import org.mitre.mpf.nms.xml.NodeManagers;
import org.mitre.mpf.nms.xml.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.WritableResource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.*;

@Component
public class MasterNode {

    private static final Logger log = LoggerFactory.getLogger(MasterNode.class);

    private final Map<String, Boolean> configuredManagerHosts = new HashMap<>();

    private final MasterNodeStateManager nodeStateManager;


    public static MasterNode create() {
        try (ClassPathXmlApplicationContext context
                     = new ClassPathXmlApplicationContext("applicationContext-nm.xml"))  {
            return context.getBean(MasterNode.class);
        }
    }


    @Autowired
    MasterNode(MasterNodeStateManager nodeStateManager) {
        this.nodeStateManager = nodeStateManager;
    }


    public void run() {
        nodeStateManager.startReceiving(NodeTypes.MasterNode, "MPF-MasterNode");
    }


    /**
     * Determine if all the known node-managers are running
     *
     * @return false if any one is not currently running
     */
    public boolean areAllManagersPresent() {
        synchronized (nodeStateManager.getNodeTable()) {
            return nodeStateManager.getNodeTable().values().stream()
                    .allMatch(NodeDescriptor::isAlive);
        }
    }

    /**
     * Determine if all the known nodes are running
     *
     * @return false if any one is not currently running
     */
    public boolean areAllNodesPresent() {
        synchronized (nodeStateManager.getServiceTable()) {
            return nodeStateManager.getServiceTable().values().stream()
                    .allMatch(ServiceDescriptor::isAlive);
        }
    }

    /**
     * Parse the node manager configuration file and determine the hostnames of machines we'll need to contact
     * (via a node manager process on that machine) to send instructions for managing services.
     * This doesn't apply the configuration.
     *
     * @param nodeManagerConfig
     * @param autoUnconfigNodes
     * @return List<NodeManager>
     */
    public synchronized List<NodeManager> loadConfigFile(WritableResource nodeManagerConfig, boolean autoUnconfigNodes) {
        // Don't let the config file have multiple node-managers with the same hostname/IP
        // This is only used in this code area to prevent collisions due to bad XMl configs
        configuredManagerHosts.clear();

        NodeManagers managers;
        try (InputStream inputStream = nodeManagerConfig.getInputStream()){
            log.info("Loading node manager config.");
            managers = NodeManagers.fromXml(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // Iterate through node manager servers, dropping those that are invalid
        for (NodeManager manager : managers.getAll()) {
            if (manager.getTarget() == null) {
                log.error("The <nodeManager> tag did not contain a target attribute describing the server to use.");
                continue;
            }
            // If user misconfigures the xml config by putting in duplicate hostnames, complain and continue
            if (configuredManagerHosts.containsKey(manager.getTarget())) {
                log.error("Duplicate node-manager specified in config file. Dropping repeated node manager host: " + manager.getTarget());
                continue;
            }
            if (autoUnconfigNodes && manager.isAutoConfigured()) {
                log.info("Node auto-unconfiguration is enabled. Dropping auto-configured node manager host: " + manager.getTarget());
                continue;
            }

            // Note that we've seen this node manager host from the current config file
            configuredManagerHosts.put(manager.getTarget(), false); // configuration not applied yet
        }

        boolean updated = managers.getAll().removeIf(node -> !configuredManagerHosts.containsKey(node.getTarget()));

        // Save the configuration if node manager servers have been dropped
        if (updated) {
            try (OutputStream outputStream = nodeManagerConfig.getOutputStream()){
                log.info("Saving updated node manager config.");
                NodeManagers.toXml(managers, outputStream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return managers.getAll();
    }

    /**
     * Apply the configuration captured in the state of the provided node manager list. Instantiate a NodeDescriptor
     * for each node and ServiceDescriptor for each service. This may send updates to node manager processes.
     *
     * @param managers
     * @param activeMqBrokerUri
     */
    public void configureNodes(List<NodeManager> managers, String activeMqBrokerUri) {
        //creating a unique list of service descriptor names to compare to the service table
        List<String> serviceNamesFromConfig = new ArrayList<>();

        // Iterate through node manager servers
        for (NodeManager manager : managers) {

            // Tell the world about this manager we have configured (until we hear from it, designate it CONFIGURED)
            // If it already exists, the master must be restarting, don't recreate it.
            synchronized (nodeStateManager.getNodeTable()) {
                NodeDescriptor mgr = nodeStateManager.getNodeTable().get(manager.getTarget()); // see if it exists already
                if (mgr == null) {
                    mgr = new NodeDescriptor(manager.getTarget());
                    nodeStateManager.getNodeTable().put(manager.getTarget(), mgr);
                    nodeStateManager.updateState(mgr, States.Configured);
                    log.info("Node descriptor created for expected (not yet discovered) NodeManager: " + mgr.getHostname());
                } else if(!mgr.isAlive()){
                    nodeStateManager.updateState(mgr, States.Configured);
                    log.info("An existing node descriptor running at '{}' will be updated. It is part of the config and was not running.", mgr.getHostname());
                }
            }

            // Note that we've seen this node-manager host from the current config file
            configuredManagerHosts.put(manager.getTarget(), true);

            if(CollectionUtils.isEmpty(manager.getServices())) {
                log.warn("no services present in the node at target {}", manager.getTarget());
                continue;
            }

            // Configure the nodes under this node-manager
            for (Service serviceFromConfig : manager.getServices()) {
                for (int rank = 1; rank <= serviceFromConfig.getCount(); ++rank) {
                    ServiceDescriptor descriptorFromConfig = new ServiceDescriptor(serviceFromConfig, manager.getTarget(), rank);
                    descriptorFromConfig.setActiveMqHost(activeMqBrokerUri);

                    serviceNamesFromConfig.add(descriptorFromConfig.getFullyQualifiedName());

                    // We note it immediately and then tell the world
                    synchronized (nodeStateManager.getServiceTable()) {
                        ServiceDescriptor serviceTableDescriptor = nodeStateManager.getServiceTable().get(descriptorFromConfig.getFullyQualifiedName());
                        if (serviceTableDescriptor == null) {
                            log.debug("Updating config for {}", descriptorFromConfig.getFullyQualifiedName());
                            nodeStateManager.updateState(descriptorFromConfig, States.Configured);
                        } else {
                            //TODO: might want to consider more states here
                            if(serviceTableDescriptor.getLastKnownState() == NodeManagerConstants.States.InactiveNoStart) {
                                log.debug("Will not update the existing config for {} because the state is {}", descriptorFromConfig.getFullyQualifiedName(), serviceTableDescriptor.getLastKnownState());
                            } else if(serviceTableDescriptor.getLastKnownState() == NodeManagerConstants.States.Running) {
                                log.debug("Not updating existing config for {} because it is already running", descriptorFromConfig.getFullyQualifiedName());
                            } else {
                                //If service was DeleteInactive it should still be set back to configured because the config
                                //needs to overwrite existing service table information
                                log.debug("Updating existing config for {}", descriptorFromConfig.getFullyQualifiedName());
                                nodeStateManager.updateState(descriptorFromConfig, States.Configured);
                            }
                        }
                    }
                }
            }
        } //end of for (NodeManager manager : managers)

        //if the node table is not empty - go through it and check to make sure
        //any updates to the node manager config are reflected in the node table
        synchronized (nodeStateManager.getNodeTable()) {
            Iterator<NodeDescriptor> nodeIter = nodeStateManager.getNodeTable().values().iterator();
            while (nodeIter.hasNext()) {
                NodeDescriptor node = nodeIter.next();
                if (!configuredManagerHosts.containsKey(node.getHostname()) && node.getLastKnownState() != States.DeleteInactive) {
                    log.debug("Node with name '{}' is no longer present in the config", node.getHostname());
                    nodeStateManager.updateState(node, States.DeleteInactive);
                    log.debug("removing node with name '{}' from the node table", node.getHostname());
                    nodeIter.remove();
                }
            }
        }

        //if the service table is not empty - go through it and check to make sure
        //any updates to the node manager config are reflected in the service table
        synchronized (nodeStateManager.getServiceTable()) {
            Iterator<ServiceDescriptor> servicesIter = nodeStateManager.getServiceTable().values().iterator();
            while (servicesIter.hasNext()) {
                ServiceDescriptor service = servicesIter.next();
                if (!serviceNamesFromConfig.contains(service.getFullyQualifiedName())
                        && service.getLastKnownState() != States.DeleteInactive) {
                    log.debug("Service with name '{}' is part of the service table, but no longer part of the node manager config.",
                            service.getFullyQualifiedName());
                    nodeStateManager.updateState(service, States.Delete);
                    log.debug("removing service with name '{}' from the service table", service.getFullyQualifiedName());
                    servicesIter.remove();
                }
            }
        }

        // Give time for things to propagate
        SleepUtil.interruptableSleep(3000);
    }

    public void shutdown() {
        nodeStateManager.shutdownAllServices();
        nodeStateManager.shutdown();
    }


    /**
     * Go through each node (in hashed order) and tell the responsible
     * NodeManager to launch them.
     */
    public void launchAllNodes() {
    	log.debug("Launch all nodes called");
        nodeStateManager.launchAllNodes();
    }


    /**
     * Get a map containing the current hosts configured
     */
    public Map<String, Boolean> getConfiguredManagerHosts() {
    	return this.configuredManagerHosts;
    }


    public void setCallback(ClusterChangeNotifier notifier) {
        nodeStateManager.setCallback(notifier);
    }


    public List<Address> getCurrentNodeManagerHosts() {
        return nodeStateManager.getMessageChannel().getView().getMembers();
    }


    public void send(Address address, ServiceDescriptor descriptor, NodeManagerConstants.States state) {
        nodeStateManager.updateState(address, descriptor, state);
    }


    public Collection<ServiceDescriptor> getServices() {
        return Collections.unmodifiableCollection(nodeStateManager.getServiceTable().values());
    }


    public void startStreamingJob(LaunchStreamingJobMessage message) {
        nodeStateManager.startStreamingJob(message);
    }

    public void stopStreamingJob(StopStreamingJobMessage message) {
        nodeStateManager.stopStreamingJob(message);
    }


    public Set<String> getAvailableNodes() {
        return nodeStateManager.getAvailableNodes();
    }
}
