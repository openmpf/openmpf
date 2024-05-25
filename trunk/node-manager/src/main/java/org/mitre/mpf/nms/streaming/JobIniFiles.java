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

package org.mitre.mpf.nms.streaming;

import org.springframework.util.FileSystemUtils;

import java.nio.file.Path;

public class JobIniFiles {

	private final Path _jobIniDir;

	// Path to an .ini file that contains all job information.
	// When support for multi-stage streaming pipelines is added, this file will no longer be used.
	// Instead, there will be a separate ini file for each executor that only
	// contains the information relevant to that executor.
	private final Path _jobIniPath;

	//TODO: For future use. Untested.
//	private final Path _frameReaderIniPath;
//
//	private final Path _videoWriterIniPath;
//
//	private final ImmutableTable<String, Integer, Path> _componentIniPaths;


	//TODO: For future use. Untested.
//	public JobIniFiles(Path jobIniDir, Path frameReaderIniPath, Path videoWriterIniPath,
//	                   Table<String, Integer, Path> componentIniPaths) {
//		_jobIniDir = jobIniDir;
//		_frameReaderIniPath = frameReaderIniPath;
//		_videoWriterIniPath = videoWriterIniPath;
//		_componentIniPaths = ImmutableTable.copyOf(componentIniPaths);
//	}

	public JobIniFiles(Path jobIniDir, Path jobIniPath) {
		_jobIniDir = jobIniDir;
		_jobIniPath = jobIniPath;

	}

	public Path getJobIniPath() {
		return _jobIniPath;
	}

	//TODO: For future use. Untested.
//	public Path getFrameReaderIniPath() {
//		return _frameReaderIniPath;
//	}
//
//	public Path getVideoWriterIniPath() {
//		return _videoWriterIniPath;
//	}
//
//	public Path getComponentIniPath(String componentName, int stage) {
//		return _componentIniPaths.get(componentName, stage);
//	}

	public void deleteIniFiles() {
		FileSystemUtils.deleteRecursively(_jobIniDir.toFile());
	}
}
