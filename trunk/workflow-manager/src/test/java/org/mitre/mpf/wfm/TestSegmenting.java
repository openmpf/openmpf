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

package org.mitre.mpf.wfm;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.wfm.util.TimePair;
import org.mitre.mpf.wfm.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSegmenting {
	private static final Logger log = LoggerFactory.getLogger(TestSegmenting.class);
	private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

	private TimeUtils timeUtils = new TimeUtils();

	private SortedSet<TimePair> timePairs = new TreeSet<>();
    private TimePair source = new TimePair(0, 15);
    private TimePair target = new TimePair(10, 20);
    private TimePair result = new TimePair(0, 20);

	public TestSegmenting() {
		timePairs.add(new TimePair(0, 24));
	}

	@Test(timeout = 5 * MINUTES)
	public void TestNoSplits() throws Exception {
		List<TimePair> results = timeUtils.createSegments(timePairs, 25, 1, 100);
		Assert.assertTrue(results.get(0).getStartInclusive() == 0 && results.get(0).getEndInclusive() == 24);
	}

	@Test(timeout = 5 * MINUTES)
	public void TestSplits() throws Exception {
		List<TimePair> results = timeUtils.createSegments(timePairs, 10, 1, 100);
		Assert.assertTrue(results.get(0).getStartInclusive() == 0 && results.get(0).getEndInclusive() == 9);
		Assert.assertTrue(results.get(1).getStartInclusive() == 10 && results.get(1).getEndInclusive() == 19);
		Assert.assertTrue(results.get(2).getStartInclusive() == 20 && results.get(2).getEndInclusive() == 24);
	}

    @Test(timeout = 1 * MINUTES)
    public void TestOverlap() throws Exception {
        int minGapBetweenSegments = 1;
        Assert.assertTrue(timeUtils.overlaps(source, target, minGapBetweenSegments));
    }

	@Test(timeout = 1 * MINUTES)
    public void TestMerge() throws Exception {
        Assert.assertTrue(timeUtils.merge(source, target).equals(result));
    }
}
