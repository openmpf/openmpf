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

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.mst.TestSystemWithDefaultConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.mitre.mpf.wfm.data.entities.transients.TransientStream;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestJsonUtils extends TestSystemWithDefaultConfig {

    protected static final Logger log = LoggerFactory.getLogger(TestJsonUtils.class);

    @Autowired
    private JsonUtils jsonUtils;

    /**
     * Test serialize and deserialize a TransientStream using JsonUtils
     */
    @Test(timeout = 2*MINUTES)
    public void runTestTransientStreamSerialization() throws Exception {

        TransientStream transientStream1 = new TransientStream(1L,"rtsp://home/mpf/openmpf-projects/openmpf/trunk/mpf-system-tests/src/test/resources/samples/person/obama-basketball.mp4");
        TransientStream transientStream2 = jsonUtils.deserialize(jsonUtils.serialize(transientStream1),TransientStream.class);

        Assert.assertEquals("JsonUtils test serialize,deserialize of a TransientStream with lower-case rtsp protocol failed",transientStream1,transientStream2);

        TransientStream transientStream3 = new TransientStream(1L,"RTSP://home/mpf/openmpf-projects/openmpf/trunk/mpf-system-tests/src/test/resources/samples/person/obama-basketball.mp4");
        TransientStream transientStream4 = jsonUtils.deserialize(jsonUtils.serialize(transientStream3),TransientStream.class);

        Assert.assertEquals("JsonUtils test serialize,deserialize of a TransientStream with upper-case RTSP protocol failed",transientStream3,transientStream4);

        log.info("runTestTransientStreamSerialization(): Finished serialize,deserialize test of upper and lower-case RTSP protocol TransientStreams");
    }


}
