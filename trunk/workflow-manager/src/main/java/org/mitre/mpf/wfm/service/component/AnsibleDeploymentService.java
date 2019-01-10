/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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


import com.google.common.collect.Lists;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AnsibleDeploymentService implements ComponentDeploymentService {

    private static final Logger _log = LoggerFactory.getLogger(AnsibleDeploymentService.class);

    private static final String UNREACHABLE_ERROR_TEXT =
            "Ansible failed because it could not connect to one of the hosts in the cluster. " +
            "If this is a single node configuration, " +
            "try adding \"mpf.ansible.local-only=true\" to mpf-private.properties";

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Override
    public String deployComponent(String componentPackageFileName) throws DuplicateComponentException {
        try {
            return runAnsibleDeploy(componentPackageFileName);
        }
        catch (IOException e) {
            throw new IllegalStateException("Ansible deployment failed", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ansible deployment received interrupt", e);
        }
    }

    @Override
    public void undeployComponent(String componentTld) {
        try {
            runAnsibleUndeploy(componentTld);
        }
        catch (IOException e) {
            throw new IllegalStateException("Ansible un-deployment failed", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ansible un-deployment received interrupt", e);
        }
    }

    private static final Pattern COMPONENT_TOP_LEVEL_DIRECTORY_REGEX =
            Pattern.compile("\"COMPONENT_TOP_LEVEL_DIRECTORY_PATH=([^\"]+)\"");

    private static final Pattern COMPONENT_DESCRIPTOR_PATH_REGEX =
            Pattern.compile("\"COMPONENT_DESCRIPTOR_PATH=([^\"]+)\"");

    private static final String DUPLICATE_ERROR_IDENTIFIER = "FAILED_DUPLICATE_ERROR";

    private static final String UNREACHABLE_ERROR_IDENTIFIER = "Failed to connect to the host via ssh";

    private String runAnsibleDeploy(String componentPackageFileName)
            throws DuplicateComponentException, IOException, InterruptedException
    {
        String uploadedComponentArg = "uploaded_component=" + componentPackageFileName;
        Process ansibleProc = createAnsibleProc(propertiesUtil.getAnsibleCompDeployPath(), uploadedComponentArg);

        String topLevelDirectoryPath = null;
        String descriptorPath = null;
        boolean failedDueToDuplicateError = false;
        boolean failedDueToHostUnreachable = false;

        try (BufferedReader procOutputReader = new BufferedReader(
                new InputStreamReader(ansibleProc.getInputStream()))) {

            String line;
            while ((line = procOutputReader.readLine()) != null) {
                if (line.startsWith("skipping") && line.contains("=> (item=")) {
                	// The "Get the component top level directory" Ansible task prints out each
                    // file in the component package which pollutes the log.
                    _log.debug(line);
                }
                else {
                    _log.info(line);
                }

                if (!failedDueToDuplicateError) {
                    failedDueToDuplicateError = line.contains(DUPLICATE_ERROR_IDENTIFIER);
                }
                if (!failedDueToHostUnreachable) {
                    failedDueToHostUnreachable = line.contains(UNREACHABLE_ERROR_IDENTIFIER);
                }
                if (topLevelDirectoryPath == null) {
                    Matcher matcher = COMPONENT_TOP_LEVEL_DIRECTORY_REGEX.matcher(line);
                    if (matcher.find()) {
                        topLevelDirectoryPath = matcher.group(1);
                    }
                }
                if (descriptorPath == null) {
                    Matcher matcher = COMPONENT_DESCRIPTOR_PATH_REGEX.matcher(line);
                    if (matcher.find()) {
                        descriptorPath = matcher.group(1);
                    }
                }
            }
        }

        int exitCode = ansibleProc.waitFor();
        if (exitCode == 0) {
            if (descriptorPath == null) {
            	undeployIfSuccessfullyExtracted(topLevelDirectoryPath);
                throw new IllegalStateException("Couldn't find the descriptor path in ansible output");
            }
            return descriptorPath;
        }
        else if (failedDueToDuplicateError) {
        	// We do not undeploy the component because an existing component is using
            // that top level directory and we don't want to unregister the existing component.
            throw new DuplicateComponentException(componentPackageFileName);
        }
        else if (failedDueToHostUnreachable) {
        	// The component archive was never extracted, so we don't undeploy the component.
            throw new IllegalStateException(UNREACHABLE_ERROR_TEXT);
        }
        else {
            undeployIfSuccessfullyExtracted(topLevelDirectoryPath);
            throw new IllegalStateException(
                    String.format("Ansible process did not return success exit code: %s", exitCode));
        }
    }


    // If registration fails after successfully extracting the component,
    // we need to undeploy the component. If we don't we will get
    // duplicate top level directory errors when we try to register again.
    private void undeployIfSuccessfullyExtracted(String topLevelDirectoryPath) {
        if (topLevelDirectoryPath != null)  {
        	String componentTld = Paths.get(topLevelDirectoryPath).getFileName().toString();
            undeployComponent(componentTld);
        }
    }



    // Regex match example:
    // fatal: [localhost.localdomain]: FAILED! => {"changed": false, "failed": true, "msg": "FAILED_COMPONENT_NOT_FOUND_ERROR PATH=/home/mpf/mpf/trunk/install/plugins/TestComponent"}
    private static final Pattern COMPONENT_NOT_FOUND_ERROR_REGEX =
            Pattern.compile("\"FAILED_COMPONENT_NOT_FOUND_ERROR PATH=([^\"]+)\"");

    private void runAnsibleUndeploy(String componentTld) throws IOException, InterruptedException {
        String componentTldArg = "component_tld=" + componentTld;
        Process ansibleProc = createAnsibleProc(propertiesUtil.getAnsibleCompRemovePath(), componentTldArg);

        String missingComponentPath = null;
        boolean failedDueToHostUnreachable = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ansibleProc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!failedDueToHostUnreachable) {
                    failedDueToHostUnreachable = line.contains(UNREACHABLE_ERROR_IDENTIFIER);
                }
                _log.info(line);
                if (missingComponentPath == null) {
                    Matcher matcher = COMPONENT_NOT_FOUND_ERROR_REGEX.matcher(line);
                    if (matcher.find()) {
                        missingComponentPath = matcher.group(1);
                    }
                }
            }
        }

        int exitCode = ansibleProc.waitFor();
        if (missingComponentPath != null) {
            _log.warn("Expected to find component top level directory at {} but it isn't there.",
                    missingComponentPath);
        }
        else if (failedDueToHostUnreachable) {
            throw new IllegalStateException(UNREACHABLE_ERROR_TEXT);
        }
        else if (exitCode != 0) {
            throw new IllegalStateException(
                    String.format("Ansible process did not return success exit code: %s", exitCode));
        }
    }

    private Process createAnsibleProc(String playbookPath, String finalArg) {
        List<String> command = Lists.newArrayList(
                "ansible-playbook",
                playbookPath,
                "--user=mpf",
                "-e", "@" + propertiesUtil.getAnsibleChildVarsPath(),
                "-e", finalArg);
        if (propertiesUtil.isAnsibleLocalOnly()) {
            command.add("--connection=local");
        }

        _log.info("Running command: {}", String.join(" ", command));
        try {
            return new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        }
        catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to start ansible process. Make sure ansible-playbook is on the path.", e);
        }
    }
}
