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

public class NodeManagerConstants {
    //public static final String NODE_TYPES   = "nodeTypes";

    // These are the strings that should be used for node types in config file and elsewhere
  /*  public static final String GENERIC_TYPE = "Generic";
    public static final String TRACKER_TYPE = "Tracker";
    public static final String DETECTOR_TYPE = "Detector";*/

    public static enum ServiceTypes {
        Generic, Tracker, Detector, Unknown;
    }

    // String matching is used with these later, so make sure they are unique and not subsets of one another
    // public static final String NODE_MANAGER_TAG = "NodeManager";  // all node managers shall have this as part of their name
    //public static final String MASTER_MANAGER_TAG = "MasterManager";  // all master controllers will have this in their name
    // @FIXME Pick one set
    //public static final String MASTER_TAG       = "MasterNode";
    /**
     * JGroup Node Types
     *
     * public static enum NodeTypes { NodeManager, MasterNodeManager }
     */
    public static final String AMQ_HOSTNAME_PARAM = "activeMqHostname";
    public static final String JGROUPS_CONFIG_PARAM = "jgroupsConfig";

    public static final String DEFAULT_CHANNEL = "MPF_Channel";

    /**
     * Broadcast states of Services.
     *
     * <pre>
     * Configured - we have config only but no other indication of presence
     * Launching - asked to start
     * Running - ready to go
     * ShuttingDown - requesting to go down
     * ShuttingDownNoRestart - requesting to go down and then be set to InactiveNoRestart
     * Inactive - currently no longer active after being told to shutdown
     * InactiveNoStart - currently no longer active after being told to shutdown and not allowed to start again if changes are made
     * Failed - crashed
     * Unknown - never setup in any way (default)
     * Delete - shut down and set to DeleteInactive
     * DeleteInactive - the service is now shut down and ready to be removed from the service table if desired
     * </pre>
     */
    public static enum States {

        Unknown, Configured, Launching, Running, ShuttingDown, ShuttingDownNoRestart, Inactive, InactiveNoStart, Delete, DeleteInactive

    }
}
