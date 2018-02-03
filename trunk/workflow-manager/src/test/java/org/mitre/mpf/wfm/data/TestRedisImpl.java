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

package org.mitre.mpf.wfm.data;

import java.util.HashMap;
import java.util.Map;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * A series of test cases for RedisImpl functionality.  These all assume Redis itself works properly, and verify that
 * assignment and retrieval of values are correctly matched.
 */
@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
@RunListener.ThreadSafe
public class TestRedisImpl extends TestCase {

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private Redis redis;

    @Autowired
    private IoUtils ioUtils;

    @Test
    public void testSetJobStatus() throws Exception {
        final long jobId = 112235;
        Exchange exchange = new DefaultExchange(camelContext);
        TransientJob job = TestUtil.setupJob(jobId, redis, ioUtils);
        exchange.getIn().setBody(jsonUtils.serialize(job));
        redis.setJobStatus(jobId, BatchJobStatusType.IN_PROGRESS_WARNINGS);
        Assert.assertEquals(BatchJobStatusType.IN_PROGRESS_WARNINGS, redis.getBatchJobStatus(jobId));
    }

    @Test
    public void testAlgorithmJobProperties() throws Exception {
        final long jobId = 112236;
        Exchange exchange = new DefaultExchange(camelContext);
        TransientJob job = TestUtil.setupJob(jobId, redis, ioUtils);

        HashMap<String, Map> overriddenAlgorithmProperties = new HashMap<>();
        HashMap<String, String> props = new HashMap<>();
        props.put("DUMMY_PROPERTY", "VALUE");

        overriddenAlgorithmProperties.put("ALGORITHM", props);

        job.setOverriddenAlgorithmProperties(overriddenAlgorithmProperties);
        redis.persistJob(job);

        TransientJob retrievedJob = redis.getJob(jobId);
        assertNotNull(retrievedJob.getOverriddenAlgorithmProperties());
        assertFalse(retrievedJob.getOverriddenAlgorithmProperties().isEmpty());
        assertNotNull(retrievedJob.getOverriddenAlgorithmProperties().get("ALGORITHM"));
        assertFalse(retrievedJob.getOverriddenAlgorithmProperties().get("ALGORITHM").isEmpty());
        assertEquals("VALUE", retrievedJob.getOverriddenAlgorithmProperties().get("ALGORITHM").get("DUMMY_PROPERTY"));
    }
}