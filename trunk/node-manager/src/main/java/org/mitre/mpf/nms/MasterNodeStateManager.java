/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import org.jgroups.Message;
import org.mitre.mpf.nms.streaming.MasterStreamingJobManager;
import org.mitre.mpf.nms.streaming.messages.LaunchStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StopStreamingJobMessage;
import org.mitre.mpf.nms.streaming.messages.StreamingJobExitedMessage;
import org.mitre.mpf.nms.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class MasterNodeStateManager extends ChannelReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(MasterNodeStateManager.class);

    private final MasterStreamingJobManager streamingJobManager;


    @Autowired
    public MasterNodeStateManager(PropertiesUtil propertiesUtil, ChannelNode channelNode,
                                  MasterStreamingJobManager streamingJobManager) {
        super(propertiesUtil, channelNode);
        this.streamingJobManager = streamingJobManager;
    }


    @Override
    public void receive(Message msg) {
        Object obj = msg.getObject();
        if (obj instanceof StreamingJobExitedMessage) {
            StreamingJobExitedMessage exitMessage = (StreamingJobExitedMessage) obj;
            streamingJobManager.streamingJobExited(exitMessage);
            getNotifier().streamingJobExited(exitMessage);
        }
        else if (obj instanceof ServiceStatusUpdate) {
            ServiceStatusUpdate nsu = (ServiceStatusUpdate) obj;
            //System.out.println("I received a node status update: " + nsu.getNodeName() + " : " + nsu.getTheNode().getLastKnownState());

            // Replace our node view (basically, only an internal state will change)
            String name = nsu.getServiceName();
            NodeManagerConstants.States state = nsu.getLastKnownState();
            synchronized (getServiceTable()) {
                // Odd network latencies can yield out-of-order state changes where a JGroups observer sees
                // the request after the result. So, a service might go from LAUNCHING to RUNNING, but we could
                // get the messages as RUNNING followed by LAUNCHING.  Trap these and don't permit reversals.

                if (getServiceTable().get(name) != null
                        && (getServiceTable().get(name).getLastKnownState().ordinal() - nsu.getLastKnownState().ordinal()) == 1) {
                    // Bad dog.  A fast node manager acted on a request and got its state change to us before we
                    // even saw the original (master) request to do it.  Don't act on old data.
                } else {
                    getServiceTable().put(name, nsu.getServiceDescriptor());
                }
            }
            // Two things get notified, going to a running state and going to a non-running state
            // Intermediate stuff (Launching) is transitory
            if (null != getNotifier() && null != state) {
                switch (state) {
                    case Running:
                        //This is a real running state, so mark it
                        getNotifier().newService(getServiceTable().get(name));
                        break;
                    case Inactive:
                        //Reverting back to a non-working state, mark it - the state of a normal shutdown
                        //example: WFM goes down, all Running services go to the 'Inactive' state
                        getNotifier().serviceDown(getServiceTable().get(name));
                        break;
                    case InactiveNoStart:
                        //The final state of a shutdown from the WFM web application
                        getNotifier().serviceDown(getServiceTable().get(name));
                        break;
                    case DeleteInactive:
                        //Service has been shut down and is ready to be removed from the service table if desired
                        //this is the final state of a service that has been removed from the MasterNode's config
                        //Delete is not handled here because it should be handled once it reaches 'DeleteInactive'
                        getNotifier().serviceReadyToRemove(getServiceTable().get(name));
                        break;
                    default:
                        LOG.debug("Unprocessed ServiceStatusUpdate state of '{}' for service '{}'.", state, name);
                }
            }
        } else if (obj instanceof NodeStatusUpdate) {
            NodeStatusUpdate msu = (NodeStatusUpdate) obj;
            //System.out.println("I received a manager status update of " + msu.getHostName() + " " + msu.getLastKnownState());

            String name = msu.getHostName();
            NodeManagerConstants.States state = msu.getLastKnownState();
            synchronized (getNodeTable()) {
                getNodeTable().put(name, msu.getTheNode());
            }
            if (null != getNotifier() && null != state) {
                switch (state) {
                    case Running:
                        // This is a real running state, so mark it
                        getNotifier().newManager(name);
                        break;
                    case Inactive:
                        // Reverting back to a non-working state, mark it
                        getNotifier().managerDown(name);
                        break;
                    case DeleteInactive:
                        // Node has been shut down and is ready to be removed from the node table if desired
                        getNotifier().managerDown(name);
                        break;
                    default:
                        LOG.debug("Unprocessed Manager StatusUpdate state: {} ", state);
                }
            }
        }
    }


    public void launchAllNodes() {
        LOG.debug("Launch all nodes called");
        synchronized (getServiceTable()) {
            getServiceTable().values().forEach(this::launchNode);
        }
    }


    private boolean launchNode(ServiceDescriptor sd) {
        NodeDescriptor mgr = getNodeTable().get(sd.getHost());

        // If we don't know about this manager yet or it is defunct, launching won't work.  Note this.
        if (mgr != null) {
            if(!mgr.isAlive()) {
                //should not make it here if DeleteInactive, but still want to check
                if(mgr.getLastKnownState() == NodeManagerConstants.States.DeleteInactive) {
                    LOG.warn("The NodeManager at hostname '{}' was previously removed from the config and should be removed from the node table", mgr.getHostname());
                    getNodeTable().remove(mgr.getHostname());
                } else {
                    LOG.warn("The NodeManager at hostname '{}' is not running and is in state '{}'.", mgr.getHostname(), mgr.getLastKnownState());
                    LOG.warn("The service with name '{}' cannot be launched.", sd.getFullyQualifiedName());
                }
                return false;
            }
        } else {
            LOG.warn("NodeManager at host '{}' does not exist in the node table and its services should be removed", sd.getHost());
            LOG.warn("The service with name '{}' will not be launched", sd.getFullyQualifiedName());
            return false;
        }

        NodeManagerConstants.States lastKnownState = sd.getLastKnownState();
        //the service is not alive and currently not already in a launching (asked to start) state
        if (!sd.isAlive() && lastKnownState != NodeManagerConstants.States.Launching) {
            if(lastKnownState == NodeManagerConstants.States.InactiveNoStart ||
                    lastKnownState == NodeManagerConstants.States.DeleteInactive ||
                    lastKnownState == NodeManagerConstants.States.Delete) { //also ignoring Delete! - there have been cases where the node manager
                //has not processed the Delete request before getting to this point! - TODO: prevent Delete from jumping to Launching
                //using a better method, the same goes for other states changes - use the ordinal subtraction method found elsewhere in the node manager source!
                LOG.debug("Will not start the service '{}', the current state is '{}'.", sd.getFullyQualifiedName(), lastKnownState);
                //could clean up nodes from the service table in this state if desired
                return false;
            } else {
                LOG.debug("State prior to launching {} on {} is {}: ", sd.getFullyQualifiedName(), sd.getHost(), lastKnownState);
                updateState(sd, NodeManagerConstants.States.Launching);
                LOG.debug("Launching {} on {}: ", sd.getFullyQualifiedName(), sd.getHost());
                return true;
            }
        } else {
            LOG.debug("Not relaunching {} on {} - (already running or asked to start)", sd.getFullyQualifiedName(), sd.getHost());
            return false;
        }
    }

    /**
     * Shutdown a specific service in the cluster.
     */
    private void shutdownService(ServiceDescriptor service) {
        // identify the owning NodeManagering the service and send it a msg
        LOG.debug("Shutting down {}: ", service.getFullyQualifiedName());

        // Notify the world that we want this node dead if it is running, or just mark as INACTIVE if not
        // The second case exists if there was never a node manager to start it.  We don't want it lingering
        // in some kind of LAUNCHING or CONFIGURED state with no-one to ever act on it.
        if (service.doesStateMatch(NodeManagerConstants.States.Running)) {
            updateState(service, NodeManagerConstants.States.ShuttingDown);
            //If the state is already InactiveNoStart or DeleteInactive we don't want it to be set to Inactive
        } else if( !service.doesStateMatch(NodeManagerConstants.States.InactiveNoStart)
                && !service.doesStateMatch(NodeManagerConstants.States.DeleteInactive) ){
            updateState(service, NodeManagerConstants.States.Inactive);
        }
        //TODO: could remove DeleteInactive in this method!!
    }

    /**
     * Go through the list of all nodes in the cluster and send their
     * NodeManager a shutdown request. Note that shutdown requests are just
     * that. The nodeTable hash will eventually indicate the disposition of the
     * nodes.
     */
    public void shutdownAllServices() {
        synchronized (getServiceTable()) {
            LOG.info("Shutting down all the services currently running: {}", getServiceTable().size());
            getServiceTable().values().forEach(this::shutdownService);
        }

        streamingJobManager.stopAllJobs();

        // do we expect replies, like Inactive
        try {
            Thread.sleep(10000); // shutdown takes some time - let the messages get out
        } catch (InterruptedException e) {
            LOG.error("Received interrupt during shutdown. Ignoring interrupt since already shutting down", e);
            Thread.currentThread().interrupt();
        }
    }


    public void startStreamingJob(LaunchStreamingJobMessage launchMessage) {
        streamingJobManager.startJob(launchMessage, getRunningNodes());
    }


    public void stopStreamingJob(StopStreamingJobMessage message) {
        streamingJobManager.stopJob(message);
    }
}
