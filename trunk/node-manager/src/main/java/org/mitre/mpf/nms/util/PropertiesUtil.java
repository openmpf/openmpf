/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

package org.mitre.mpf.nms.util;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

@Component
public class PropertiesUtil {

	@javax.annotation.Resource(name="propFile")
	private Resource propFile;

	private PropertiesConfiguration propertiesConfig;

	@PostConstruct
	private void init() {
		URL url;
		try {
			url = propFile.getURL();
		} catch (IOException e) {
			throw new IllegalStateException("Cannot get URL from " + propFile + ".", e);
		}

		FileBasedConfigurationBuilder<PropertiesConfiguration> fileBasedConfigBuilder =
				new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class);

		Parameters configBuilderParameters = new Parameters();
		fileBasedConfigBuilder.configure(configBuilderParameters.fileBased().setURL(url)
				.setListDelimiterHandler(new DefaultListDelimiterHandler(',')));

		try {
			propertiesConfig = fileBasedConfigBuilder.getConfiguration();
		} catch (ConfigurationException e) {
			throw new IllegalStateException("Cannot create configuration from " + propFile + ".", e);
		}
	}


	@Value("${mpf.jgroups.config}")
	private Resource jGroupsConfig;
	public Resource getJGroupsConfig() {
		return jGroupsConfig;
	}


	@Value("${mpf.jgroups.channel.name}")
	private String channelName;
	public String getChannelName() {
		return channelName;
	}


	public String getThisMpfNode() {
		return propertiesConfig.getString("mpf.this.node");
	}


	@Value("${min.service.timeup.millis}")
	private int minServiceUpTimeMillis;
	public int getMinServiceUpTimeMillis() {
		return minServiceUpTimeMillis;
	}


	@Value("${mpf.node.status.http.port}")
	private int nodeStatusHttpPort;
	public int getNodeStatusHttpPort() {
		return nodeStatusHttpPort;
	}


	@Value("${mpf.node.status.page.enabled}")
	private boolean nodeStatusPageEnabled;
	public boolean isNodeStatusPageEnabled() {
		return nodeStatusPageEnabled;
	}


	@Value("${streaming.job.ini.dir}")
	private File iniFilesDir;
	public Path getIniFilesDir() {
		return iniFilesDir.toPath();
	}


	@Value("${streaming.process.max.restarts}")
	private int streamingProcessMaxRestarts;
	public int getStreamingProcessMaxRestarts() {
		return streamingProcessMaxRestarts;
	}


	//TODO: For future use. Untested.
//	@Value("${streaming.frame.reader.executable}")
//	private String streamingFrameReaderExecutable;
//	public String getStreamingFrameReaderExecutable() {
//		return streamingFrameReaderExecutable;
//	}
//
//
//	@Value("${streaming.video.writer.executable}")
//	private String streamingVideoWriterExecutable;
//	public String getStreamingVideoWriterExecutable() {
//		return streamingVideoWriterExecutable;
//	}
//
//
//	@Value("${streaming.component.executable}")
//	private String streamingComponentExecutor;
//	public String getStreamingComponentExecutor() {
//		return streamingComponentExecutor;
//	}

	@Value("${streaming.component.executable}")
	private String streamingComponentExecutor;
	public String getStreamingComponentExecutor() {
		return streamingComponentExecutor;
	}

	@Value("${plugin.dir}")
	private String pluginDir;
	public String getPluginDir() {
		return pluginDir;
	}
}
