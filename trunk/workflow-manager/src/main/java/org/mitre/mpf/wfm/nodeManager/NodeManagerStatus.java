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

package org.mitre.mpf.wfm.nodeManager;

import org.apache.commons.lang3.tuple.Pair;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.jgroups.Address;
import org.mitre.mpf.mvc.controller.AtmosphereController;
import org.mitre.mpf.mvc.model.AtmosphereChannel;
import org.mitre.mpf.nms.*;
import org.mitre.mpf.nms.NodeManagerConstants.States;
import org.mitre.mpf.nms.streaming.messages.StreamingJobExitedMessage;
import org.mitre.mpf.wfm.businessrules.StreamingJobRequestBo;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobStatus;
import org.mitre.mpf.wfm.enums.StreamingJobStatusType;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NodeManagerStatus implements ClusterChangeNotifier {

    private static final int VIEW_UPDATE_CHECK_TIME_MILLISEC = 500;
    private static final int VIEW_UPDATE_MAX_WAIT_TIME_MILLISEC = 60_000;

    private static final Logger log = LoggerFactory.getLogger(NodeManagerStatus.class);

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Autowired
    private MasterNode masterNode;

    @Autowired
    private StreamingJobRequestBo streamingJobRequestBo;

    // flag that indicates if at least one view update was initiated by JGroups
    private boolean viewUpdated = false;

    private volatile boolean isRunning = false;

    private Map<String, ServiceDescriptor> serviceDescriptorMap = new ConcurrentHashMap<>();


    public void init(boolean reloadConfig) {
        if (!reloadConfig) {
            masterNode.setCallback(this);
            masterNode.run();
            isRunning = true;
        }

        try (InputStream inStream = propertiesUtil.getNodeManagerConfigResource().getInputStream()) {
            if (masterNode.loadConfigFile(inStream, propertiesUtil.getAmqUri())) {
                if (!reloadConfig && !masterNode.areAllManagersPresent()) {
                    waitForViewUpdate();
                } else {
                    log.info("All known node managers are available.");
                }
                masterNode.launchAllNodes();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        updateServiceDescriptors();
    }

    private void waitForViewUpdate() {
        // Wait until our view is updated by JGroups so we know the current cluster membership.
        // This is necessary to avoid a race condition that may happen if child node managers are already running
        // but JGroups has yet to report that they are available.
        int cumulativeWaitTimeMillisec = 0;
        try {
            if (!viewUpdated) {
                log.info("Waiting up to " + VIEW_UPDATE_MAX_WAIT_TIME_MILLISEC + " milliseconds for cluster view update ...");
            }
            while (!viewUpdated && cumulativeWaitTimeMillisec < VIEW_UPDATE_MAX_WAIT_TIME_MILLISEC) {
                log.debug("Time spent waiting so far: " + cumulativeWaitTimeMillisec + " milliseconds");
                Thread.sleep(VIEW_UPDATE_CHECK_TIME_MILLISEC);
                cumulativeWaitTimeMillisec += VIEW_UPDATE_CHECK_TIME_MILLISEC;
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for cluster view update.");
            Thread.currentThread().interrupt();
        }

        if (cumulativeWaitTimeMillisec > 0) {
            log.info("Waited a total of " + cumulativeWaitTimeMillisec + " milliseconds for cluster view update.");
        }

        if (!viewUpdated) {
            log.warn("Cluster view has not updated yet. Proceeding anyway. This may result in failure to launch " +
                    "services on nodes that are not available or cannot be identified.");
        } else {
            log.info("Cluster view updated. Proceeding.");
        }
    }

    public void stop() {
        try {
            masterNode.shutdown();
            isRunning = false;
        }
        catch(Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void updateServiceDescriptors() {
        if(masterNode != null) {
            serviceDescriptorMap = new ConcurrentHashMap<>();
            for (ServiceDescriptor sd : masterNode.getServices()) {
                if(sd.getLastKnownState() != States.Delete && sd.getLastKnownState() != States.DeleteInactive) {
                    updateServiceDescriptorEntry(sd);
                }
            }
        }
    }

    //comment - if this throws an exception, JGroups goes bonkers - Unprocessed ServiceStatusUpdate state
    private void updateServiceDescriptorEntry(ServiceDescriptor desc) {
        synchronized (serviceDescriptorMap) {
            this.serviceDescriptorMap.put(desc.getName(), desc);
            log.debug("updated ServiceDescriptor with name: {}", desc.getName());
        }
    }

    //TODO: It is possible for the master node to keep track of the available node managers, which will
    //prevent having to generate a list each time
    //TODO: could also just return the address as soon as a match to the service descriptor is found to reduce
    //a small amount of processing time
    private Map<String, Address> getCurrentNodeManagerHostsAddressMap() {
        Map<String, Address> availableNodeManagerHostsAddressMap = new HashMap<String, Address>();
        for (Address addr : masterNode.getCurrentNodeManagerHosts()) {
            String name = addr.toString();
            //If it's a node manager then track it, it's name contains the machine name upon which it resides
            Pair<String, NodeTypes> hostNodeTypePair = AddressParser.parse(addr);
            if (hostNodeTypePair == null) {
                continue;
            }
            //see if we know what type this is
            if(hostNodeTypePair.getRight() == NodeTypes.NodeManager) {
                String mgrHost = hostNodeTypePair.getLeft();
                availableNodeManagerHostsAddressMap.put(mgrHost, addr);
            }
        }
        return availableNodeManagerHostsAddressMap;
    }

    private void sendMessageToNodeManager(ServiceDescriptor desc, States requestedServiceState) {
        BaseServiceLauncher launcher = BaseServiceLauncher.getLauncher(desc);
        if(launcher != null) {
            String descriptorHost = desc.getHost();

            Map<String, Address> availableNodeManagerHostsAddressMap = getCurrentNodeManagerHostsAddressMap();
            if(!availableNodeManagerHostsAddressMap.isEmpty()) {
                //get correct node manager address
                Address correctNodeManagerAddress = availableNodeManagerHostsAddressMap.get(descriptorHost);

                if(requestedServiceState == States.Launching) {
                    //send launch message to that address - Launching = asked to start
                    masterNode.send(correctNodeManagerAddress, desc, requestedServiceState);
                }
                else if(requestedServiceState == States.ShuttingDownNoRestart) {
                    //send shut down message to that address - ShuttingDown - requesting to go down from NodeManager source
                    masterNode.send(correctNodeManagerAddress, desc, requestedServiceState);
                } else {
                    log.debug("requestedServiceState of {} is not accepted - no message will be sent", requestedServiceState);
                }
            } else {
                log.warn("No recovered node managers - cannot request state {} of the service with name: {}",
                        requestedServiceState, desc.getName());
            }
        } else {
            log.warn("Launcher is null - cannot request state {} of the service with name: {}",
                    requestedServiceState, desc.getName());
        }
    }

    private void shutdown(ServiceDescriptor desc) {
        if (desc.isAlive()) {
            sendMessageToNodeManager(desc, States.ShuttingDownNoRestart);
        } else {
            log.debug("Service with name, {}, is not alive - no need to send a shutdown request", desc.getName());
        }
    }

    private void launch(ServiceDescriptor desc) {
        if (!desc.isAlive()) {
            sendMessageToNodeManager(desc, States.Launching);
        } else {
            log.debug("Service with name, {}, is already alive - no need to send a launch request", desc.getName());
        }
    }

    /** broadcasts service events via Atmosphere */
    private void broadcast( ServiceDescriptor service, String event )
    {
        HashMap<String,Object> datamap = new HashMap<String,Object>();
        datamap.put( "name", service.getName() );
        datamap.put( "lastKnownState", service.getLastKnownState() );
        datamap.put( "host", service.getHost() );
        datamap.put( "event", event );
        AtmosphereController.broadcast( AtmosphereChannel.SSPC_SERVICE, event, datamap );
    }

    /** broadcasts service events via Atmosphere */
    private void broadcast( String serviceName, String event )
    {
        HashMap<String,Object> datamap = new HashMap<String,Object>();
        datamap.put( "name", serviceName );
        datamap.put( "event", event );
        AtmosphereController.broadcast( AtmosphereChannel.SSPC_SERVICE, event, datamap );
    }

    @Override
    public void viewUpdated(boolean forced) {
        if (!forced) {
            log.debug("Cluster view updated.");
            viewUpdated = true;
        }
    }

    @Override
    public void newManager(String hostname) {
        log.debug("{} manager has started.", hostname);
        //go ahead and launch anything that is able to launch (nothing that starts with a state of Delete or InactiveNoStart)
        masterNode.launchAllNodes();
    }

    @Override
    public void managerDown(String hostname) {
        //log.debug("{} manager down.", hostname);
    }

    @Override
    public void newService(ServiceDescriptor service) {
        updateServiceDescriptorEntry(service);
        broadcast( service, "OnNewService" );
        log.info("adding new service: {}", service.getName());
    }

    @Override
    public void serviceDown(ServiceDescriptor service) {
        updateServiceDescriptorEntry(service);
        broadcast( service, "OnServiceDown" );
        log.info("{} has shut down.", service.getName());
    }

    @Override
    public void serviceChange(ServiceDescriptor service) {
        updateServiceDescriptorEntry(service);
        broadcast( service, "OnServiceChange" );
        //log.debug("{} has changed.", service.getName());
    }

    public Map<String, ServiceDescriptor> getServiceDescriptorMap() {
        //should not have to update on get because of the update on when changed - but that does not seem to work
        updateServiceDescriptors(); //TODO: a solution for now - not much added processing
        return serviceDescriptorMap;
    }

    public boolean shutdownService(String serviceName) {
        if(serviceDescriptorMap.containsKey(serviceName)) {
            log.info("{} is shutting down.", serviceName);
            shutdown(serviceDescriptorMap.get(serviceName));
            return true;
        }
        //no else needed
        log.error("Cannot shut down service with name '{}' because it does not exist.", serviceName);
        return false;
    }

    public boolean startService(String serviceName) {
        if(serviceDescriptorMap.containsKey(serviceName)) {
            launch(serviceDescriptorMap.get(serviceName));
            log.info("{} is starting.", serviceName);
            return true;
        }
        //no else needed
        log.error("Cannot start service with name '{}' because it does not exist.", serviceName);
        return false;
    }

    public Map<String, Boolean> getConfiguredManagerHosts() {
        return masterNode.getConfiguredManagerHosts();
    }

    public void reloadNodeManagerConfig() {
        log.info("Reloading the node manager config");
        Split split = SimonManager.getStopwatch("org.mitre.mpf.wfm.nodeManager.NodeManagerStatus.reloadNodeManagerConfig").start();
        init(true);
        split.stop();
    }

    @Override
    public void serviceReadyToRemove(ServiceDescriptor serviceDescriptor) {
        log.info("The service '{}' has been shut down and is ready to be removed.", serviceDescriptor.getName());
        synchronized (serviceDescriptorMap) {
            //other nodes may continue to keep the desc in their service table or map, but it is not necessary
            //once in this state
            serviceDescriptorMap.remove(serviceDescriptor.getName());
            broadcast( serviceDescriptor, "OnServiceReadyToRemove" );
        }
    }


    @Override
    public void streamingJobExited(StreamingJobExitedMessage message) {
        StreamingJobStatus status;
        log.info("Streaming job {} exited due to {}.", message.jobId, message.reason);
        switch (message.reason) {
            case CANCELLED:
                status = new StreamingJobStatus(StreamingJobStatusType.CANCELLED, message.reason.detail);
                break;
            case STREAM_STALLED:
                status = new StreamingJobStatus(StreamingJobStatusType.TERMINATED, message.reason.detail);
                break;
            default:
                status = new StreamingJobStatus(StreamingJobStatusType.ERROR, message.reason.detail);
        }
        streamingJobRequestBo.handleJobStatusChange(message.jobId, status, System.currentTimeMillis());
    }


    public void updateInitialHosts(List<String> hosts, List<Integer> ports) {
        masterNode.updateInitialHosts(hosts, ports);
    }
}
