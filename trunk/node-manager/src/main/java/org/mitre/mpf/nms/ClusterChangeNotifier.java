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

import org.mitre.mpf.nms.streaming.messages.StreamingJobExitedMessage;

public interface ClusterChangeNotifier {

    /**
     * Called every time the current view of the network is updated.
     *
     * @parm forced true if JChannel.getView() was used to force an update;
     *              otherwise false if the update was initiated by JGroups
     */
    public void viewUpdated(boolean forced);

    /**
     * Called when a new node-manager appears on the network
     *
     * @param hostname
     */
    public void newManager(String hostname);

    /**
     * Called when a known node-manager is shutdown
     *
     * @param hostname
     */
    public void managerDown(String hostname);

    /**
     * Called when a new processing service (process) appears on the network
     *
     * @param service
     */
    public void newService(ServiceDescriptor service);

    /**
     * Called when an existing service (process) on a node is shutdown
     *
     * @param service
     */
    public void serviceDown(ServiceDescriptor service);

    /**
     * Called when an existing service (process) on a node changes state somehow (e.g. launching or running)
     *
     * @param service
     */
    public void serviceChange(ServiceDescriptor service);

    /**
     * Called when an existing service (process) has been removed from the node manager config via the master node, 
     * has been shut down, and ready to be removed from the service table if desired
     *
     * @param serviceDescriptor
     */
    public void serviceReadyToRemove(ServiceDescriptor serviceDescriptor);

    /**
     * Called when a streaming job process exits.
     *
     * @param message
     */
    public void streamingJobExited(StreamingJobExitedMessage message);
}
