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


import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    @Value("${mpf.ansible.child.vars.path}")
    private  String _varsPath;

    @Value("${mpf.ansible.compdeploy.path}")
    private String _compDeployPath;

    @Value("${mpf.ansible.compremove.path}")
    private String _compRemovePath;

    @Value("${mpf.ansible.local-only:false}")
    private boolean _ansibleLocalOnly;

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

    private static final Pattern COMPONENT_DESCRIPTOR_PATH_REGEX =
            Pattern.compile("\"COMPONENT_DESCRIPTOR_PATH=([^\"]+)\"");

    private static final String DUPLICATE_ERROR_IDENTIFIER = "FAILED_DUPLICATE_ERROR";

    private static final String UNREACHABLE_ERROR_IDENTIFIER = "Failed to connect to the host via ssh";

    private String runAnsibleDeploy(String componentPackageFileName)
            throws DuplicateComponentException, IOException, InterruptedException
    {
        String uploadedComponentArg = "uploaded_component=" + componentPackageFileName;
        Process ansibleProc = createAnsibleProc(_compDeployPath, uploadedComponentArg);

        String descriptorPath = null;
        boolean failedDueToDuplicateError = false;
        boolean failedDueToHostUnreachable = false;

        try (BufferedReader procOutputReader = new BufferedReader(
                new InputStreamReader(ansibleProc.getInputStream()))) {

            String line;
            while ((line = procOutputReader.readLine()) != null) {
                _log.info(line);

                if (!failedDueToDuplicateError) {
                    failedDueToDuplicateError = line.contains(DUPLICATE_ERROR_IDENTIFIER);
                }
                if (!failedDueToHostUnreachable) {
                    failedDueToHostUnreachable = line.contains(UNREACHABLE_ERROR_IDENTIFIER);
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
                throw new IllegalStateException("Couldn't find the descriptor path in ansible output");
            }
            return descriptorPath;
        }
        else if (failedDueToDuplicateError) {
            throw new DuplicateComponentException(componentPackageFileName);
        }
        else if (failedDueToHostUnreachable) {
            throw new IllegalStateException(UNREACHABLE_ERROR_TEXT);
        }
        else {
            throw new IllegalStateException(
                    String.format("Ansible process did not return success exit code: %s", exitCode));
        }
    }

    // Regex match example:
    // fatal: [localhost.localdomain]: FAILED! => {"changed": false, "failed": true, "msg": "FAILED_COMPONENT_NOT_FOUND_ERROR PATH=/home/mpf/mpf/trunk/install/plugins/TestComponent"}
    private static final Pattern COMPONENT_NOT_FOUND_ERROR_REGEX =
            Pattern.compile("\"FAILED_COMPONENT_NOT_FOUND_ERROR PATH=([^\"]+)\"");

    private void runAnsibleUndeploy(String componentTld) throws IOException, InterruptedException {
        String componentTldArg = "component_tld=" + componentTld;
        Process ansibleProc = createAnsibleProc(_compRemovePath, componentTldArg);

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
                "-e", "@" + _varsPath,
                "-e", finalArg);
        if (_ansibleLocalOnly) {
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
