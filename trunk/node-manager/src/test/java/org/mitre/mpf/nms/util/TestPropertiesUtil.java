/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.Iterator;

@ContextConfiguration(locations = {"classpath:applicationContext-nm.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
public class TestPropertiesUtil {

    private static final String THIS_MPF_NODE_ENV_VAR = "THIS_MPF_NODE";

    @Autowired
    private PropertiesUtil propertiesUtil;

    @Test
    public void testPropertiesUtilGetters() throws IOException {
        Assert.assertEquals(System.getenv(THIS_MPF_NODE_ENV_VAR), propertiesUtil.getThisMpfNode());

        Assert.assertTrue(Resource.class.isAssignableFrom(propertiesUtil.getJGroupsConfig().getClass()));
        Assert.assertNotNull(propertiesUtil.getJGroupsConfig().getURL());

        // attempt to resolve every property value
        Iterator<String> keyIterator = propertiesUtil.getKeys();
        while (keyIterator.hasNext()) {
            String key = keyIterator.next();
            String value = propertiesUtil.lookup(key);

            System.out.println(key + " = " + value); // DEBUG

            Assert.assertFalse(key + " has a value of \"" + value + "\", which contains \"${\". Failed interpolation?",
                    value.contains("${"));
        }
    }
}