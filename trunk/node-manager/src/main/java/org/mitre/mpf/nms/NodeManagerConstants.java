/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

    public static enum ServiceTypes {
        Generic, Tracker, Detector, Unknown;
    }


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
     * Unknown - never setup in any way (default)
     * Delete - shut down and set to DeleteInactive
     * DeleteInactive - the service is now shut down and ready to be removed from the service table if desired
     * </pre>
     */
    public static enum States {

        Unknown, Configured, Launching, Running, ShuttingDown, ShuttingDownNoRestart, Inactive, InactiveNoStart, Delete, DeleteInactive

    }
}
