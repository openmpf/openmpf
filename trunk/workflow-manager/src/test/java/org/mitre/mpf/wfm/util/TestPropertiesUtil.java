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

package org.mitre.mpf.wfm.util;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mitre.mpf.mvc.model.PropertyModel;
import org.mitre.mpf.wfm.enums.ArtifactExtractionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.WritableResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("jenkins")
public class TestPropertiesUtil {

    private static final String FRAME_INTERVAL_KEY = "detection.sampling.interval";
    private static final String MODELS_DIR_KEY = "detection.models.dir.path";
    private static final String SHARE_PATH_KEY = "mpf.share.path";
    private static final String TIMEOUT_KEY = "web.session.timeout";

    private static final String MPF_HOME_ENV_VAR = "MPF_HOME";

    @Autowired
    MpfPropertiesConfigurationBuilder mpfPropertiesConfigurationBuilder;

    @javax.annotation.Resource(name="customPropFile")
    private FileSystemResource customPropFile;

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Test
    public void testBuilderGetConfiguration() {
        ImmutableConfiguration mpfPropertiesconfig = mpfPropertiesConfigurationBuilder.getCompleteConfiguration();

        Assert.assertEquals(1, mpfPropertiesconfig.getInt(FRAME_INTERVAL_KEY));

        String mpfHome = System.getenv(MPF_HOME_ENV_VAR);
        Assert.assertNotNull(mpfHome);

        // ensure "${mpf.share.path}" and $MPF_HOME are interpolated
        Assert.assertEquals(mpfHome + "/share/models/", mpfPropertiesconfig.getString(MODELS_DIR_KEY));

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
        ImmutableConfiguration mpfPropertiesconfig = mpfPropertiesConfigurationBuilder.getCompleteConfiguration();

        List<PropertyModel> newCustomPropertyModels = new ArrayList<>();
        newCustomPropertyModels.add(new PropertyModel(FRAME_INTERVAL_KEY, "4", false));
        newCustomPropertyModels.add(new PropertyModel(MODELS_DIR_KEY, "${" + SHARE_PATH_KEY +"}/new/dir/", false));
        newCustomPropertyModels.add(new PropertyModel(TIMEOUT_KEY, "60", false));

        ImmutableConfiguration newMpfPropertiesConfig =
                mpfPropertiesConfigurationBuilder.setAndSaveCustomProperties(newCustomPropertyModels);

        String mpfHome = System.getenv(MPF_HOME_ENV_VAR);
        Assert.assertNotNull(mpfHome);

        // ensure that current config isn't modified
        Assert.assertEquals(1, mpfPropertiesconfig.getInt(FRAME_INTERVAL_KEY));
        Assert.assertEquals(mpfHome + "/share/models/", mpfPropertiesconfig.getString(MODELS_DIR_KEY));
        Assert.assertEquals(30, mpfPropertiesconfig.getInt(TIMEOUT_KEY));

        // get updated config
        mpfPropertiesconfig = newMpfPropertiesConfig; // mpfPropertiesConfigurationBuilder.getConfiguration();

        // ensure detection value sticks
        Assert.assertEquals(4, mpfPropertiesconfig.getInt(FRAME_INTERVAL_KEY));

        // ensure that interpolation is performed on recently-set values
        Assert.assertEquals(mpfHome + "/share/new/dir/", mpfPropertiesconfig.getString(MODELS_DIR_KEY));

        // ensure non-detection value doesn't stick
        Assert.assertEquals(30, mpfPropertiesconfig.getInt(TIMEOUT_KEY));

        // ensure all values written to disk
        Configurations configs = new Configurations();

        FileBasedConfigurationBuilder<PropertiesConfiguration> mpfCustomPropertiesConfigBuilder =
                configs.propertiesBuilder(customPropFile.getURL());
        Configuration mpfCustomPropertiesConfig = mpfCustomPropertiesConfigBuilder.getConfiguration();

        Assert.assertEquals(4, mpfCustomPropertiesConfig.getInt(FRAME_INTERVAL_KEY));
        Assert.assertEquals("${" + SHARE_PATH_KEY +"}/new/dir/", mpfCustomPropertiesConfig.getString(MODELS_DIR_KEY));
        Assert.assertEquals(60, mpfCustomPropertiesConfig.getInt(TIMEOUT_KEY));

        // reset
        mpfCustomPropertiesConfig.clear();
        mpfCustomPropertiesConfigBuilder.save();
    }

    @Test
    public void testPropertiesUtilGetters() {
        Assert.assertTrue(propertiesUtil.isAmqBrokerEnabled());

        Assert.assertEquals(2, propertiesUtil.getAmqBrokerPurgeWhiteList().size());

        Assert.assertEquals(ArtifactExtractionPolicy.VISUAL_TYPES_ONLY, propertiesUtil.getArtifactExtractionPolicy());

        Assert.assertTrue(WritableResource.class.isAssignableFrom(propertiesUtil.getAlgorithmDefinitions().getClass()));
        Assert.assertTrue(propertiesUtil.getAlgorithmDefinitions().exists());
    }

    @Test
    public void testPropertiesUtilSave() {
        List<PropertyModel> newCustomPropertyModels = new ArrayList<>();
        newCustomPropertyModels.add(new PropertyModel(FRAME_INTERVAL_KEY, "4", false));
        newCustomPropertyModels.add(new PropertyModel(TIMEOUT_KEY, "60", false));

        // test current values
        Assert.assertEquals(1, propertiesUtil.getSamplingInterval());
        Assert.assertEquals(30, propertiesUtil.getWebSessionTimeout());

        propertiesUtil.getCustomProperties().forEach(m -> Assert.assertFalse(m.getKey() +
                " should not need require a WFM restart", m.getNeedsRestart()));

        propertiesUtil.setAndSaveCustomProperties(newCustomPropertyModels);
        List<PropertyModel> properties = propertiesUtil.getCustomProperties();

        // ensure detection value sticks
        Assert.assertEquals(4, propertiesUtil.getSamplingInterval());
        properties.stream().filter(m -> m.getKey().equals(FRAME_INTERVAL_KEY)).forEach(m -> Assert.assertFalse(m.getNeedsRestart()));

        // ensure non-detection value doesn't stick
        Assert.assertEquals(30, propertiesUtil.getWebSessionTimeout());
        properties.stream().filter(m -> m.getKey().equals(TIMEOUT_KEY)).forEach(m -> Assert.assertTrue(m.getNeedsRestart()));

        // reset
        newCustomPropertyModels.clear();
        newCustomPropertyModels.add(new PropertyModel(FRAME_INTERVAL_KEY, "1", false));
        newCustomPropertyModels.add(new PropertyModel(TIMEOUT_KEY, "30", false));

        propertiesUtil.setAndSaveCustomProperties(newCustomPropertyModels);
        properties = propertiesUtil.getCustomProperties();

        Assert.assertEquals(1, propertiesUtil.getSamplingInterval());
        properties.stream().filter(m -> m.getKey().equals(FRAME_INTERVAL_KEY)).forEach(m -> Assert.assertFalse(m.getNeedsRestart()));

        Assert.assertEquals(30, propertiesUtil.getWebSessionTimeout());
        properties.stream().filter(m -> m.getKey().equals(TIMEOUT_KEY)).forEach(m -> Assert.assertFalse(m.getNeedsRestart()));
    }
}
