/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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
import org.mitre.mpf.nms.streaming.messages.LaunchStreamingJobMessage;
import org.mitre.mpf.nms.util.PropertiesUtil;

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

	private PropertiesUtil _mockPropertiesUtil;

	@Before
	public void init() {
		_mockPropertiesUtil = mock(PropertiesUtil.class);
		when(_mockPropertiesUtil.getIniFilesDir())
				.thenReturn(_tempFolder.getRoot().toPath().resolve("mpf-ini-files"));

	}

	//TODO: For future use.
//	@Test
//	public void canCreateIniFiles() throws IOException, NoSuchFieldException, IllegalAccessException {
//		IniManager iniManager = new IniManager(_mockPropertiesUtil);
//		LaunchStreamingJobMessage launchMessage = StreamingJobTestUtil.createLaunchMessage();
//
//		JobIniFiles jobIniFiles = iniManager.createJobIniFiles(launchMessage);
//
//		assertFieldsWritten(launchMessage.launchFrameReaderMessage, jobIniFiles.getFrameReaderIniPath(), 12);
//		assertFieldsWritten(launchMessage.launchVideoWriterMessage, jobIniFiles.getVideoWriterIniPath(), 5);
//
//		for (LaunchComponentMessage launchComponentMessage : launchMessage.launchComponentMessages) {
//			int expectedFieldCount = launchComponentMessage instanceof LaunchLastStageComponentMessage ? 8 : 6;
//			Path componentIniPath = jobIniFiles.getComponentIniPath(launchComponentMessages.componentName,
//			                                                        launchComponentMessage.stage);
//
//			assertFieldsWritten(launchComponentMessage, componentIniPath, expectedFieldCount);
//			assertJobPropertiesWritten(launchComponentMessage.jobProperties, componentIniPath);
//		}
//
//
//		jobIniFiles.deleteIniFiles();
//		assertTrue(Files.notExists(jobIniFiles.getFrameReaderIniPath()));
//		assertTrue(Files.notExists(jobIniFiles.getVideoWriterIniPath()));
//		for (LaunchComponentMessage launchComponentMessage : launchMessage.launchComponentMessages) {
//			Path componentIniPath = jobIniFiles.getComponentIniPath(launchComponentMessage.componentName,
//			                                                        launchComponentMessage.stage);
//			assertTrue(Files.notExists(componentIniPath));
//		}
//	}

	@Test
	public void canCreateIniFiles() throws IOException, NoSuchFieldException, IllegalAccessException {
		IniManager iniManager = new IniManager(_mockPropertiesUtil);
		LaunchStreamingJobMessage launchMessage = StreamingJobTestUtil.createLaunchMessage();

		JobIniFiles jobIniFiles = iniManager.createJobIniFiles(launchMessage);
		assertFieldsWritten(launchMessage, jobIniFiles.getJobIniPath(), 11);
		assertPropertiesWritten(launchMessage.jobProperties, jobIniFiles.getJobIniPath(),
		                        IniManager.JOB_PROPERTIES_SECTION);
		assertPropertiesWritten(launchMessage.mediaProperties, jobIniFiles.getJobIniPath(),
		                        IniManager.MEDIA_PROPERTIES_SECTION);

		jobIniFiles.deleteIniFiles();
		assertTrue(Files.notExists(jobIniFiles.getJobIniPath()));
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


	private static void assertPropertiesWritten(Map<String, String> jobProperties, Path iniPath, String section) throws IOException {
		Ini ini = new Ini(iniPath.toFile());
		Map<String, String> actualJobProperties = ini.get(section);
		assertEquals(jobProperties, actualJobProperties);
	}

}
