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

package org.mitre.mpf.wfm.util;

import com.google.common.collect.ImmutableList;
import org.apache.commons.configuration2.*;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.mitre.mpf.mvc.model.PropertyModel;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.*;

import javax.inject.Inject;
import javax.inject.Named;

@Component
public class MpfPropertiesConfigurationBuilder {

    private static final Collection<String> MUTABLE_PREFIXES = ImmutableList.of(
            "detection.",
            "http.callback.retries",
            "http.object.storage.",
            "http.callback.timeout.ms",
            "mpf.output.objects.",
            "node.auto.",
            "remote.media.download.",
            "web.broadcast.job.status.enabled",
            "web.job.polling.interval",
            "warn.",
            "markup.",
            "ties.db",
            "s3.",
            "ffprobe."
    );

    private static final Collection<String> SNAPSHOT_PREFIXES = ImmutableList.of(
            "detection.",
            "markup.",
            "http.object.storage.nginx.service.uri",
            "mpf.output.objects.artifacts.and.exemplars.only",
            "s3.");


    private final FileSystemResource _customPropFile;

    private final List<Resource> _propFiles;


    private CompositeConfiguration _mpfCompositeConfig;

    // return a snapshot to users of this class to prevent the case where a property is being set / updated at the same
    // time that properties are being read out of the config
    // this is volatile to ensure the most updated version is used across threads
    private volatile PropertiesConfiguration _mpfConfigSnapshot;

    private PropertiesConfiguration _mpfCustomPropertiesConfig;

    @Inject
    public MpfPropertiesConfigurationBuilder(
            @Named("customPropFile") FileSystemResource customPropFile,
            @Named("propFiles") List<Resource> propFiles) {
        _customPropFile = customPropFile;
        _propFiles = propFiles;
    }

    public ImmutableConfiguration getCompleteConfiguration() {
        if (_mpfConfigSnapshot == null) {
            _mpfCompositeConfig = createCompositeConfiguration();
            updateConfigurationSnapshot();
        }
        return _mpfConfigSnapshot;
    }

    public synchronized ImmutableConfiguration setAndSaveCustomProperties(List<PropertyModel> propertyModels) {

        // create a new builder and configuration to write the properties to disk
        // without affecting the values of the composite config
        FileBasedConfigurationBuilder<PropertiesConfiguration> tmpMpfCustomPropertiesConfigBuilder =
                createFileBasedConfigurationBuilder(_customPropFile);

        Configuration tmpMpfCustomPropertiesConfig;
        try {
            tmpMpfCustomPropertiesConfig = tmpMpfCustomPropertiesConfigBuilder.getConfiguration();
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Cannot create configuration from " + _customPropFile + ".", e);
        }

        for (PropertyModel propModel : propertyModels) {
            String key = propModel.getKey();
            String value = propModel.getValue();

            // update all properties that will be written to disk
            tmpMpfCustomPropertiesConfig.setProperty(key, value);

            // update only the mutable properties in the composite config used by the WFM
            if (isMutableProperty(key)) {
                _mpfCustomPropertiesConfig.setProperty(key, value);
            }
        }

        try {
            tmpMpfCustomPropertiesConfigBuilder.save();
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Cannot save configuration to " + _customPropFile + ".", e);
        }

        updateConfigurationSnapshot();

        return _mpfConfigSnapshot;
    }

    public synchronized List<PropertyModel> getCustomProperties() {

        // create a new builder and configuration to read the properties on disk
        FileBasedConfigurationBuilder<PropertiesConfiguration> tmpMpfCustomPropertiesConfigBuilder =
                createFileBasedConfigurationBuilder(_customPropFile);

        Configuration tmpMpfCustomPropertiesConfig;
        try {
            tmpMpfCustomPropertiesConfig = tmpMpfCustomPropertiesConfigBuilder.getConfiguration();
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Cannot create configuration from " + _customPropFile + ".", e);
        }

        // generate a complete list of property models and determine if a WFM restart is needed for each
        List <PropertyModel> propertyModels = new ArrayList<>();
        Iterator<String> propertyKeyIter = _mpfCompositeConfig.getKeys();
        while (propertyKeyIter.hasNext()) {
            String key = propertyKeyIter.next();

            // use uninterpolated values
            String currentValue = getRawValue(_mpfCompositeConfig, key);

            if (tmpMpfCustomPropertiesConfig.containsKey(key)) {
                String customValue = getRawValue(tmpMpfCustomPropertiesConfig, key);
                boolean needsRestart = !Objects.equals(currentValue, customValue);
                propertyModels.add(new PropertyModel(key, customValue, needsRestart));
            } else {
                propertyModels.add(new PropertyModel(key, currentValue, false));
            }
        }

        return propertyModels;
    }

    private String getRawValue(Configuration config, String key) {
        Object prop = config.getProperty(key);
        String raw = prop.toString();
        if (prop instanceof Collection<?>) {
            return raw.substring(1, raw.length()-1); // remove the beginning "[" and ending "]"
        }
        return raw;
    }

    private CompositeConfiguration createCompositeConfiguration() {

        if (!_customPropFile.exists()) {
            try {
                PropertiesUtil.createParentDir(_customPropFile);
                _customPropFile.getFile().createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create " + _customPropFile + ".", e);
            }
        }

        CompositeConfiguration compositeConfig = new CompositeConfiguration();

        // add resources in the order they are specified in the application context XML;
        // the first configs that are added to the composite override property values in configs that are added later
        for (Resource resource : _propFiles) {
            try {
                if (resource.equals(_customPropFile)) {
                    _mpfCustomPropertiesConfig = createFileBasedConfigurationBuilder(_customPropFile).getConfiguration();
                    compositeConfig.addConfiguration(_mpfCustomPropertiesConfig);
                } else if (resource.exists()){
                    compositeConfig.addConfiguration(createFileBasedConfigurationBuilder(resource).getConfiguration());
                }
            } catch (ConfigurationException e) {
                throw new IllegalStateException("Cannot create configuration from " + resource + ".", e);
            }
        }

        if (_mpfCustomPropertiesConfig == null) {
            throw new IllegalStateException("List of configuration properties files did not contain the " +
                    "custom configuration property file: " + _propFiles);
        }

        return compositeConfig;
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

    private void updateConfigurationSnapshot() {
        PropertiesConfiguration tmpConfig = new PropertiesConfiguration();

        // this will copy over each property one at a time,
        // essentially generating a "flat" config from the composite config
        ConfigurationUtils.copy(_mpfCompositeConfig, tmpConfig);

        _mpfConfigSnapshot = tmpConfig;
    }

    public static boolean propertyRequiresSnapshot(String key) {
        return SNAPSHOT_PREFIXES.stream().anyMatch(key::startsWith);
    }

    public static boolean isMutableProperty(String key) {
        return MUTABLE_PREFIXES.stream().anyMatch(key::startsWith);
    }
}
