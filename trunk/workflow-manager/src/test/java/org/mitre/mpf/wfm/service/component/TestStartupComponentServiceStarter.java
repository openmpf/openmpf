/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.rest.api.node.NodeManagerModel;
import org.mitre.mpf.rest.api.node.ServiceModel;
import org.mitre.mpf.wfm.service.NodeManagerService;
import org.mitre.mpf.wfm.util.PropertiesUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import static org.mitre.mpf.test.TestUtil.collectionContaining;
import static org.mockito.Mockito.*;

public class TestStartupComponentServiceStarter {

	private StartupComponentServiceStarterImpl _serviceStarter;

	private PropertiesUtil _mockPropsUtils;

	private NodeManagerService _mockNodeManagerSvc;

	private static final String _testMpfNodeHostName = "somehost.localdomain";


	private void initTest(int numStartUpSvcs) {
		initTest(numStartUpSvcs, Arrays.asList(createAutoConfiguredNodeManagerModel("fakehost1"),
						                       createAutoConfiguredNodeManagerModel("fakehost2"),
						                       createAutoConfiguredNodeManagerModel(_testMpfNodeHostName)));
	}

	private void initTest(int numStartUpSvcs, List<NodeManagerModel> nodeManagerModels) {
		_mockPropsUtils = mock(PropertiesUtil.class);
		when(_mockPropsUtils.isNodeAutoConfigEnabled())
				.thenReturn(true);
		when(_mockPropsUtils.getNodeAutoConfigNumServices())
				.thenReturn(numStartUpSvcs);

		_mockNodeManagerSvc = mock(NodeManagerService.class);
		when(_mockNodeManagerSvc.getNodeManagerModels())
				.thenReturn(nodeManagerModels);

		_serviceStarter = new StartupComponentServiceStarterImpl(_mockPropsUtils, _mockNodeManagerSvc);
	}

	private static NodeManagerModel createAutoConfiguredNodeManagerModel(String hostname) {
		NodeManagerModel nodeManagerModel = new NodeManagerModel(hostname);
		nodeManagerModel.setAutoConfigured(true);
		return  nodeManagerModel;
	}

	@Test
	public void testHappyPath() {
		initTest(2);
		List<RegisterComponentModel> components = createDefaultRegistrationModels();
		setExistingSvcModels(components);

		_serviceStarter.startServicesForComponents(components);
		components.forEach(rcm -> assertServiceStarted(rcm, 2));
		assertServiceStarted("Markup", 2);
	}


	@Test
	public void doesNotAddServicesWhenNumSvcsIsZero() {
		initTest(0);
		List<RegisterComponentModel> components = createDefaultRegistrationModels();
		setExistingSvcModels(components);

		_serviceStarter.startServicesForComponents(components);
		assertNoServicesStarted();
	}


	@Test
	public void doesNotAddSvcWhenNoExistingSvcModels() throws IOException {
		initTest(1);
		when(_mockNodeManagerSvc.getServiceModels())
				.thenReturn(Collections.emptyMap());

		_serviceStarter.startServicesForComponents(createDefaultRegistrationModels());
		assertNoServicesStarted();
	}

	@Test
	public void onlyStartsMarkupWhenNoComponentsGiven() throws IOException {
		initTest(2);
		setExistingSvcModels("OtherComponent1", "OtherComponent2");
		_serviceStarter.startServicesForComponents(Collections.emptyList());
		assertServiceStarted("Markup", 2);

		verify(_mockNodeManagerSvc)
				.saveAndReloadNodeManagerConfig(collectionContaining(
						nm -> nm.getHost().equals(_testMpfNodeHostName)
								&& nm.getServices().size() == 1 && containsSvc("Markup", 2, nm)));
	}


	@Test
	public void doesNotStartExistingServicesIfNotOneOfPassedInComponents() {
		initTest(2);
		String componentName1 = "Component1";
		String componentName2 = "Component2";
		String existingComponentName = "ExistingComponent";

		List<RegisterComponentModel> components = createRegistrationModels(componentName1, componentName2);
		setExistingSvcModels(componentName1, componentName2, existingComponentName);
		_serviceStarter.startServicesForComponents(components);

		assertServiceStarted(componentName1, 2);
		assertServiceStarted(componentName2, 2);
		assertServiceNotStarted(existingComponentName);
	}


	@Test
	public void modifySvcCountForAutoConfiguredNodes() {
		ServiceModel serviceModel1 = new ServiceModel();
		serviceModel1.setServiceName("StartMe1");

		ServiceModel serviceModel2 = new ServiceModel();
		serviceModel2.setServiceName("StartMe2");
		serviceModel2.setServiceCount(3);

		ServiceModel serviceModel3 = new ServiceModel();
		serviceModel3.setServiceName("StartMe3");
		serviceModel3.setServiceCount(10);

		NodeManagerModel nodeManagerModel1 = createAutoConfiguredNodeManagerModel("fakehost1");

		NodeManagerModel nodeManagerModel2 = createAutoConfiguredNodeManagerModel("fakehost2");
		nodeManagerModel2.getServices().add(serviceModel1);
		nodeManagerModel2.getServices().add(serviceModel2);
		nodeManagerModel2.getServices().add(serviceModel3);

		NodeManagerModel nodeManagerModel3 = new NodeManagerModel(_testMpfNodeHostName);
		nodeManagerModel3.getServices().add(serviceModel1);
		nodeManagerModel3.getServices().add(serviceModel2);
		nodeManagerModel3.getServices().add(serviceModel3);

		initTest(5, Arrays.asList(nodeManagerModel1, nodeManagerModel2, nodeManagerModel3));

		List<RegisterComponentModel> components = createRegistrationModels(serviceModel1.getServiceName(),
				                                                           serviceModel2.getServiceName(),
				                                                           serviceModel3.getServiceName());

		when(_mockNodeManagerSvc.getServiceModels())
				.thenReturn(ImmutableMap.of(serviceModel1.getServiceName(), serviceModel1,
						                    serviceModel2.getServiceName(), serviceModel2,
						                    serviceModel3.getServiceName(), serviceModel3));

		_serviceStarter.startServicesForComponents(components);

		assertServiceStarted(nodeManagerModel1.getHost(), serviceModel1.getServiceName(), 5);
		assertServiceStarted(nodeManagerModel1.getHost(), serviceModel2.getServiceName(), 5);
		assertServiceStarted(nodeManagerModel1.getHost(), serviceModel3.getServiceName(), 5);

		assertServiceStarted(nodeManagerModel2.getHost(), serviceModel1.getServiceName(), 5);
		assertServiceStarted(nodeManagerModel2.getHost(), serviceModel2.getServiceName(), 5);
		assertServiceStarted(nodeManagerModel2.getHost(), serviceModel3.getServiceName(), 5);

		assertServiceStarted(nodeManagerModel3.getHost(), serviceModel1.getServiceName(), serviceModel1.getServiceCount());
		assertServiceStarted(nodeManagerModel3.getHost(), serviceModel2.getServiceName(), serviceModel2.getServiceCount());
		assertServiceStarted(nodeManagerModel3.getHost(), serviceModel3.getServiceName(), serviceModel3.getServiceCount());
	}

	@Test
	public void doesNotAddDuplicateServiceToNode() throws IOException {
		initTest(2);

		List<RegisterComponentModel> components = createDefaultRegistrationModels();
		Map<String, ServiceModel> serviceModels = setExistingSvcModels(components);

		NodeManagerModel nodeManager = createAutoConfiguredNodeManagerModel(_testMpfNodeHostName);
		nodeManager.getServices().add(serviceModels.get(components.get(0).getServiceName()));
		nodeManager.getServices().add(serviceModels.get("Markup"));

		int initialNumServices = nodeManager.getServices().size();
		int numComponentsToStart = components.size();

		when(_mockNodeManagerSvc.getNodeManagerModels())
				.thenReturn(Arrays.asList(createAutoConfiguredNodeManagerModel("fakehost1"), nodeManager));

		_serviceStarter.startServicesForComponents(components);

		components.forEach(rcm -> assertServiceStarted(rcm, 2));
		assertServiceStarted("Markup", 2);

		int numDuplicateServices = 1;
		int expectedNumServices = initialNumServices + numComponentsToStart - numDuplicateServices;
		verify(_mockNodeManagerSvc)
				.saveAndReloadNodeManagerConfig(collectionContaining(
						n -> n.getHost().equals(_testMpfNodeHostName)
								&& n.getServices().size() == expectedNumServices));
	}


	private void assertNoServicesStarted() {
		try {
			verify(_mockNodeManagerSvc, never())
					.setServiceModels(any());

			verify(_mockNodeManagerSvc, never())
					.saveAndReloadNodeManagerConfig(any());

			verify(_mockNodeManagerSvc, never())
					.saveAndReloadNodeManagerConfig(any());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}


	private void assertServiceStarted(String svcName, int numInstances) {
		assertServiceStarted(_testMpfNodeHostName, svcName, numInstances);
	}

	private void assertServiceStarted(String hostname, String svcName, int numInstances) {
		try {
			verify(_mockNodeManagerSvc)
					.saveAndReloadNodeManagerConfig(collectionContaining(
							nm -> nm.getHost().equals(hostname) && containsSvc(svcName, numInstances, nm)));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}


	private void assertServiceNotStarted(String svcName) {
		try {
			verify(_mockNodeManagerSvc, never())
					.saveAndReloadNodeManagerConfig(collectionContaining(
							nm -> nm.getHost().equals(_testMpfNodeHostName) && containsSvc(svcName, nm)));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}

	}


	private void assertServiceStarted(RegisterComponentModel component, int numInstances) {
		assertServiceStarted(component.getServiceName(), numInstances);
	}


	private static boolean containsSvc(String svcName, NodeManagerModel nm) {
		return nm.getServices().stream().anyMatch(sm -> sm.getServiceName().equals(svcName));
	}

	private static boolean containsSvc(String svcName, int numInstances, NodeManagerModel nm) {
		return nm.getServices().stream().anyMatch(
				sm -> sm.getServiceName().equals(svcName) && sm.getServiceCount() == numInstances);
	}


	private Map<String, ServiceModel> setExistingSvcModels(String... names) {
		Map<String, ServiceModel> servicesMap = new HashMap<>();
		for (String name : names) {
			ServiceModel service = new ServiceModel();
			service.setServiceName(name);
			servicesMap.put(name, service);
		}
		ServiceModel markupSvc = new ServiceModel();
		markupSvc.setServiceName("Markup");
		servicesMap.put("Markup", markupSvc);

		when(_mockNodeManagerSvc.getServiceModels())
				.thenReturn(servicesMap);
		return servicesMap;
	}


	private Map<String, ServiceModel> setExistingSvcModels(Collection<RegisterComponentModel> components) {
		String[] names = components.stream()
				.map(RegisterComponentModel::getServiceName)
				.toArray(String[]::new);
		return setExistingSvcModels(names);
	}



	private static List<RegisterComponentModel> createRegistrationModels(String... names) {
		List<RegisterComponentModel> results = new ArrayList<>();
		for (String name : names) {
			RegisterComponentModel rcm = new RegisterComponentModel();
			rcm.setServiceName(name);
			results.add(rcm);
		}
		return results;
	}


	private static List<RegisterComponentModel> createDefaultRegistrationModels() {
		return createRegistrationModels("Component1", "Component2", "Component3");
	}
}
