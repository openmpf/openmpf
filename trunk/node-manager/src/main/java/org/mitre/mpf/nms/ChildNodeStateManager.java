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
import org.jgroups.Message;
import org.mitre.mpf.nms.streaming.messages.StreamingJobMessage;
import org.mitre.mpf.nms.streaming.ChildStreamingJobManager;
import org.mitre.mpf.nms.util.PropertiesUtil;
import org.mitre.mpf.nms.util.SleepUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChildNodeStateManager extends ChannelReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(ChildNodeStateManager.class);


    private final PropertiesUtil propertiesUtil;

    private final ChildStreamingJobManager streamingJobManager;

    private final Map<String, BaseServiceLauncher> launchedAppsMap = new HashMap<>();


    @Autowired
    public ChildNodeStateManager(PropertiesUtil propertiesUtil, ChannelNode channelNode,
                                 ChildStreamingJobManager streamingJobManager) {
        super(propertiesUtil, channelNode);
        this.propertiesUtil = propertiesUtil;
        this.streamingJobManager = streamingJobManager;
    }


    @Override
    public void receive(Message msg) {
        Object obj = msg.getObject();   // actual payload, we'll figure out what it is in a sec
        Address sender = msg.getSrc();  // handle so we can send a response back (if desired)

        //log.info("Received message from {}", sender);
	    if (obj instanceof StreamingJobMessage) {
	       streamingJobManager.handle((StreamingJobMessage) obj);
        }
        else if (obj instanceof ServiceStatusUpdate) {
            LOG.debug("Received a ServiceStatusUpdate from {}", sender);
            ServiceStatusUpdate ssu = (ServiceStatusUpdate) obj;
            ServiceDescriptor sd = ssu.getServiceDescriptor();
            String name = ssu.getServiceName();
            LOG.debug("Message received by address: {} for service descriptor with name: {}",
                    getMessageChannel().getAddress(), name);
            //System.err.println("\tReceived  " + sd.getLastKnownState().toString() + " state for " + sd.getName());

            synchronized (getServiceTable()) {
                // Odd network latencies can yield out-of-order state changes where a JGroups observer sees
                // the request after the result. So, a service might go from LAUNCHING to RUNNING, but we could
                // get the messages as RUNNING followed by LAUNCHING.  Trap these and don't permit reversals.

                ServiceDescriptor tableService = getServiceTable().get(name);
                if (tableService != null
                        && (tableService.getLastKnownState().ordinal() - ssu.getLastKnownState().ordinal()) == 1) {
                    // Bad dog.  A fast node manager acted on a request and got its state change to us before we
                    // even saw the original (master) request to do it.  Don't act on old data.
                    // @TODO: fix this
                    LOG.warn("Fast node: the state of the service '{}' changed too fast!. Went from '{}' to '{}' with ordinal difference of '{}'",
                            name, tableService.getLastKnownState(), ssu.getLastKnownState(),
                            tableService.getLastKnownState().ordinal() - ssu.getLastKnownState().ordinal());
                } else {
                    getServiceTable().put(name, sd);
                }
            }

            if (sd.doesHostMatch(propertiesUtil.getThisMpfNode())) {
                // one of ours set it up correctly
                switch (sd.getLastKnownState()) {
                    case Delete:
                        LOG.debug("Processing a shutdown and delete request for {} from {} ", sd.getFullyQualifiedName(), sender);
                        shutdown(sd, true, false);
                        break;
                    case ShuttingDown:
                        LOG.debug("Processing a shutdown request for {} from {} ", sd.getFullyQualifiedName(), sender);
                        shutdown(sd, false, false);
                        break;
                    case ShuttingDownNoRestart:
                        LOG.debug("Processing a shutdown with no restart request for {} from {} ", sd.getFullyQualifiedName(), sender);
                        shutdown(sd, false, true);
                        break;
                    case Launching:
                        LOG.debug("Processing a launch request for {} from {} ", sd.getFullyQualifiedName(), sender);
                        launch(sd);
                        break;
                }
            }
        } else if (obj instanceof NodeStatusUpdate) {
            //TODO: another node manager may want to remove (nodeTable.remove) a node descriptor from its node table
            //this would be the case if another node was set to delete inactive by the master node in a multi node scenario
            NodeStatusUpdate msu = (NodeStatusUpdate) obj;
            LOG.debug("Received a ManagerStatusUpdate: {} for {} ", msu.getLastKnownState(), msu.getHostName());

            String name = msu.getHostName();
            if(msu.getLastKnownState() == NodeManagerConstants.States.DeleteInactive) {
                synchronized (getNodeTable()) {
                    LOG.info("removing node with name '{}'", name);
                    getNodeTable().remove(name);
                }
            } else {
                synchronized (getNodeTable()) {
                    getNodeTable().put(name, msu.getTheNode());
                }

                // If we are not already deemed running, tell the world that indeed we are
                if (msu.getLastKnownState() != NodeManagerConstants.States.Running
                        && msu.getTheNode().doesHostMatch(propertiesUtil.getThisMpfNode())) {
                    updateState(msu.getTheNode(), NodeManagerConstants.States.Running);
                }
            }
        }
    }


    /**
     * Shutdown a service as described in the NodeStatusUpdate object
     *
     * @param desc - What we need to shutdown
     */
    public void shutdown(ServiceDescriptor desc, boolean markAsDelete, boolean noStartOnConfigChange) {
        // lookup node by name, remove from table, perform other actions (or let object do its own cleanup)
        BaseServiceLauncher theApp;
        synchronized (launchedAppsMap) {
            theApp = launchedAppsMap.remove(desc.getFullyQualifiedName());
        }
        if (theApp != null) {
            //make sure the state does not go back to Inactive if removed from the config!
            theApp.shutdown();
            //TODO: this logic should be improved
            if (noStartOnConfigChange) {
                updateState(desc, NodeManagerConstants.States.InactiveNoStart);
            } else {
                NodeManagerConstants.States newState = markAsDelete
                        ? NodeManagerConstants.States.DeleteInactive
                        : NodeManagerConstants.States.Inactive;
                updateState(desc, newState);
            }
        }
    }

    /**
     * Launch a node as described in the NodeStatusUpdate object
     *
     * @param desc - What we need to start
     */
    private void launch(ServiceDescriptor desc) {
        boolean error = false;
        synchronized (launchedAppsMap) {
            if (launchedAppsMap.containsKey(desc.getFullyQualifiedName())) {
                return;
            }

            // create and launch a node of the specified type with the given name
            BaseServiceLauncher launcher = BaseServiceLauncher.getLauncher(desc);
            if (launcher != null) {
                // if it's startable then hold onto it
                if (launcher.startup(propertiesUtil.getMinServiceUpTimeMillis())) {
                    launchedAppsMap.put(launcher.getServiceName(), launcher);
                    updateState(desc, NodeManagerConstants.States.Running);
                    LOG.debug("Sending {} state for {}", NodeManagerConstants.States.Running, desc.getFullyQualifiedName());
                } else {
                    LOG.error("Could not launch: {} at path: {}", desc.getFullyQualifiedName(), desc.getService().getCmdPath());
                    error = true;
                }
            } else {
                LOG.error("Could not create launcher for: {} at path: {}", desc.getFullyQualifiedName(),
                        desc.getService().getCmdPath());
                error = true;
            }
        }

        if (error) {
            // Give time for things to propagate
            SleepUtil.interruptableSleep(2000);
            desc.setFatalIssueFlag(true);
            updateState(desc, NodeManagerConstants.States.Inactive);
        }
    }

    /**
     * Every few seconds, review the list of running apps and log their count.
     * If one goes offline, and it was previously active, then notify the
     * cluster as INACTIVE
     */
    public void run() {
        // We are running - tell the world
        NodeDescriptor mgr = new NodeDescriptor(propertiesUtil.getThisMpfNode());
        updateState(mgr, NodeManagerConstants.States.Running);

        // Now that we are all up and running and syncd with the cluster, see if we had been tasked
        // with any launches that we weren't there to do.
        synchronized (getServiceTable()) {
            for (ServiceDescriptor sd : getServiceTable().values()) {
                if (sd.doesStateMatch(NodeManagerConstants.States.Launching)
                        && sd.doesHostMatch(propertiesUtil.getThisMpfNode())) {
                    // @todo: offline when messages came? If we went away the state of these would be running!
                    // System.err.println("Launching a previously setup node: " + sd.getName());
                    LOG.debug("Launching a previously setup node: " + sd.getFullyQualifiedName());
                    // one of ours set it up correctly
                    launch(sd);
                }
            }
        }

        // Every so often, log what has gone offline and a tally of what is running, but only when things change
        int lastRunning = -1;
        List<String> toDelete = new ArrayList<>();

        while (isConnected()) {
            SleepUtil.interruptableSleep(2000);

            synchronized (launchedAppsMap) {
                int running = launchedAppsMap.size();
                for (BaseServiceLauncher n : launchedAppsMap.values()) {
                    ServiceDescriptor sd = getServiceTable().get(n.getServiceName());

                    if (null == sd) {
                        LOG.warn("Missing launched service: {}", n.getServiceName());
                        continue;
                    }

                    // If a process we are responsible for is no longer running, notify the world, then mark it for
                    // deletion from our list of managed processes.  Don't delete in mid enumeration.
                    if (n.runToCompletion()) {
                        LOG.debug("{} has gone offline", n.getServiceName());
                        toDelete.add(n.getServiceName());
                        sd.setFatalIssueFlag(n.getFatalProblemFlag());
                        //mark as InactiveNoStart here if the fatal issue flag is set! will prevent that service from
                        //starting on node manger config being saved or the worklow manager webapp being restarted
                        if (n.getFatalProblemFlag()) {
                            updateState(sd, NodeManagerConstants.States.InactiveNoStart);
                        } else {
                            updateState(sd, NodeManagerConstants.States.Inactive);
                        }
                        running--;
                        // Otherwise, just keep track of any restarts the process is going through
                    } else if (n.getRestartCount() > sd.getRestarts()) {
                        LOG.debug("{} has a new restart count of {}", n.getServiceName(), n.getRestartCount());
                        sd.setRestarts(n.getRestartCount());
                        updateState(sd, sd.getLastKnownState());
                    }
                }
                // Anything that terminated (restart != true) has to be cleaned up outside the iterator above
                for (String name : toDelete) {
                    launchedAppsMap.remove(name);
                }
                toDelete.clear();

                if (running != lastRunning) {
                    LOG.info("At this point, there are {} running nodes", running);
                }
                lastRunning = running;
            }
        }
    }
}
