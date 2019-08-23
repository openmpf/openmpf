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


package org.mitre.mpf.mst;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestInfoLoggerClassRule extends TestWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(TestInfoLoggerClassRule.class);

    private int _testCount;

    private int _numStarted;


    @Override
    protected void starting(Description description) {
        // description.testCount() will always be 1 in the method rule, so we need to get the count with a class rule.
        _testCount += description.testCount();
        LOG.info("=== Discovered {} additional tests in {} ===", description.testCount(), description.getClassName());
    }


    public TestWatcher methodRule() {
        return new TestWatcher() {
            protected void starting(Description description) {
                _numStarted++;
                LOG.info("=== Starting test #{} of {}: {} ===", _numStarted, _testCount, description.getDisplayName());
            }

            @Override
            protected void succeeded(Description description) {
                LOG.info("=== {} succeeded ===", description.getDisplayName());
            }

            @Override
            protected void failed(Throwable e, Description description) {
                LOG.error("=== {} failed: {} ===", description.getDisplayName(), e.getMessage());
            }
        };
    }
}
