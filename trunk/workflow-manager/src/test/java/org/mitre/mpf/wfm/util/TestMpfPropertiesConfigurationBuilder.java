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

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("jenkins")
public class TestMpfPropertiesConfigurationBuilder {

    private static String FRAME_INTERVAL_KEY = "detection.sampling.interval";
    private static String MODELS_DIR_KEY = "detection.models.dir.path";
    private static String VERSION_KEY = "mpf.version.timestamp";
    private static String SHARE_PATH_KEY = "mpf.share.path";
    private static String TIMEOUT_KEY = "web.session.timeout";

    private static String MPF_HOME_ENV_VAR = "MPF_HOME";

    @Autowired
    MpfPropertiesConfigurationBuilder mpfPropertiesConfigurationBuilder;

    @javax.annotation.Resource(name="customPropFile")
    private Resource customPropFile;

    @Test
    public void testGetConfiguration() {
        ImmutableConfiguration mpfPropertiesconfig = mpfPropertiesConfigurationBuilder.getConfiguration();

        Assert.assertEquals(1, mpfPropertiesconfig.getInt(FRAME_INTERVAL_KEY));

        String mpfHome = System.getenv(MPF_HOME_ENV_VAR);
        Assert.assertNotNull(mpfHome);

        // NOTE: The next assert will fail if this test is run through IntelliJ unless "mvn compile" is run first.
        // That can be done on the command line, or by adding a "Run Maven Goal" to the "Before launch" section
        // of the IntelliJ test configuration and setting the "Command line" to "compile".

        // ensure value carried over from pom (maven filtering)
        Assert.assertFalse(mpfPropertiesconfig.getString(VERSION_KEY).contains(VERSION_KEY));

        // ensure "${mpf.share.path}" and $MPF_HOME are interpolated
        Assert.assertEquals(mpfHome + "/share/models/", mpfPropertiesconfig.getString(MODELS_DIR_KEY));

        // attempt to resolve every property value
        Iterator<String> keyIterator = mpfPropertiesconfig.getKeys();
        while (keyIterator.hasNext()) {
            String key = keyIterator.next();

            // NOTE: config.getProperty() doesn't do interpolation;
            // this call actually resolves the value
            String value = mpfPropertiesconfig.getString(key);

            System.out.println(key + " = " + value); // DEBUG

            Assert.assertFalse(key + " has a value of \"" + value + "\", which contains \"${\". Failed interpolation?",
                    value.contains("${"));
        }
    }

    @Test
    public void testSave() throws ConfigurationException, IOException {
        ImmutableConfiguration mpfPropertiesconfig = mpfPropertiesConfigurationBuilder.getConfiguration();

        Map<String, String> newCustomPropertiesMap = new HashMap<>();
        newCustomPropertiesMap.put(FRAME_INTERVAL_KEY, "4");
        newCustomPropertiesMap.put(MODELS_DIR_KEY, "${" + SHARE_PATH_KEY +"}/new/dir/");
        newCustomPropertiesMap.put(TIMEOUT_KEY, "60");

        ImmutableConfiguration newMpfPropertiesConfig =
                mpfPropertiesConfigurationBuilder.setAndSaveCustomProperties(newCustomPropertiesMap);

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
}