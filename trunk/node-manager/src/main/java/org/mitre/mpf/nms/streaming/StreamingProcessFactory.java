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

package org.mitre.mpf.nms.streaming;

import org.mitre.mpf.nms.util.EnvironmentVariableExpander;
import org.mitre.mpf.nms.util.PropertiesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

@Component
public class StreamingProcessFactory {

	private final PropertiesUtil _propertiesUtil;

	@Autowired
	public StreamingProcessFactory(PropertiesUtil propertiesUtil) {
		_propertiesUtil = propertiesUtil;
	}


	//TODO: For future use. Untested.
//	public StreamingProcess createFrameReaderProcess(Path iniPath) {
//		return createProcess("FrameReader", _propertiesUtil.getStreamingFrameReaderExecutable(), iniPath);
//	}
//
//
//	public StreamingProcess createVideoWriterProcess(Path iniPath) {
//		return createProcess("VideoWriter", _propertiesUtil.getStreamingVideoWriterExecutable(), iniPath);
//	}
//
//
//	private StreamingProcess createProcess(String name, String executable, Path iniPath) {
//		return createProcess(name, executable, iniPath, Collections.emptyMap());
//	}



//	public StreamingProcess createComponentProcess(Path iniPath, Map<String, String> environmentVariables) {
//		return createProcess("Component", _propertiesUtil.getStreamingComponentExecutor(), iniPath,
//		                     environmentVariables);
//	}

//private StreamingProcess createProcess(String name, String executable, Path iniPath,
//                                       Map<String, String> environmentVariables) {
//	List<String> cmdlineArgs = Arrays.asList(executable, name, iniPath.toString());
//
//
//	ProcessBuilder processBuilder = new ProcessBuilder(cmdlineArgs)
//			.redirectErrorStream(true);
//	processBuilder.environment().putAll(environmentVariables);
//
//	return new StreamingProcess(name, processBuilder, _propertiesUtil.getStreamingProcessMaxRestarts());
//}


	public StreamingProcess createComponentProcess(String componentName, Path iniPath, Map<String, String> environmentVariables) {
		String[] command = { _propertiesUtil.getStreamingComponentExecutor(), iniPath.toAbsolutePath().toString()} ;
		File workingDirectory = new File(_propertiesUtil.getPluginDir(), componentName);

		ProcessBuilder processBuilder = new ProcessBuilder(command)
				.directory(workingDirectory)
				.redirectErrorStream(true);

		String mpfHomeKey = "MPF_HOME";
		String ldLibPathKey = "LD_LIBRARY_PATH";
		Map<String, String> env = processBuilder.environment();

		env.putAll(EnvironmentVariableExpander.expandValues(environmentVariables));

		String mpfHomeVal = env.get(mpfHomeKey);
		if (mpfHomeVal == null) {
			throw new IllegalStateException("Missing environment variable: " + mpfHomeKey);
		}

		String ldLibPathVal = env.get(ldLibPathKey);
		if (ldLibPathVal != null) {
			env.put(ldLibPathKey, ldLibPathVal + System.getProperty("path.separator") + mpfHomeVal + "/lib");
		} else {
			env.put(ldLibPathKey, mpfHomeVal + "/lib");
		}

		return new StreamingProcess("Component", processBuilder,
		                            _propertiesUtil.getStreamingProcessMaxRestarts());
	}
}
