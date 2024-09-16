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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.mvc.model.PropertyModel;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.WritableResource;


public class TestPropertiesUtil {

    private static final String FRAME_INTERVAL_KEY = "detection.sampling.interval";
    private static final String MODELS_DIR_KEY = "detection.models.dir.path";
    private static final String SHARE_PATH_KEY = "mpf.share.path";
    private static final String CACHE_COUNT_KEY = "static.s3.client.cache.count";

    private static final String MPF_HOME_ENV_VAR = "MPF_HOME";

    private MpfPropertiesConfigurationBuilder _mpfPropertiesConfigurationBuilder;

    private FileSystemResource _customPropFile;

    private PropertiesUtil _propertiesUtil;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private String _sharePath;

    @Before
    public void init() throws IOException {
        var shareFolder = _tempFolder.newFolder("share").toPath();
        _sharePath = shareFolder.toString();

        var unitTestProps = shareFolder.resolve("mpf-unit-test.properties");
        Files.write(unitTestProps, List.of(
            "mpf.share.path=%s\n".formatted(shareFolder.toAbsolutePath()),
            "test.env=${env:MPF_HOME}"
        ));

        _customPropFile = new FileSystemResource(shareFolder.resolve("mpf-custom.properties"));

        _mpfPropertiesConfigurationBuilder = new MpfPropertiesConfigurationBuilder(
            _customPropFile,
            List.of(
                _customPropFile,
                new FileSystemResource(unitTestProps),
                new ClassPathResource("/properties/mpf-private.properties"),
                new ClassPathResource("/properties/mpf-jenkins.properties"),
                new ClassPathResource("/properties/mpf.properties")
            ));

        var appContext = mock(ApplicationContext.class);
        var loader = new DefaultResourceLoader();

        when(appContext.getResource(any()))
            .then(inv -> loader.getResource(inv.getArgument(0, String.class)));

        _propertiesUtil = new PropertiesUtil(
            appContext,
            _mpfPropertiesConfigurationBuilder,
            null,
            new FileSystemResource(shareFolder.resolve("config/mediaType.properties")),
            new FileSystemResource(shareFolder.resolve("config/user.properties")));

    }

    @Test
    public void testBuilderGetConfiguration() {
        ImmutableConfiguration mpfPropertiesconfig = _mpfPropertiesConfigurationBuilder.getCompleteConfiguration();

        Assert.assertEquals(1, mpfPropertiesconfig.getInt(FRAME_INTERVAL_KEY));

        String mpfHome = System.getenv(MPF_HOME_ENV_VAR);
        Assert.assertNotNull(mpfHome);

        // ensure "${mpf.share.path}" and $MPF_HOME are interpolated
        Assert.assertEquals(_sharePath + "/models/", mpfPropertiesconfig.getString(MODELS_DIR_KEY));
        Assert.assertEquals(mpfHome, mpfPropertiesconfig.getString("test.env"));

        // attempt to resolve every property value
        Iterator<String> keyIterator = mpfPropertiesconfig.getKeys();
        while (keyIterator.hasNext()) {
            String key = keyIterator.next();

            // this call does interpolation
            String value = mpfPropertiesconfig.getString(key);

            System.out.println(key + " = " + value); // DEBUG

            Assert.assertFalse(key + " has a value of \"" + value + "\", which contains \"${\". Failed interpolation?",
                    value.contains("${"));
        }
    }

    @Test
    public void testBuilderSave() throws ConfigurationException, IOException {
        ImmutableConfiguration mpfPropertiesconfig = _mpfPropertiesConfigurationBuilder.getCompleteConfiguration();

        List<PropertyModel> newCustomPropertyModels = new ArrayList<>();
        newCustomPropertyModels.add(new PropertyModel(FRAME_INTERVAL_KEY, "4", false));
        newCustomPropertyModels.add(new PropertyModel(MODELS_DIR_KEY, "${" + SHARE_PATH_KEY +"}/new/dir/", false));
        newCustomPropertyModels.add(new PropertyModel(CACHE_COUNT_KEY, "60", false));

        ImmutableConfiguration newMpfPropertiesConfig =
                _mpfPropertiesConfigurationBuilder.setAndSaveCustomProperties(newCustomPropertyModels);

        // ensure that current config isn't modified
        Assert.assertEquals(1, mpfPropertiesconfig.getInt(FRAME_INTERVAL_KEY));
        Assert.assertEquals(_sharePath + "/models/", mpfPropertiesconfig.getString(MODELS_DIR_KEY));
        Assert.assertEquals(40, mpfPropertiesconfig.getInt(CACHE_COUNT_KEY));

        // get updated config
        mpfPropertiesconfig = newMpfPropertiesConfig; // mpfPropertiesConfigurationBuilder.getConfiguration();

        // ensure detection value sticks
        Assert.assertEquals(4, mpfPropertiesconfig.getInt(FRAME_INTERVAL_KEY));

        // ensure that interpolation is performed on recently-set values
        Assert.assertEquals(_sharePath + "/new/dir/", mpfPropertiesconfig.getString(MODELS_DIR_KEY));

        // ensure non-detection value doesn't stick
        Assert.assertEquals(40, mpfPropertiesconfig.getInt(CACHE_COUNT_KEY));

        // ensure all values written to disk
        Configurations configs = new Configurations();

        FileBasedConfigurationBuilder<PropertiesConfiguration> mpfCustomPropertiesConfigBuilder =
                configs.propertiesBuilder(_customPropFile.getURL());
        Configuration mpfCustomPropertiesConfig = mpfCustomPropertiesConfigBuilder.getConfiguration();

        Assert.assertEquals(4, mpfCustomPropertiesConfig.getInt(FRAME_INTERVAL_KEY));
        Assert.assertEquals("${" + SHARE_PATH_KEY +"}/new/dir/", mpfCustomPropertiesConfig.getString(MODELS_DIR_KEY));
        Assert.assertEquals(60, mpfCustomPropertiesConfig.getInt(CACHE_COUNT_KEY));

        // reset
        mpfCustomPropertiesConfig.clear();
        mpfCustomPropertiesConfigBuilder.save();
    }

    @Test
    public void testPropertiesUtilGetters() {
        Assert.assertEquals(ArtifactExtractionPolicy.VISUAL_TYPES_ONLY, _propertiesUtil.getArtifactExtractionPolicy());

        Assert.assertTrue(WritableResource.class.isAssignableFrom(_propertiesUtil.getAlgorithmDefinitions().getClass()));
        Assert.assertTrue(_propertiesUtil.getAlgorithmDefinitions().exists());
    }

    @Test
    public void testPropertiesUtilSave() {
        List<PropertyModel> newCustomPropertyModels = new ArrayList<>();
        newCustomPropertyModels.add(new PropertyModel(FRAME_INTERVAL_KEY, "4", false));
        newCustomPropertyModels.add(new PropertyModel(CACHE_COUNT_KEY, "60", false));

        // test current values
        Assert.assertEquals(1, _propertiesUtil.getSamplingInterval());
        Assert.assertEquals(40, _propertiesUtil.getS3ClientCacheCount());

        _propertiesUtil.getCustomProperties().forEach(m -> Assert.assertFalse(m.getKey() +
                " should not need require a WFM restart", m.getNeedsRestart()));

        _propertiesUtil.setAndSaveCustomProperties(newCustomPropertyModels);
        List<PropertyModel> properties = _propertiesUtil.getCustomProperties();

        // ensure detection value sticks
        Assert.assertEquals(4, _propertiesUtil.getSamplingInterval());
        properties.stream().filter(m -> m.getKey().equals(FRAME_INTERVAL_KEY)).forEach(m -> Assert.assertFalse(m.getNeedsRestart()));

        // ensure non-detection value doesn't stick
        Assert.assertEquals(40, _propertiesUtil.getS3ClientCacheCount());
        properties.stream().filter(m -> m.getKey().equals(CACHE_COUNT_KEY)).forEach(m -> Assert.assertTrue(m.getNeedsRestart()));

        // reset
        newCustomPropertyModels.clear();
        newCustomPropertyModels.add(new PropertyModel(FRAME_INTERVAL_KEY, "1", false));
        newCustomPropertyModels.add(new PropertyModel(CACHE_COUNT_KEY, "40", false));

        _propertiesUtil.setAndSaveCustomProperties(newCustomPropertyModels);
        properties = _propertiesUtil.getCustomProperties();

        Assert.assertEquals(1, _propertiesUtil.getSamplingInterval());
        properties.stream().filter(m -> m.getKey().equals(FRAME_INTERVAL_KEY)).forEach(m -> Assert.assertFalse(m.getNeedsRestart()));

        Assert.assertEquals(40, _propertiesUtil.getS3ClientCacheCount());
        properties.stream().filter(m -> m.getKey().equals(CACHE_COUNT_KEY)).forEach(m -> Assert.assertFalse(m.getNeedsRestart()));
    }
}
