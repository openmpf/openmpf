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

package org.mitre.mpf.test;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)

@TestExecutionListeners(
        listeners = SpringTestWithMocks.DirtyContextBeforeAndAfterTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@ContextConfiguration
public @interface SpringTestWithMocks {

    @AliasFor(attribute = "classes", annotation = ContextConfiguration.class)
    Class<?>[] value() default {};


    // When test classes both require a Spring ApplicationContext and mocks, the mocks need to be added to the
    // ApplicationContext so the mocks can be @Autowired in to the classes being tested. By default when multiple test
    // classes run, Spring will reuse the same Application context for each test class.
    // This can cause issues when some tests mock a service and other don't. If a test class that uses mocks runs first,
    // all the other test classes will have mocks in their ApplicationContext, even if they are supposed to use the
    // actual non-mocked service.
    // The @DirtiesContext is supposed to handle the situation where you don't want the ApplicationContext to be reused.
    // The issue is that @DirtiesContext can only mark the ApplicationContext either before or after the test class
    // runs. This class marks the context as dirty both before and after the test class runs.
    // The class below was the suggested solution of a Spring Test developer on StackOverflow.
    // https://stackoverflow.com/questions/39277040/make-applicationcontext-dirty-before-and-after-test-class
    static class DirtyContextBeforeAndAfterTestExecutionListener extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void beforeTestClass(TestContext testContext) {
            testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.CURRENT_LEVEL);
        }

        @Override
        public void afterTestClass(TestContext testContext) {
            testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.CURRENT_LEVEL);
        }
    }
}
