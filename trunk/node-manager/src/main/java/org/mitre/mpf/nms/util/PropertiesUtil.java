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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

@Component
public class PropertiesUtil {

	@Autowired
	private ApplicationContext appContext;

	@javax.annotation.Resource(name="propFile")
	private ClassPathResource propFile;

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

	protected Iterator<String> getKeys() {
		return propertiesConfig.getKeys();
	}

	public String lookup(String key) {
		return propertiesConfig.getString(key);
	}

	public Resource getJGroupsConfig() {
		return appContext.getResource(propertiesConfig.getString("mpf.jgroups.config"));
	}

	public String getChannelName() {
		return propertiesConfig.getString("mpf.jgroups.channel.name");
	}

	public String getThisMpfNode() {
		return propertiesConfig.getString("mpf.this.node");
	}

	public int getMinServiceUpTimeMillis() {
		return propertiesConfig.getInt("min.service.timeup.millis");
	}

	public int getNodeStatusHttpPort() {
		return propertiesConfig.getInt("mpf.node.status.http.port");
	}

	public boolean isNodeStatusPageEnabled() {
		return propertiesConfig.getBoolean("mpf.node.status.page.enabled");
	}

	public Path getIniFilesDir() {
		return Paths.get(propertiesConfig.getString("streaming.job.ini.dir"));
	}

	public int getStreamingProcessMaxRestarts() {
		return propertiesConfig.getInt("streaming.process.max.restarts");
	}

	/* // TODO: For future use. Untested.
	public String getStreamingFrameReaderExecutable() {
		return propertiesConfig.getString("streaming.frame.reader.executable");
	}

	public String getStreamingVideoWriterExecutable() {
		return propertiesConfig.getString("streaming.video.writer.executable");
	}

	public String getStreamingComponentExecutor() {
		return propertiesConfig.getString("streaming.component.executable");
	}
	*/

	public String getStreamingComponentExecutor() {
		return propertiesConfig.getString("streaming.component.executable");
	}

	public String getPluginDir() {
		return propertiesConfig.getString("plugin.dir");
	}
}
