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

package org.mitre.mpf.nms.streaming;

import org.mitre.mpf.nms.NodeManagerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class StreamingProcessFactory {

	private final NodeManagerProperties _properties;

	@Autowired
	public StreamingProcessFactory(NodeManagerProperties properties) {
		_properties = properties;
	}


	public StreamingProcess createFrameReaderProcess(Path iniPath) {
		return createProcess("FrameReader", _properties.getStreamingFrameReaderExecutable(), iniPath);
	}


	public StreamingProcess createVideoWriterProcess(Path iniPath) {
		return createProcess("VideoWriter", _properties.getStreamingVideoWriterExecutable(), iniPath);
	}

	public StreamingProcess createComponentProcess(Path iniPath, Map<String, String> environmentVariables) {
		return createProcess("Component", _properties.getStreamingComponentExecutor(), iniPath,
		                     environmentVariables);
	}



	private StreamingProcess createProcess(String name, String executable, Path iniPath) {
		return createProcess(name, executable, iniPath, Collections.emptyMap());
	}


	private StreamingProcess createProcess(String name, String executable, Path iniPath,
	                                       Map<String, String> environmentVariables) {
		// TODO: Remove python as first command line argument
		List<String> cmdlineArgs = Arrays.asList("python", executable, name, iniPath.toString());
//		List<String> cmdlineArgs = Arrays.asList(executable, name, iniPath.toString());


		ProcessBuilder processBuilder = new ProcessBuilder(cmdlineArgs)
				.redirectErrorStream(true);
		processBuilder.environment().putAll(environmentVariables);

		return new StreamingProcess(name, processBuilder, _properties.getStreamingProcessMaxRestarts());
	}
}
