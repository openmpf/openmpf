/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

package org.mitre.mpf.mst;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = TestSystemWithDefaultConfig.AppCtxInit.class)
public abstract class TestSystemWithDefaultConfig extends TestSystem {

	public static class AppCtxInit extends BaseAppCtxInit {
		public AppCtxInit() {
			super("classpath:mpf-system-tests.properties");
		}
	}

	protected String addDefaultMotionMogPipeline() throws WfmProcessingException {
		String pipelineName = "TEST DEFAULT MOG MOTION DETECTION PIPELINE";
		addPipeline(pipelineName, "MOG MOTION DETECTION TASK");
		return pipelineName;
	}
}
