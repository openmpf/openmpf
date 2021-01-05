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

package org.mitre.mpf.wfm.service;

import org.mitre.mpf.nms.xml.Service;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.rest.api.node.ServiceModel;
import org.mitre.mpf.wfm.util.Tuple;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface NodeManagerService {

    public List<NodeManagerModel> getNodeManagerModels();

    // this method is used by the ComponentRegistrationController but should not be
    // publicly exposed as part of the REST API
    public boolean addService(Service service);

    // this method is used by the ComponentRegistrationController but should not be
    // publicly exposed as part of the REST API
    public Tuple<Boolean, String> removeService(Service service);

    public Tuple<Boolean, String> removeService(String serviceName);

    public Map<String, ServiceModel> getServiceModels();

    public boolean setServiceModels(Map<String, ServiceModel> nodeManagerFilePaletteMap);

    public boolean saveAndReloadNodeManagerConfig(List<NodeManagerModel> nodeManagerModels) throws IOException;

    public Set<String> getCoreNodes();

    public boolean isCoreNode(String host);

    public Set<String> getAvailableNodes();

    public void autoConfigureNewNode(String host) throws IOException;

    public void unconfigureIfAutoConfiguredNode(String host) throws IOException;
}
