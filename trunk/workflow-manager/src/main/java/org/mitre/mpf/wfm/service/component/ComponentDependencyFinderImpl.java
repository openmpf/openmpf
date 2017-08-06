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

import com.google.common.collect.Lists;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Named
public class ComponentDependencyFinderImpl implements ComponentDependencyFinder {

	private static final Logger _log = LoggerFactory.getLogger(ComponentDependencyFinderImpl.class);

	private final String _componentDependencyScript;


	@Inject
	public ComponentDependencyFinderImpl(PropertiesUtil propertiesUtil) {
		_componentDependencyScript = propertiesUtil.getComponentDependencyFinderScript().getAbsolutePath();
	}


	@Override
	public List<Path> getRegistrationOrder(Collection<Path> componentPaths) {
		List<String> arguments = componentPaths.stream()
				.map(Path::toString)
				.collect(toList());

		return runCommand(arguments);
	}


	@Override
	public List<Path> getReRegistrationOrder(Path componentToReReg, Collection<Path> registeredComponentPaths) {
		List<String> arguments = Lists.newArrayList("--for-single-component", componentToReReg.toString());
		for (Path componentPath : registeredComponentPaths) {
			arguments.add(componentPath.toString());
		}
		return runCommand(arguments);
	}


	private List<Path> runCommand(Collection<String> arguments) {
		Process process = beginProcess(arguments);
		logDependencyScriptStandardError(process);

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			List<Path> result = reader.lines()
					.map(Paths::get)
					.collect(toList());

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				throw new IllegalStateException(String.format(
						"The component dependency finder script returned non-zero exit code: %s.", exitCode));
			}
			return result;
		}
		catch (IOException e) {
			throw new IllegalStateException(
					"An exception occurred while trying to read the output of the component dependency finder script.",
					e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(
					"Received interrupt while waiting for component dependency finder script to exit.", e);
		}
	}


	private Process beginProcess(Collection<String> arguments) {
		List<String> command = Lists.newArrayList("python", _componentDependencyScript);
		command.addAll(arguments);

		String cmdString = command.stream().collect(joining(" "));
		_log.info("Running command: {}", cmdString);

		try {
			return new ProcessBuilder(command)
					.start();
		}
		catch (IOException e) {
			throw new IllegalStateException("An exception occurred while trying to run: " + cmdString, e);
		}
	}


	private void logDependencyScriptStandardError(Process process) {
		new Thread(() -> {
			String dependencyScriptName = Paths.get(_componentDependencyScript).getFileName().toString();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				reader.lines()
						.forEach(line -> _log.error("[{} stderr]: {}", dependencyScriptName, line));
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}).start();
	}
}
