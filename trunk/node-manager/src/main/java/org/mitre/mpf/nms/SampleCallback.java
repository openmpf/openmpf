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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.mitre.mpf.nms.xml.Service;


/**
 * Handle for each application node in group.  These are known (expected) as defined in the config file and
 * as the Master directs the Mgrs to create.  They are also discovered after they have been created and have
 * joined the group.  Thus, these are objects are created before discovery but are noted to exist (isAlive) once
 * discovered.
 */

@Component
public class SampleCallback implements ClusterChangeNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(SampleCallback.class);

    @Override
    public void newManager(String hostname) {
        LOG.debug("[SampleCallback] New node manager: {}", hostname);
    }

    @Override
    public void managerDown(String hostname) {
        LOG.debug("[SampleCallback] Node manager down: {}", hostname);
    }

    @Override
    public void newService(ServiceDescriptor desc) {
        Service s = desc.getService();
        LOG.debug("[SampleCallback] NEW " + desc.getName() + " (" + s.getLauncher() + ") " +
                "\"" + s.getCmdPath() + " " + s.getArgumentsString() + "\" " +
                desc.getLastKnownState());
    }

    public void serviceDown(ServiceDescriptor desc) {       
        Service s = desc.getService();
        LOG.debug("[SampleCallback] DIE " + desc.getName() + " (" + s.getLauncher() + ") " +
                "\"" + s.getCmdPath() + " " + s.getArgumentsString() + "\" " +
                desc.getLastKnownState());
    }

    public void serviceChange(ServiceDescriptor desc) {
        Service s = desc.getService();
        LOG.debug("[SampleCallback] CHG " + desc.getName() + " (" + s.getLauncher() + ") " +
                "\"" + s.getCmdPath() + " " + s.getArgumentsString() + "\" " +
                desc.getLastKnownState());
    }

	@Override
	public void serviceReadyToRemove(ServiceDescriptor desc) {
        Service s = desc.getService();
        LOG.debug("[SampleCallback] DEL " + desc.getName() + " (" + s.getLauncher() + ") " +
                "\"" + s.getCmdPath() + " " + s.getArgumentsString() + "\" " +
                desc.getLastKnownState());
	}
}
