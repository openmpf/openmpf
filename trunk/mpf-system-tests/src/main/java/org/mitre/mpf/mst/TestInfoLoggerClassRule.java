/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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


package org.mitre.mpf.mst;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TestInfoLoggerClassRule extends TestWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(TestInfoLoggerClassRule.class);

    private static int _numStarted;

    private static final Map<Class<?>, Integer> _testCounts = new HashMap<>();
    private static final Map<Class<?>, Integer> _numStartedPerClass = new HashMap<>();


    @Override
    protected void starting(Description description) {
        // description.testCount() will always be 1 in the method rule, so we need to get the count with a class rule.
        _testCounts.put(description.getTestClass(), description.testCount());
        _numStartedPerClass.put(description.getTestClass(), 0);
    }


    public TestWatcher methodRule() {
        return new TestWatcher() {
            protected void starting(Description description) {
                int numStartedThisClass = _numStartedPerClass.merge(description.getTestClass(), 1, Integer::sum);
                _numStarted++;

                LOG.info("\n\n=== Starting test #{}: {} (test #{} of {} in {}) ===\n",
                         _numStarted, description.getDisplayName(), numStartedThisClass,
                         _testCounts.get(description.getTestClass()), description.getTestClass().getName());
            }

            @Override
            protected void succeeded(Description description) {
                LOG.info("\n\n=== {} succeeded ===", description.getDisplayName());
            }

            @Override
            protected void failed(Throwable e, Description description) {
                LOG.error("\n\n=== {} failed: {} ===", description.getDisplayName(), e.getMessage());
            }
        };
    }
}
