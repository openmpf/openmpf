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

package org.mitre.mpf.test;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.hamcrest.Description;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtil {

    private TestUtil() {

    }

    public static <T> T whereArg(Predicate<T> matchPredicate) {
        return CustomMatcher.of(matchPredicate);
    }


    public static String nonBlank() {
        return CustomMatcher.of("Non-blank String", s -> s != null && !s.trim().isEmpty());
    }


    public static <T> T anyNonNull() {
        return CustomMatcher.of("Any not null", Objects::nonNull);
    }

    public static String eqIgnoreCase(String expected) {
        return CustomMatcher.of(expected + " (case insensitive)", s -> s.equalsIgnoreCase(expected));
    }


    public static <T, C extends Collection<T>> C collectionContaining(Predicate<T> matchPredicate) {
        return CustomMatcher.of("Collection containing", c -> c.stream().anyMatch(matchPredicate));
    }

    public static <T, C extends Collection<T>> C nonEmptyCollection() {
        return CustomMatcher.of("Non-empty Collection", c -> !c.isEmpty());
    }

    public static <K, V, M extends Map<K, V>> M nonEmptyMap() {
        return CustomMatcher.of("Non-empty Map", m -> !m.isEmpty());
    }


    private static class CustomMatcher<T> extends ArgumentMatcher<T> {

        private final Predicate<T> _matchPred;
        private final String _description;

        private CustomMatcher(Predicate<T> matchPred, String description) {
            _matchPred = matchPred;
            _description = description;
        }

        @Override
        public boolean matches(Object o) {
            //noinspection unchecked
            return _matchPred.test((T) o);
        }

        @Override
        public void describeTo(Description description) {
            if (_description == null) {
                super.describeTo(description);
            }
            else {
                description.appendText(_description);
            }
        }

        public static <T> T of(Predicate<T> matchPred)  {
            return of(null, matchPred);
        }

        public static <T> T of(String description, Predicate<T> matchPred) {
            return Mockito.argThat(new CustomMatcher<>(matchPred, description));
        }
    }

    public static TransientJob setupJob(
            long jobId, SystemPropertiesSnapshot systemPropertiesSnapshot,
            InProgressBatchJobsService inProgressJobs, IoUtils ioUtils) {
        return setupJob(jobId, systemPropertiesSnapshot, inProgressJobs, ioUtils, Collections.emptyMap(),
                        Collections.emptyMap());
    }

    public static TransientJob setupJob(
            long jobId, SystemPropertiesSnapshot systemPropertiesSnapshot,
            InProgressBatchJobsService inProgressJobs, IoUtils ioUtils,
            Map<String, String> jobProperties, Map<String, Map<String, String>> algorithmProperties) {

        TransientAction dummyAction = new TransientAction(
                "dummyAction", "dummyDescription", "dummyAlgo", Collections.emptyMap());
        TransientStage dummyStageDet = new TransientStage(
                "dummydummy", "dummyDescription", ActionType.DETECTION, Collections.singletonList(dummyAction));

        TransientPipeline dummyPipeline = new TransientPipeline(
                "dummyPipeline", "dummyDescription", Collections.singletonList(dummyStageDet));
        URI mediaUri = ioUtils.findFile("/samples/video_01.mp4");
        TransientMedia media = new TransientMediaImpl(
                234234, mediaUri.toString(), UriScheme.FILE, Paths.get(mediaUri), Collections.emptyMap(), null);

        return inProgressJobs.addJob(
                jobId,
                "234234",
                systemPropertiesSnapshot,
                dummyPipeline,
                1,
                false,
                null,
                null,
                Collections.singletonList(media),
                jobProperties,
                algorithmProperties);
    }


    public static boolean almostEqual(double x, double y, double epsilon) {
        return Math.abs(x - y) < epsilon;
    }


    public static boolean almostEqual(double x, double y) {
        return almostEqual(x, y, 0.01);
    }


    public static URI findFile(String path) {
        try {
            return TestUtil.class.getResource(path).toURI();
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public interface ThrowingRunnable {
        public void run() throws Exception;
    }

    public static <TEx extends Exception> TEx assertThrows(Class<TEx> expectedExceptionType,
                                                           ThrowingRunnable runnable) {
        try {
            runnable.run();
        }
        catch (Exception ex) {
            if (expectedExceptionType.isInstance(ex)) {
                return (TEx) ex;
            }
            throw new AssertionError(String.format(
                    "Expected an instance of %s to be thrown, but an instance of %s was thrown instead.",
                    expectedExceptionType.getName(), ex.getClass().getName()), ex);
        }
        throw new AssertionError(String.format("Expected an instance of %s to be thrown, but nothing was thrown.",
                                               expectedExceptionType.getName()));
    }


    public static Exchange createTestExchange() {
        return createTestExchange(new DefaultMessage(), new DefaultMessage());
    }

    public static Exchange createTestExchange(Message inMessage, Message outMessage) {
        Exchange exchange = mock(Exchange.class);
        when(exchange.getIn())
                .thenReturn(inMessage);
        when(exchange.getOut())
                .thenReturn(outMessage);
        return exchange;
    }
}


