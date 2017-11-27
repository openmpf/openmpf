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

package org.mitre.mpf.nms.streaming.messages;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

//TODO: For future use. Untested.
@SuppressWarnings("PublicField")
public class ComponentLaunchMessage implements Serializable {

	public final long jobId;

	public final String componentName;

	public final int stage;

	public final String libraryPath;

	public final Map<String, String> environmentVariables;

	public final int numInstances;

	public final Map<String, String> jobProperties;

	public final String segmentInputQueue;

	public final String frameInputQueue;

	public final String frameOutputQueue;

	public ComponentLaunchMessage(
			long jobId,
			String componentName,
			int stage,
			String libraryPath,
			Map<String, String> environmentVariables,
			int numInstances,
			Map<String, String> jobProperties,
			String segmentInputQueue,
			String frameInputQueue,
			String frameOutputQueue) {

		this.jobId = jobId;
		this.componentName = componentName;
		this.stage = stage;
		this.libraryPath = libraryPath;
		this.environmentVariables = new HashMap<>(environmentVariables);
		this.numInstances = numInstances;
		this.jobProperties = new HashMap<>(jobProperties);
		this.segmentInputQueue = segmentInputQueue;
		this.frameInputQueue = frameInputQueue;
		this.frameOutputQueue = frameOutputQueue;
	}
}
