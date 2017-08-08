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
import java.io.Serializable;


/**
 * Handle for each {@link ChannelReceiver} node in JGroup.  These are known (expected) as defined in the config file and
 * as the Master directs the Mgrs to create.  They are also discovered after they have been created and have
 * joined the group.  Thus, these are objects are created before discovery but are noted to exist (isAlive) once
 * discovered.
 */
public class NodeDescriptor implements Serializable {
    private String hostname;
    private Address jAddr = null;
    private int minServiceTimeupMillis;
    private  NodeManagerConstants.States lastKnownState =  NodeManagerConstants.States.Unknown;

    public  NodeDescriptor(String hostname) {
        this.hostname = hostname;
    }

    public String getHostname() {
        return hostname;
    }

    public boolean isAlive() {
        return (NodeManagerConstants.States.Running == lastKnownState);
    }

    public  NodeManagerConstants.States getLastKnownState() {
        return lastKnownState;
    }

    public void setLastKnownState( NodeManagerConstants.States lastKnownState) {
        this.lastKnownState = lastKnownState;
    }

    public boolean doesHostMatch(String host) {
        return (hostname.compareTo(host) == 0);
    }

	public int getMinServiceTimeupMillis() {
		return minServiceTimeupMillis;
	}

	public void setMinServiceTimeupMillis(int minServiceTimeupMillis) {
		this.minServiceTimeupMillis = minServiceTimeupMillis;
	}
}
