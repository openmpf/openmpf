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

package org.mitre.mpf.wfm.util;

import org.apache.commons.configuration2.*;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.mitre.mpf.mvc.model.PropertyModel;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.List;

@Component
public class MpfPropertiesConfigurationBuilder {

    public static final String DETECTION_KEY_PREFIX = "detection.";

    @javax.annotation.Resource(name="customPropFile")
    private Resource customPropFile;

    @javax.annotation.Resource(name="propFiles")
    private List<Resource> propFiles;

    private CompositeConfiguration compositeConfig;

    // return a copy so that if the original is modified it won't update existing configs in use;
    // also, it's better to maintain one copy than create multiple immutable copies
    private PropertiesConfiguration configCopy;

    private PropertiesConfiguration mpfCustomPropertiesConfig;

    public MpfPropertiesConfigurationBuilder() {} // empty to allow for Spring autowiring

    // TODO: Update PropertiesUtil to use this config to get all properties
    public ImmutableConfiguration getCompleteConfiguration() {
        if (configCopy == null) {
            createCompositeConfiguration();
        }
        return configCopy;
    }

    public ImmutableConfiguration getCustomConfiguration() {
        return mpfCustomPropertiesConfig;
    }

    public ImmutableConfiguration setAndSaveCustomProperties(List<PropertyModel> propertyModels) {

        // create a new builder and configuration to write the properties to disk
        // without affecting the values of the composite config
        FileBasedConfigurationBuilder<PropertiesConfiguration> tmpMpfCustomPropertiesConfigBuilder =
                createFileBasedConfigurationBuilder(customPropFile);

        Configuration tmpMpfCustomPropertiesConfig;
        try {
            tmpMpfCustomPropertiesConfig = tmpMpfCustomPropertiesConfigBuilder.getConfiguration();
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Cannot create configuration from " + customPropFile + ".", e);
        }

        for (PropertyModel propModel : propertyModels) {
            String key = propModel.getKey();
            String value = propModel.getValue();

            // update all properties that will be written to disk
            tmpMpfCustomPropertiesConfig.setProperty(key, value);

            // update only the detection.* values in the composite config used by the WFM
            if (key.startsWith(DETECTION_KEY_PREFIX)) {
                mpfCustomPropertiesConfig.setProperty(key, value);
            }
        }

        try {
            tmpMpfCustomPropertiesConfigBuilder.save();
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Cannot save configuration to " + customPropFile + ".", e);
        }

        createConfigurationCopy();

        return configCopy; // return the updated config
    }

    private void createCompositeConfiguration() {

        compositeConfig = new CompositeConfiguration();

        // add resources in reverse order than they are specified in the application context XML;
        // the first configs that are added to the composite override property values in configs that are added later
        for (int i = propFiles.size() - 1; i >= 0; i--) {
            Resource resource = propFiles.get(i);
            try {
                if (resource.equals(customPropFile)) {
                    mpfCustomPropertiesConfig = createFileBasedConfigurationBuilder(customPropFile).getConfiguration();
                    compositeConfig.addConfiguration(mpfCustomPropertiesConfig);
                } else {
                    compositeConfig.addConfiguration(createFileBasedConfigurationBuilder(resource).getConfiguration());
                }
            } catch (ConfigurationException e) {
                throw new IllegalStateException("Cannot create configuration from " + resource + ".", e);
            }
        }

        if (mpfCustomPropertiesConfig == null) {
            throw new IllegalStateException("List of configuration properties files did not contain the " +
                    "custom configuration property file: " + propFiles);
        }

        createConfigurationCopy();
    }

    private FileBasedConfigurationBuilder<PropertiesConfiguration> createFileBasedConfigurationBuilder(Resource resource) {

        URL url;
        try {
            url = resource.getURL();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot get URL from " + resource + ".", e);
        }

        FileBasedConfigurationBuilder<PropertiesConfiguration> fileBasedConfigBuilder =
                new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class);

        Parameters configBuilderParameters = new Parameters();
        fileBasedConfigBuilder.configure(configBuilderParameters.fileBased().setURL(url)
                .setListDelimiterHandler(new DefaultListDelimiterHandler(',')));

        return fileBasedConfigBuilder;
    }

    private void createConfigurationCopy() {
        PropertiesConfiguration tmpConfig = new PropertiesConfiguration();

        // this will copy over each property one at a time,
        // essentially generating a "flat" config from the composite config
        ConfigurationUtils.copy(compositeConfig, tmpConfig);

        configCopy = tmpConfig; // assignment is atomic
    }
}