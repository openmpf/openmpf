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

package org.mitre.mpf.wfm;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mitre.mpf.interop.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonTests {
	private static final Logger log = LoggerFactory.getLogger(JsonTests.class);
	private static final long SECOND = 1000;
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test(timeout = 1 * SECOND)
	public void testJson() throws Exception {
		MAPPER.readValue("{\"jobId\":5,\"pipeline\":null,\"siteId\":\"d4dcbf92-d7af-490a-a4fa-13463a6b6304\",\"timeStart\":\"2015-10-21 12:46:53.0\",\"timeStop\":\"2015-10-21 12:46:54.0\",\"status\":\"ERROR\",\"media\":[{\"mediaId\":0,\"path\":\"http://somehost-mpf-4.mitre.org/rsrc/datasets/samples/motion/five-second-marathon-clip.mkv\",\"mimeType\":null,\"length\":0,\"status\":\"COMPLETE\",\"message\":null,\"sha256\":null,\"markupResult\":null,\"tracks\":{},\"detectionProcessingErrors\":{}}]}", JsonOutputObject.class);
	}
}
