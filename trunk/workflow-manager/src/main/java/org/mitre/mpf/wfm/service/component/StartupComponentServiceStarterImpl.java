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

package org.mitre.mpf.wfm.service.component;


import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.rest.api.node.ServiceModel;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.util.PropertiesUtil;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Named
public class StartupComponentServiceStarterImpl implements StartupComponentServiceStarter {

	private final int _numStartUpServices;

	private final String _thisMpfNodeHostName;

	private final NodeManagerService _nodeManagerService;


	@Inject
	public StartupComponentServiceStarterImpl(
			PropertiesUtil propertiesUtil,
			NodeManagerService nodeManagerService) {
		_numStartUpServices = propertiesUtil.getNumStartUpServices();
		_thisMpfNodeHostName = propertiesUtil.getThisMpfNodeHostName();
		_nodeManagerService = nodeManagerService;
	}


	@Override
	public void startServicesForComponents(List<RegisterComponentModel> components) {
		if (_numStartUpServices <= 0 || _thisMpfNodeHostName == null) {
			return;
		}

		Map<String, ServiceModel> allServiceModels = _nodeManagerService.getServiceModels();
		List<ServiceModel> servicesToStart = getServicesToStart(components, allServiceModels);
		if (servicesToStart.isEmpty()) {
			return;
		}

		for (ServiceModel service : servicesToStart) {
			if (service.getServiceCount() < _numStartUpServices) {
				service.setServiceCount(_numStartUpServices);
			}
		}

		_nodeManagerService.setServiceModels(allServiceModels);

		List<NodeManagerModel> allNodes = new ArrayList<>(_nodeManagerService.getNodeManagerModels());
		NodeManagerModel thisNode = getThisNodeModel(allNodes);
		addServicesToNode(thisNode, servicesToStart);
		try {
			_nodeManagerService.saveNodeManagerConfig(allNodes);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}


	private NodeManagerModel getThisNodeModel(Collection<NodeManagerModel> nodes) {
		Optional<NodeManagerModel> thisNodeModel = nodes.stream()
				.filter(n -> _thisMpfNodeHostName.equals(n.getHost()))
				.findAny();

		if (thisNodeModel.isPresent()) {
			return thisNodeModel.get();
		}
		else {
			NodeManagerModel thisNode = new NodeManagerModel(_thisMpfNodeHostName);
			nodes.add(thisNode);
			return thisNode;
		}
	}

	private static final List<String> SERVICES_TO_ALWAYS_START = Collections.singletonList("Markup");

	private static List<ServiceModel> getServicesToStart(
			Collection<RegisterComponentModel> components,
			Map<String, ServiceModel> allServiceModels) {

		Stream<String> componentServiceNames = components.stream()
				.map(RegisterComponentModel::getServiceName)
				.filter(Objects::nonNull);

		return Stream.concat(componentServiceNames, SERVICES_TO_ALWAYS_START.stream())
				.map(allServiceModels::get)
				.filter(Objects::nonNull)
				.collect(toList());
	}


	private static void addServicesToNode(NodeManagerModel node, Iterable<ServiceModel> servicesToStart) {
		Map<String, ServiceModel> existingServices = node.getServices()
				.stream()
				.collect(toMap(ServiceModel::getServiceName, s -> s));

		for (ServiceModel service : servicesToStart) {
			ServiceModel existingService = existingServices.get(service.getServiceName());
			if (existingService == null) {
				node.getServices().add(service);
			}
			else {
				existingService.setServiceCount(service.getServiceCount());
			}
		}
	}

}
