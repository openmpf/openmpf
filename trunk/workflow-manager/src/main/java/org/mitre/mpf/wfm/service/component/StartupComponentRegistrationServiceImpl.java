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


package org.mitre.mpf.wfm.service.component;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.mitre.mpf.rest.api.component.ComponentState;
import org.mitre.mpf.rest.api.component.RegisterComponentModel;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static java.util.stream.Collectors.*;

@Named
public class StartupComponentRegistrationServiceImpl implements StartupComponentRegistrationService {

	private static final Logger _log = LoggerFactory.getLogger(StartupComponentRegistrationServiceImpl.class);

	private final boolean _startupAutoRegistrationSkipped;

	private final Path _componentUploadDir;

	private final Path _pluginDeploymentDir;

	private final ComponentDependencyFinder _componentDependencyFinder;

	private final ComponentStateService _componentStateSvc;

	private final AddComponentService _addComponentService;

	private final StartupComponentServiceStarter _componentServiceStarter;


	@Inject
	public StartupComponentRegistrationServiceImpl(
			PropertiesUtil propertiesUtil,
			ComponentDependencyFinder componentDependencyFinder,
			ComponentStateService componentStateService,
			AddComponentService addComponentService,
			StartupComponentServiceStarter componentServiceStarter) {
		_startupAutoRegistrationSkipped = propertiesUtil.isStartupAutoRegistrationSkipped();
		_componentUploadDir = propertiesUtil.getUploadedComponentsDirectory().toPath();
		_pluginDeploymentDir = propertiesUtil.getPluginDeploymentPath().toPath();
		_componentDependencyFinder = componentDependencyFinder;
		_componentStateSvc = componentStateService;
		_addComponentService = addComponentService;
		_componentServiceStarter = componentServiceStarter;
	}



	@Override
	public void registerUnregisteredComponents() {
		if (_startupAutoRegistrationSkipped) {
			_log.info("Skipping component auto registration.");
			return;
		}

		List<Path> uploadedComponentPackages = listDirContent(_componentUploadDir);
		List<RegisterComponentModel> allComponentEntries = _componentStateSvc.get();
		Set<Path> unregisteredComponentPackages = getUnregisteredComponentPackages(uploadedComponentPackages,
		                                                                           allComponentEntries);
		if (unregisteredComponentPackages.isEmpty()) {
			_log.info("All component packages are already registered");
			return;
		}
		Map<Path, Path> packageToDeployedDescriptorMapping = getPackageToDeployedDescriptorMapping(
				unregisteredComponentPackages, allComponentEntries);

		List<Path> registrationOrder = getComponentRegistrationOrder(
				allComponentEntries, unregisteredComponentPackages, packageToDeployedDescriptorMapping);

		registerComponents(registrationOrder, packageToDeployedDescriptorMapping);
	}


	private List<Path> getComponentRegistrationOrder(Collection<RegisterComponentModel> allComponents,
	                                                 Collection<Path> uploadedComponentPackages,
	                                                 Map<Path, Path> packageToDescriptorMapping) {

		Stream<Path> registeredDescriptors = allComponents.stream()
				.filter(rcm -> rcm.getComponentState() == ComponentState.REGISTERED)
				.map(rcm -> Paths.get(rcm.getJsonDescriptorPath()));

		Set<Path> unregisteredPaths = uploadedComponentPackages.stream()
				.map(p -> packageToDescriptorMapping.getOrDefault(p, p))
				.collect(toSet());

		Set<Path> allPaths = Stream.concat(registeredDescriptors, unregisteredPaths.stream())
				.collect(toSet());

		try {
			return _componentDependencyFinder.getRegistrationOrder(allPaths).stream()
					.filter(unregisteredPaths::contains)
					.collect(toList());
		}
		catch (IllegalStateException e) {
			_log.error("An error occurred while trying to get component registration order.", e);
			for (Path componentPath : uploadedComponentPackages) {
				Path descriptorPath = packageToDescriptorMapping.get(componentPath);
				if (descriptorPath == null) {
					_componentStateSvc.addRegistrationErrorEntry(componentPath);
				}
				else {
					_componentStateSvc.addEntryForDeployedPackage(componentPath, descriptorPath);
				}
			}
			return Collections.emptyList();
		}
	}


	private void registerComponents(Iterable<Path> registrationOrder, Map<Path, Path> packageToDescriptorMapping) {
		Map<Path, Path> descriptorToPackageMapping = packageToDescriptorMapping
				.entrySet()
				.stream()
				.collect(toMap(Map.Entry::getValue, Map.Entry::getKey));

		boolean registrationFailed = false;
		List<RegisterComponentModel> registeredComponents = new ArrayList<>();
		for (Path componentPath : registrationOrder) {
			String packageName;
			if (componentPath.toString().toLowerCase().endsWith(".tar.gz")) {
				_componentStateSvc.addEntryForUploadedPackage(componentPath);
				packageName = componentPath.getFileName().toString();
			}
			else {
				Path packagePath = descriptorToPackageMapping.get(componentPath);
				_componentStateSvc.addEntryForDeployedPackage(packagePath, componentPath);
				packageName = packagePath.getFileName().toString();
			}
			if (registrationFailed) {
				continue;
			}

			try {
				RegisterComponentModel component = _addComponentService.registerComponent(packageName);
				registeredComponents.add(component);
			}
			catch (ComponentRegistrationException e) {
				registrationFailed = true;
				_log.error(String.format("Failed to register %s", packageName), e);
			}
		}
		_componentServiceStarter.startServicesForComponents(registeredComponents);
	}


	private static Set<Path> getUnregisteredComponentPackages(
			Collection<Path> uploadedPackages,
			Collection<RegisterComponentModel> allComponentEntries) {

		Set<Path> registeredPaths = allComponentEntries.stream()
				.filter(rcm -> rcm.getFullUploadedFilePath() != null)
				.map(rcm -> Paths.get(rcm.getFullUploadedFilePath()))
				.collect(toSet());

		return uploadedPackages.stream()
				.filter(p -> !registeredPaths.contains(p))
				.collect(toSet());
	}



	private Map<Path, Path> getPackageToDeployedDescriptorMapping(
				Collection<Path> unregisteredComponentPackages, Collection<RegisterComponentModel> components) {

		Set<Path> descriptors = getUnregisteredDeployedDescriptors(components);
		Map<String, Path> tldToDescriptorMapping = descriptors
				.stream()
				.collect(toMap(p -> p.getParent().getParent().getFileName().toString(),
				               Function.identity()));

		Map<Path, Path> packageToDeployedDescriptorMapping = new HashMap<>();
		for (Path packagePath : unregisteredComponentPackages) {
			String packageTld = getPackageTld(packagePath);
			Optional.ofNullable(packageTld)
					.map(tldToDescriptorMapping::get)
					.ifPresent(desc -> packageToDeployedDescriptorMapping.put(packagePath, desc));
		}

		logDescriptorsWithNoMatchingPackages(descriptors, packageToDeployedDescriptorMapping.values());

		return packageToDeployedDescriptorMapping;
	}


	private static void logDescriptorsWithNoMatchingPackages(Collection<Path> allDeployedDescriptors,
	                                                         Collection<Path> mappedDescriptors) {
		Collection<Path> descriptorsWithNoMatchingPackages = new HashSet<>(allDeployedDescriptors);
		descriptorsWithNoMatchingPackages.removeAll(mappedDescriptors);
		if (descriptorsWithNoMatchingPackages.isEmpty()) {
			return;
		}

		_log.error("The following descriptors do not have a matching component package and can not be registered: ");
		descriptorsWithNoMatchingPackages
				.forEach(d -> _log.error(d.toString()));
	}



	private Set<Path> getUnregisteredDeployedDescriptors(Collection<RegisterComponentModel> allComponents) {
		Set<Path> existingDescriptors = allComponents.stream()
				.filter(rcm -> rcm.getJsonDescriptorPath() != null)
				.map(rcm -> Paths.get(rcm.getJsonDescriptorPath()))
				.collect(toSet());

		List<Path> possiblePluginDirs = listDirContent(_pluginDeploymentDir);

		return possiblePluginDirs.stream()
				.map(StartupComponentRegistrationServiceImpl::getDescriptor)
				.filter(Objects::nonNull)
				.filter(p -> !existingDescriptors.contains(p))
				.collect(toSet());
	}


	private static Path getDescriptor(Path componentTopLevelDir) {
		Path descriptorDir = componentTopLevelDir.resolve("descriptor");
		if (Files.notExists(descriptorDir)) {
			return null;
		}
		Path defaultDescriptor = descriptorDir.resolve("descriptor.json");
		if (Files.exists(defaultDescriptor)) {
			return defaultDescriptor;
		}

		List<Path> descriptorDirContent = listDirContent(descriptorDir);
		if (descriptorDirContent.isEmpty()) {
			return null;
		}
		return descriptorDirContent.stream()
				.filter(p -> p.toString().toLowerCase().endsWith(".json"))
				.findAny()
				.orElse(descriptorDirContent.get(0));
	}


	private static List<Path> listDirContent(Path dir) {
		if (!Files.isDirectory(dir)) {
			return Collections.emptyList();
		}
		try (Stream<Path> dirChildren = Files.list(dir)) {
			return dirChildren.collect(toList());

		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to list contents of: " + dir, e);
		}
	}


	private static String getPackageTld(Path componentPackage) {
		try (TarArchiveInputStream inputStream
				     = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(componentPackage)))) {
			TarArchiveEntry tarEntry;
			while ((tarEntry = inputStream.getNextTarEntry()) != null) {
				Path entryPath = Paths.get(tarEntry.getName());
				if (entryPath.getNameCount() > 0) {
					return entryPath.getName(0).toString();
				}
			}
			return null;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
