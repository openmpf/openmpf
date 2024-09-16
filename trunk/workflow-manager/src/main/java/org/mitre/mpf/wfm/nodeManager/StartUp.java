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

package org.mitre.mpf.wfm.nodeManager;

import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!docker")
public class StartUp implements SmartLifecycle {

	@Autowired
	private NodeManagerStatus nodeManagerStatus;

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	@Override
	public void start() {
		Split split = SimonManager.getStopwatch("org.mitre.mpf.wfm.nodeManager.StartUp.start").start();
		nodeManagerStatus.start();
		split.stop();
	}

	@Override
	public void stop() {
		Split split = SimonManager.getStopwatch("org.mitre.mpf.wfm.nodeManager.StartUp.stop").start();
		nodeManagerStatus.stop();
		split.stop();
	}

	@Override
	public boolean isRunning() {
		return nodeManagerStatus.isInitialized();
	}

	@Override
	public void stop(Runnable r) {
		this.stop();
		r.run();
	}

	@Override
	public int getPhase() {
		return -1;
	}
}

