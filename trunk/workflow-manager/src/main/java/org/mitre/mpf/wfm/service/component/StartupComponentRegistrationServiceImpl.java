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


package org.mitre.mpf.wfm.service.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
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

    private final boolean _dockerProfileEnabled;

    private final Path _componentUploadDir;

    private final Path _pluginDeploymentDir;

    private final ComponentStateService _componentStateSvc;

    private final AddComponentService _addComponentService;

    private final StartupComponentServiceStarter _componentServiceStarter;

    private final ObjectMapper _objectMapper;


    @Inject
    public StartupComponentRegistrationServiceImpl(
            PropertiesUtil propertiesUtil,
            ComponentStateService componentStateService,
            AddComponentService addComponentService,
            Optional<StartupComponentServiceStarter> componentServiceStarter,
            ObjectMapper objectMapper) {
        _startupAutoRegistrationSkipped = propertiesUtil.isStartupAutoRegistrationSkipped();
        _dockerProfileEnabled = propertiesUtil.dockerProfileEnabled();
        _componentUploadDir = propertiesUtil.getUploadedComponentsDirectory().toPath();
        _pluginDeploymentDir = propertiesUtil.getPluginDeploymentPath();
        _componentStateSvc = componentStateService;
        _addComponentService = addComponentService;
        _componentServiceStarter = componentServiceStarter.orElse(null);
        _objectMapper = objectMapper;
    }


    @Override
    public void registerUnregisteredComponents() {
        if (_startupAutoRegistrationSkipped) {
            _log.info("Skipping component auto registration.");
            return;
        }
        if (_dockerProfileEnabled) {
            registerDescriptors();
            return;
        }

        registerAll();
    }


    private void registerAll() {
        List<RegisterComponentModel> allComponentEntries = _componentStateSvc.get();

        Set<Path> unregisteredComponentPackages = getUnregisteredComponentPackages(allComponentEntries);

        Set<Path> unregisteredDescriptors = new HashSet<>(getUnregisteredDeployedDescriptors(allComponentEntries));
        Map<String, Path> tldToDescriptorMapping = unregisteredDescriptors
                .stream()
                .collect(toMap(p -> p.getParent().getParent().getFileName().toString(),
                               Function.identity()));

        var registeredComponentsWithServiceToStart = new ArrayList<RegisterComponentModel>();

        for (Path unregisteredPackage : unregisteredComponentPackages) {
            String packageTld = getPackageTld(unregisteredPackage);
            if (packageTld == null) {
                continue;
            }

            Path descriptorPath = tldToDescriptorMapping.get(packageTld);
            if (descriptorPath == null) {
                _componentStateSvc.addEntryForUploadedPackage(unregisteredPackage);
            }
            else {
                unregisteredDescriptors.remove(descriptorPath);
                _componentStateSvc.addEntryForDeployedPackage(unregisteredPackage, descriptorPath);
            }

            String packageName = unregisteredPackage.getFileName().toString();
            try {
                RegisterComponentModel component = _addComponentService.registerComponent(packageName);
                registeredComponentsWithServiceToStart.add(component);
            }
            catch (ComponentRegistrationException e) {
                _log.error(String.format("Failed to register %s", packageName), e);
            }
        }

        for (Path unregisteredDescriptor : unregisteredDescriptors) {
            registerDescriptor(unregisteredDescriptor);
        }

        _componentServiceStarter.startServicesForComponents(registeredComponentsWithServiceToStart);
    }


    private void registerDescriptors() {
        List<RegisterComponentModel> allComponentEntries = _componentStateSvc.get();

        Set<Path> unregisteredDescriptors = getUnregisteredDeployedDescriptors(allComponentEntries);

        for (Path unregisteredDescriptor : unregisteredDescriptors) {
            registerDescriptor(unregisteredDescriptor);
        }
    }


    private void registerDescriptor(Path descriptorPath) {
        try {
            var descriptor
                    = _objectMapper.readValue(descriptorPath.toFile(), JsonComponentDescriptor.class);
            _addComponentService.registerUnmanagedComponent(descriptor);
        }
        catch (ComponentRegistrationException | IOException e) {
            _log.error(String.format("Failed to register %s", descriptorPath), e);
        }
    }


    private Set<Path> getUnregisteredComponentPackages(
            Collection<RegisterComponentModel> allComponentEntries) {

        List<Path> uploadedComponentPackages = listDirContent(_componentUploadDir).stream()
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".tar.gz"))
                .collect(toList());

        Set<Path> registeredPaths = allComponentEntries.stream()
                .filter(rcm -> rcm.getFullUploadedFilePath() != null)
                .map(rcm -> Paths.get(rcm.getFullUploadedFilePath()))
                .collect(toSet());

        return uploadedComponentPackages.stream()
                .filter(p -> !registeredPaths.contains(p))
                .collect(toSet());
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
