/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

package org.mitre.mpf.mvc.util;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestCloseableMdc {

    private static final Logger LOG = LoggerFactory.getLogger(TestCloseableMdc.class);

    @BeforeClass
    public static void initClass() {
        ThreadUtil.start();
    }

    @After
    public void cleanup() {
        MDC.clear();
    }

    @Test
    public void testNesting() {
        MDC.put("key1", "value1");
        var initialContext = MDC.getCopyOfContextMap();
        try (var ctx1 = CloseableMdc.all()) {
            // Make sure CloseableMdc.all() maintains existing map.
            assertEquals("value1", MDC.get("key1"));
            MDC.put("key1", "key1-replacement");
            // Make sure keys can be changed inside block.
            // Later make sure change was reverted.
            assertEquals("key1-replacement", MDC.get("key1"));

            MDC.put("jobId", "123");
            try (var ctx2 = CloseableMdc.job(456)) {
                // Make sure CloseableMdc.job replaces jobId even if it was added some other way.
                assertEquals("456", MDC.get("jobId"));
                // Make sure nesting maintains outer scopes keys
                assertEquals("key1-replacement", MDC.get("key1"));

                // This block tests similar things to above, but makes sure CloseableMdc can be
                // nested multiple times.
                try (var ctx3 = CloseableMdc.all()) {
                    assertEquals("456", MDC.get("jobId"));
                    assertEquals("key1-replacement", MDC.get("key1"));
                    MDC.put("key1", "key1-replacement2");
                    assertEquals("key1-replacement2", MDC.get("key1"));

                    try (var ctx4 = CloseableMdc.job(789)) {
                        assertEquals("789", MDC.get("jobId"));
                        assertEquals("key1-replacement2", MDC.get("key1"));

                        // Make sure CloseableMdc.all completely replaces context even when nested.
                        try (var ctx5 = CloseableMdc.all(initialContext)) {
                            // Make sure changes were reverted
                            assertEquals("value1", MDC.get("key1"));
                            // Make sure jobId was correctly removed from map.
                            assertEquals(1, MDC.getCopyOfContextMap().size());
                        }
                        assertEquals("789", MDC.get("jobId"));
                        assertEquals("key1-replacement2", MDC.get("key1"));
                    }

                    assertEquals("456", MDC.get("jobId"));
                    assertEquals("key1-replacement2", MDC.get("key1"));

                    // Clear to make sure nesting correctly restores context even after clearing.
                    MDC.clear();
                    assertTrue(MDC.getCopyOfContextMap().isEmpty());
                }
                assertEquals("456", MDC.get("jobId"));
                assertEquals("key1-replacement", MDC.get("key1"));
            }
            assertEquals("123", MDC.get("jobId"));
        }
        // Make sure changes were reverted
        assertEquals("value1", MDC.get("key1"));
        // Make sure jobId was correctly removed from map.
        assertEquals(1, MDC.getCopyOfContextMap().size());
    }


    @Test
    public void testMdcThreadingWithChainedFutures() {
        // Save the initial context
        var initialContext = MDC.getCopyOfContextMap();

        CompletableFuture<String> job123;
        try (var mdc = CloseableMdc.job(123)) {
            assertAndLogJobId(123, "initial");
            job123 = ThreadUtil.runAsync(() -> assertAndLogJobId(123, "runAsync"))
                    .thenRun(() -> assertAndLogJobId(123, "thenRun"))
                    .thenRunAsync(() -> assertAndLogJobId(123, "thenRunAsync"))
                    .thenRun(() -> assertAndLogJobId(123, "thenRun again"))
                    .thenRunAsync(() -> assertAndLogJobId(123, "thenRunAsync again"))
                    .thenApplyAsync(x -> MDC.get("jobId"));
        }

        var job456 = MdcUtil.job(456, () ->
                ThreadUtil.runAsync(() -> assertAndLogJobId(456, "runAsync"))
                .thenRun(() -> assertAndLogJobId(456, "thenRun"))
                .thenRunAsync(() -> assertAndLogJobId(456, "thenRunAsync"))
                .thenRun(() -> assertAndLogJobId(456, "thenRun again"))
                .thenRunAsync(() -> assertAndLogJobId(456, "thenRunAsync again"))
                .thenApplyAsync(x -> MDC.get("jobId")));

        assertEquals("123", job123.join());
        assertEquals("456", job456.join());

        // Compare the initial context with the MDC context after the job completes
        assertEquals(initialContext, MDC.getCopyOfContextMap());

        // Make sure threads in pool do not retain context after a job completes.
        var threadCtxSize = ThreadUtil.callAsync(
                () -> MDC.getCopyOfContextMap().size());
        assertEquals(initialContext.size(), threadCtxSize.join().intValue());
    }


    @Test
    public void testMdcThreadingWithChainedFuturesAndDelay() {
        CompletableFuture<String> job1123;
        try (var mdc = CloseableMdc.job(1123)) {
            assertAndLogJobId(1123, "initial");
            job1123 = ThreadUtil.runAsync(54, TimeUnit.MILLISECONDS,
                                         () -> assertAndLogJobId(1123, "runAsync delayed"))
                    .thenRun(() -> assertAndLogJobId(1123, "thenRun"))
                    .thenRunAsync(() -> assertAndLogJobId(1123, "thenRunAsync"))
                    .thenRun(() -> assertAndLogJobId(1123, "thenRun again"))
                    .thenRunAsync(() -> assertAndLogJobId(1123, "thenRunAsync delayed again"),
                                  ThreadUtil.delayedExecutor(50, TimeUnit.MILLISECONDS))
                    .thenRun(() -> assertAndLogJobId(1123, "thenRun after delay"))
                    .thenRunAsync(() -> assertAndLogJobId(1123, "thenRunAsync after delay"))
                    .thenApplyAsync(x -> MDC.get("jobId"));
        }

        CompletableFuture<String> job1456;
        try (var mdc = CloseableMdc.job(1456)) {
            assertAndLogJobId(1456, "initial");
            job1456 = ThreadUtil.runAsync(50, TimeUnit.MILLISECONDS,
                                         () -> assertAndLogJobId(1456, "runAsync delayed"))
                    .thenRun(() -> assertAndLogJobId(1456, "thenRun"))
                    .thenRunAsync(() -> assertAndLogJobId(1456, "thenRunAsync"))
                    .thenRun(() -> assertAndLogJobId(1456, "thenRun again"))
                    .thenRunAsync(() -> assertAndLogJobId(1456, "thenRunAsync delayed again"),
                                  ThreadUtil.delayedExecutor(50, TimeUnit.MILLISECONDS))
                    .thenRun(() -> assertAndLogJobId(1456, "thenRun after delay"))
                    .thenRunAsync(() -> assertAndLogJobId(1456, "thenRunAsync after delay"))
                    .thenApplyAsync(x -> MDC.get("jobId"));
        }

        assertEquals("1123", job1123.join());
        assertEquals("1456", job1456.join());

        assertTrue(MDC.getCopyOfContextMap().isEmpty());
        // Make sure threads in pool do not retain context after a job completes.
        var threadCtxSize = ThreadUtil.callAsync(
                () -> MDC.getCopyOfContextMap().size());
        assertEquals(0, threadCtxSize.join().intValue());
    }

    private static void assertAndLogJobId(long expectedJobId, String message) {
        assertEquals(String.valueOf(expectedJobId), MDC.get("jobId"));
        LOG.info("{} {}", expectedJobId, message);
    }
}
