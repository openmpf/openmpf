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

package org.mitre.mpf.nms.streaming;

import org.mitre.mpf.nms.streaming.messages.LaunchStreamingJobMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class StreamingJobFactory {

	private final StreamingProcessFactory _processFactory;
	private final IniManager _iniManager;

	@Autowired
	public StreamingJobFactory(StreamingProcessFactory processFactory, IniManager iniManager) {
		_processFactory = processFactory;
		_iniManager = iniManager;
	}

	public StreamingJob createJob(LaunchStreamingJobMessage launchMessage) {
		JobIniFiles jobIniFiles = _iniManager.createJobIniFiles(launchMessage);
		Path componentIniPath = jobIniFiles.getJobIniPath();
		StreamingProcess componentProcess = _processFactory.createComponentProcess(
				launchMessage.componentName, componentIniPath, launchMessage.componentEnvironmentVariables);

		return new StreamingJob(launchMessage.jobId, jobIniFiles, componentProcess);
	}


	//TODO: For future use. Untested.
//	public StreamingJob createJob(LaunchStreamingJobMessage launchMessage) {
//		JobIniFiles jobIniFiles = _iniManager.createJobIniFiles(launchMessage);
//		StreamingProcess frameReader = _processFactory.createFrameReaderProcess(jobIniFiles.getFrameReaderIniPath());
//		StreamingProcess videoWriter = _processFactory.createVideoWriterProcess(jobIniFiles.getVideoWriterIniPath());
//
//		List<StreamingProcess> componentProcesses = new ArrayList<>();
//		for (LaunchComponentMessage componentMessage : launchMessage.launchComponentMessages) {
//			for (int i = 0; i < componentMessage.numInstances; i++) {
//				Path componentIniPath = jobIniFiles.getComponentIniPath(componentMessage.componentName,
//				                                                        componentMessage.stage);
//				componentProcesses.add(_processFactory.createComponentProcess(
//						componentIniPath, componentMessage.environmentVariables));
//
//			}
//		}
//
//		return new StreamingJob(launchMessage.jobId, jobIniFiles, frameReader, videoWriter, componentProcesses);
//
//	}

}
