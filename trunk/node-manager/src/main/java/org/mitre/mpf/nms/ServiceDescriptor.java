/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Arrays;
import static org.mitre.mpf.nms.ChannelReceiver.FQN_SEP;

import org.mitre.mpf.nms.NodeManagerConstants.ServiceTypes;
import org.mitre.mpf.nms.xml.Service;


/**
 * Handle for each application node in group.  These are known (expected) as defined in the config file and
 * as the Master directs the Mgrs to create.  They are also discovered after they have been created and have
 * joined the group.  Thus, these are objects are created before discovery but are noted to exist (isAlive) once
 * discovered.
 */

@Component
public class ServiceDescriptor implements Serializable {
    private int mRestarts = 0;      // number of times restarted?
    private Service mService;
    private String mTargetHost;     // machine hostname where this app runs
    private int mRank;              // used to build the FQN to keep track of possible multiple services with the same name on the same node
    private String mFQN;
    private String activeMqHost = "";
    private boolean fatalIssueFlag = false;
    private NodeManagerConstants.States lastKnownState = NodeManagerConstants.States.Unknown;
    private long startTimeMillis = 0;
  

    public ServiceDescriptor() {}

    /**
     * Builds a Full Qualified Unique Name 
     * <br/>
     * <pre>
     * targetHost:serviceName:rank
     * </pre>
     * @param service
     * @param target
     * @param rank 
     */
    public ServiceDescriptor(Service service, String target, int rank) {
        this.mService = service;
        this.mTargetHost = target;
        this.mRank = rank;
        this.mFQN = target + FQN_SEP + this.mService.getName() + FQN_SEP + rank;
    }
    
    /**
     * Returns Fully Qualified Unique Name.
     * @return 
     */
    public String getName() {
        return this.mFQN;
    }
    
    public String getHost() {
        return this.mTargetHost;
    }
    
    public Service getService() {
        return this.mService;
    }

    /**
     * Trims the underlining service name (removes white-spaces) before comparing.
     * 
     * @return 
     */
    public ServiceTypes getServiceType() {
        try {
            return ServiceTypes.valueOf(this.mService.getName().trim());
        } catch (IllegalArgumentException iae) {
            return ServiceTypes.Unknown;
        }
    }
    
    public int getRank() {
        return this.mRank;
    }

    public boolean isAlive() {
        return (NodeManagerConstants.States.Running == lastKnownState);
    }

    @Override
    public String toString() {
        String args = Arrays.toString(mService.getArgs().toArray(new String[]{}));
        return "Name: " + mService.getName() + " Host: " +  this.mTargetHost + " Launcher: " + mService.getLauncher() + " App: \"" + mService.getCmdPath() + " " + args + "\"";
    }

    public String getActiveMqHost() {
        return activeMqHost;
    }

    public void setActiveMqHost(String activeMqHost) {
        this.activeMqHost = activeMqHost;
    }

    public NodeManagerConstants.States getLastKnownState() {
        return lastKnownState;
    }

    public void setLastKnownState(NodeManagerConstants.States lastKnownState) {
        this.lastKnownState = lastKnownState;
    }

    public boolean doesStateMatch(NodeManagerConstants.States state) {
        return (lastKnownState.compareTo(state) == 0);
    }

    public boolean doesHostMatch(String host) {
        return (mTargetHost.compareTo(host) == 0);
    }

    public void setRestarts(int restarts) { this.mRestarts = restarts;; };

    public int getRestarts() { return this.mRestarts; };

    public void setFatalIssueFlag(boolean flag) { this.fatalIssueFlag = flag;};

    public boolean getFatalIssueFlag() {return this.fatalIssueFlag;}

	public long getStartTimeMillis() { return startTimeMillis;}

	public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }
}

