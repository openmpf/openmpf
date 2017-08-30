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

package org.mitre.mpf.nms.streaming;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.mitre.mpf.nms.NodeManagerProperties;
import org.mitre.mpf.nms.streaming.messages.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

@Component
public class IniManager {

	public static final String DEFAULT_SECTION = "Job Config";

	public static final String JOB_PROPERTIES_SECTION = "Job Properties";

	private final NodeManagerProperties _properties;


	@Autowired
	public IniManager(NodeManagerProperties properties) {
		_properties = properties;
	}


	public JobIniFiles createJobIniFiles(StreamingJobLaunchMessage launchMessage) {
		try {
			Files.createDirectories(_properties.getIniFilesDir());
			Path jobIniDir = Files.createTempDirectory(
						_properties.getIniFilesDir(),
						String.format("mpf-job-%s-ini-files", launchMessage.jobId))
					.toAbsolutePath();

			jobIniDir.toFile().deleteOnExit();

			Path frameReaderIniPath = createIniFile(launchMessage.frameReaderLaunchMessage, jobIniDir);
			Path videoWriterIniPath = createIniFile(launchMessage.videoWriterLaunchMessage, jobIniDir);
			Table<String, Integer, Path> componentIniPaths =
					createComponentIniFiles(launchMessage.componentLaunchMessages, jobIniDir);

			return new JobIniFiles(jobIniDir, frameReaderIniPath, videoWriterIniPath, componentIniPaths);
		}
		catch (IOException e) {
			throw new UncheckedIOException(
					"An error occurred while trying to create INI files for job " + launchMessage.jobId, e);
		}
	}


	private static Path writeIniFile(Ini ini, String filePrefix, Path iniDir) {
		try {
			Path iniTempFile = Files.createTempFile(iniDir, filePrefix, ".ini").toAbsolutePath();
			iniTempFile.toFile().deleteOnExit();
			ini.store(iniTempFile.toFile());
			return iniTempFile;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}


	private static Path createIniFile(FrameReaderLaunchMessage launchMessage, Path iniDir) {
		Ini ini = new Ini();
		Profile.Section jobConfig = ini.add(DEFAULT_SECTION);
		jobConfig.put("jobId", launchMessage.jobId);
		jobConfig.put("streamUri", launchMessage.streamUri);
		jobConfig.put("segmentSize", launchMessage.segmentSize);
		jobConfig.put("frameDataBufferSize", launchMessage.frameDataBufferSize);
		jobConfig.put("stallTimeout", launchMessage.stallTimeout);
		jobConfig.put("messageBrokerUri", launchMessage.messageBrokerUri);
		jobConfig.put("segmentOutputQueue", launchMessage.segmentOutputQueue);
		jobConfig.put("componentFrameQueue", launchMessage.componentFrameQueue);
		jobConfig.put("videoWriterFrameQueue", launchMessage.videoWriterFrameQueue);
		jobConfig.put("releaseFrameQueue", launchMessage.releaseFrameQueue);
		jobConfig.put("stallAlertQueue", launchMessage.stallAlertQueue);
		return writeIniFile(ini, "frame-reader", iniDir);
	}


	private static Path createIniFile(VideoWriterLaunchMessage launchMessage, Path iniDir) {
		Ini ini = new Ini();
		Profile.Section jobConfig = ini.add(DEFAULT_SECTION);
		jobConfig.put("jobId", launchMessage.jobId);
		jobConfig.put("videoFileOutputPath", launchMessage.videoFileOutputPath);
		jobConfig.put("frameInputQueue", launchMessage.frameInputQueue);
		jobConfig.put("frameOutputQueue", launchMessage.frameOutputQueue);
		jobConfig.put("segmentOutputQueue", launchMessage.segmentOutputQueue);

		return writeIniFile(ini, "video-writer", iniDir);
	}



	private static Table<String, Integer, Path> createComponentIniFiles(
			Collection<ComponentLaunchMessage> launchMessages, Path iniDir) {

		ImmutableTable.Builder<String, Integer, Path> builder = ImmutableTable.builder();
		for (ComponentLaunchMessage launchMessage : launchMessages) {
			builder.put(launchMessage.componentName, launchMessage.stage, createIniFile(launchMessage, iniDir));
		}
		return builder.build();
	}


	private static Path createIniFile(ComponentLaunchMessage launchMessage, Path iniDir) {
		Ini ini = new Ini();
		Profile.Section jobConfig = ini.add(DEFAULT_SECTION);
		jobConfig.put("jobId", launchMessage.jobId);
		jobConfig.put("componentName", launchMessage.componentName);
		jobConfig.put("stage", launchMessage.stage);
		jobConfig.put("segmentInputQueue", launchMessage.segmentInputQueue);
		jobConfig.put("frameInputQueue", launchMessage.frameInputQueue);
		jobConfig.put("frameOutputQueue", launchMessage.frameOutputQueue);

		if (launchMessage instanceof LastStageComponentLaunchMessage) {
			addLastStageFields((LastStageComponentLaunchMessage) launchMessage, jobConfig);
		}

		if (!launchMessage.jobProperties.isEmpty()) {
			ini.add(JOB_PROPERTIES_SECTION).putAll(launchMessage.jobProperties);
		}

		return writeIniFile(ini, launchMessage.componentName + '-' + launchMessage.stage, iniDir);
	}



	private static void addLastStageFields(LastStageComponentLaunchMessage launchMessage, Profile.Section jobConfig) {
		jobConfig.put("newTrackAlertQueue", launchMessage.newTrackAlertQueue);
		jobConfig.put("summaryReportQueue", launchMessage.summaryReportQueue);
	}
}
