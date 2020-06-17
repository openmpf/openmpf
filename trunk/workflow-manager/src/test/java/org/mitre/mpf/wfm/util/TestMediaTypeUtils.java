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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mitre.mpf.wfm.enums.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("jenkins")
public class TestMediaTypeUtils {

    @Autowired
    private MediaTypeUtils mediaTypeUtils;

    @Test
    public void testParse() {
        Assert.assertEquals(MediaType.VIDEO, mediaTypeUtils.parse("application/x-matroska"));
        Assert.assertEquals(MediaType.VIDEO, mediaTypeUtils.parse("application/x-vnd.rn-realmedia"));
        Assert.assertEquals(MediaType.VIDEO, mediaTypeUtils.parse("application/mp4"));
        Assert.assertEquals(MediaType.VIDEO, mediaTypeUtils.parse("image/gif"));

        Assert.assertEquals(MediaType.IMAGE, mediaTypeUtils.parse("image/jpeg"));
        Assert.assertEquals(MediaType.AUDIO, mediaTypeUtils.parse("audio/mpeg"));
        Assert.assertEquals(MediaType.UNKNOWN, mediaTypeUtils.parse("text/plain"));
    }
}
