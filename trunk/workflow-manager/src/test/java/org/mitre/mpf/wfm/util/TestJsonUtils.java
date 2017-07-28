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

package org.mitre.mpf.wfm.util;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.JsonMediaInputObject;

import org.mitre.mpf.wfm.enums.MpfConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.mitre.mpf.wfm.data.entities.transients.TransientStream;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestJsonUtils {

  protected static final Logger log = LoggerFactory.getLogger(TestJsonUtils.class);

  private static final int MINUTES = 1000 * 60; // 1000 milliseconds/second & 60 seconds/minute.

  @Autowired
  private ApplicationContext context;

  @Autowired
  private JsonUtils jsonUtils;

    /**
     * Test serialize and deserialize a TransientStream using JsonUtils
     */
    @Test(timeout = 2*MINUTES)
    public void runTestTransientStreamSerialization() throws Exception {

        TransientStream transientStream1 = new TransientStream(1L,"rtsp://home/mpf/openmpf-projects/openmpf/trunk/mpf-system-tests/src/test/resources/samples/person/obama-basketball.mp4");
        TransientStream transientStream2 = jsonUtils.deserialize(jsonUtils.serialize(transientStream1),TransientStream.class);

        boolean test_id_check = transientStream1.equals(transientStream2);
        Assert.assertTrue("JsonUtils test serialize,deserialize of a default TransientStream failed the id check",test_id_check);
        boolean test_deep_check = transientStream1.equalsAllFields(transientStream2);
        Assert.assertTrue("JsonUtils test serialize,deserialize of a default TransientStream failed the serialization/deserialization check",test_deep_check);

        transientStream1 = new TransientStream(3L,"RTSP://home/mpf/openmpf-projects/openmpf/trunk/mpf-system-tests/src/test/resources/samples/person/obama-basketball.mp4");
        transientStream1.setSegmentSize(500);
        transientStream1.setMessage("some message here");
        Map <String,String> mediaProperties = new <String,String>  HashMap();
        mediaProperties.put(MpfConstants.HORIZONTAL_FLIP_PROPERTY,"true");
        transientStream1.setMediaProperties(mediaProperties);
        transientStream1.addMetadata("someMetaDataKeyHere","someMetaDataValueHere");
        transientStream1.setFailed(true);
        transientStream2 = jsonUtils.deserialize(jsonUtils.serialize(transientStream1),TransientStream.class);

        test_id_check = transientStream1.equals(transientStream2);
        Assert.assertTrue("JsonUtils test serialize,deserialize of a TransientStream with non-default values failed the id check",test_id_check);
        test_deep_check = transientStream1.equalsAllFields(transientStream2);
        Assert.assertTrue("JsonUtils test serialize,deserialize of a TransientStream with non-default values failed the serialization/deserialization check",test_deep_check);

        log.info("TestJsonUtils:runTestTransientStreamSerialization(), completed id and serialize/deserialize tests of TransientStreams");
    }

}
