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

import org.ini4j.Ini;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.nms.NodeManagerProperties;
import org.mitre.mpf.nms.streaming.messages.ComponentLaunchMessage;
import org.mitre.mpf.nms.streaming.messages.LastStageComponentLaunchMessage;
import org.mitre.mpf.nms.streaming.messages.StreamingJobLaunchMessage;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestIniManager {

	@Rule
	public TemporaryFolder _tempFolder = new TemporaryFolder();

	private NodeManagerProperties _mockProperties;

	@Before
	public void init() {
		_mockProperties = mock(NodeManagerProperties.class);
		when(_mockProperties.getIniFilesDir())
				.thenReturn(_tempFolder.getRoot().toPath().resolve("mpf-ini-files"));

	}


	@Test
	public void canCreateIniFiles() throws IOException, NoSuchFieldException, IllegalAccessException {
		IniManager iniManager = new IniManager(_mockProperties);
		StreamingJobLaunchMessage launchMessage = StreamingJobTestUtil.createLaunchMessage();

		JobIniFiles jobIniFiles = iniManager.createJobIniFiles(launchMessage);

		assertFieldsWritten(launchMessage.frameReaderLaunchMessage, jobIniFiles.getFrameReaderIniPath(), 11);
		assertFieldsWritten(launchMessage.videoWriterLaunchMessage, jobIniFiles.getVideoWriterIniPath(), 5);

		for (ComponentLaunchMessage componentLaunchMessage : launchMessage.componentLaunchMessages) {
			int expectedFieldCount = componentLaunchMessage instanceof LastStageComponentLaunchMessage ? 8 : 6;
			Path componentIniPath = jobIniFiles.getComponentIniPath(componentLaunchMessage.componentName,
			                                                        componentLaunchMessage.stage);

			assertFieldsWritten(componentLaunchMessage, componentIniPath, expectedFieldCount);
			assertJobPropertiesWritten(componentLaunchMessage, componentIniPath);
		}


		jobIniFiles.deleteIniFiles();
		assertTrue(Files.notExists(jobIniFiles.getFrameReaderIniPath()));
		assertTrue(Files.notExists(jobIniFiles.getVideoWriterIniPath()));
		for (ComponentLaunchMessage componentLaunchMessage : launchMessage.componentLaunchMessages) {
			Path componentIniPath = jobIniFiles.getComponentIniPath(componentLaunchMessage.componentName,
			                                                        componentLaunchMessage.stage);
			assertTrue(Files.notExists(componentIniPath));
		}
	}




	private static void assertFieldsWritten(Object obj, Path iniPath, int expectedFieldCount)
				throws NoSuchFieldException, IllegalAccessException, IOException {
		Ini ini = new Ini(iniPath.toFile());
		Map<String, String> jobConfig = ini.get(IniManager.DEFAULT_SECTION);
		assertEquals(expectedFieldCount, jobConfig.size());

		for (Map.Entry<String, String> entry : jobConfig.entrySet()) {
			Field field = obj.getClass().getField(entry.getKey());
			String expectedValue = field.get(obj).toString();
			assertEquals(String.format("Key: %s contained the wrong value", entry.getKey()),
			             expectedValue, entry.getValue());
		}
	}


	private static void assertJobPropertiesWritten(ComponentLaunchMessage launchMessage, Path iniPath)
			throws IOException {
		Ini ini = new Ini(iniPath.toFile());
		Map<String, String> actualJobProperties = ini.get(IniManager.JOB_PROPERTIES_SECTION);

		assertEquals(launchMessage.jobProperties, actualJobProperties);
	}

}
