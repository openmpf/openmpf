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

package org.mitre.mpf.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.assertj.core.api.Condition;
import org.assertj.core.api.FutureAssert;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.mvc.WebMvcConfig;
import org.mitre.mpf.wfm.ValidatorConfig;
import org.mitre.mpf.wfm.service.ConstraintValidationService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.springframework.core.io.PathResource;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class TestUtil {

    public static final Duration FUTURE_DURATION = Duration.ofMillis(30);

    private TestUtil() {

    }

    public static String nonBlank() {
        return describedArgThat(s -> s != null && !s.isBlank(), "nonBlank()");
    }

    public static <T, C extends Collection<T>> C collectionContaining(Predicate<T> matchPredicate) {
        return describedArgThat(
                c -> c.stream().anyMatch(matchPredicate),
                "anyMatch(%s)", matchPredicate);
    }


    public static <T, C extends Collection<? extends T>> C nonEmptyCollection() {
        return describedArgThat(c -> !c.isEmpty(), "nonEmptyCollection()");
    }

    public static <K, V, M extends Map<K, V>> M nonEmptyMap() {
        return describedArgThat(m -> !m.isEmpty(), "nonEmptyMap()");
    }

    public static <T> T describedArgThat(Predicate<T> pred, String description, Object... args) {
        return ArgumentMatchers.argThat(new ArgumentMatcher<>() {

            public boolean matches(T argument) {
                return pred.test(argument);
            }

            public String toString() {
                return description.formatted(args);
            }
        });
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

    public static Path findFilePath(String path) {
        return Paths.get(findFile(path));
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
        return new DefaultExchange(new DefaultCamelContext());
    }

    public static void initPipelineDataFiles(PropertiesUtil mockPropertiesUtil, TemporaryFolder temporaryFolder)
            throws IOException {
        Path rootTempDir = temporaryFolder.getRoot().toPath();

        Path algorithmsPath = rootTempDir.resolve("Algorithms.json");
        when(mockPropertiesUtil.getAlgorithmDefinitions())
                .thenReturn(new PathResource(algorithmsPath));
        Files.writeString(algorithmsPath, "[]");

        Path actionsPath = rootTempDir.resolve("Actions.json");
        when(mockPropertiesUtil.getActionDefinitions())
                .thenReturn(new PathResource(actionsPath));
        Files.writeString(actionsPath, "[]");

        Path tasksPath = rootTempDir.resolve("Tasks.json");
        when(mockPropertiesUtil.getTaskDefinitions())
                .thenReturn(new PathResource(tasksPath));
        Files.writeString(tasksPath, "[]");

        Path pipelinesPath = rootTempDir.resolve("Pipelines.json");
        when(mockPropertiesUtil.getPipelineDefinitions())
                .thenReturn(new PathResource(pipelinesPath));
        Files.writeString(pipelinesPath, "[]");
    }


    public static boolean nodeManagerEnabled() {
        String property = System.getProperty("node.manager.disabled");
        if (property == null) {
            return true;
        }
        return !Boolean.parseBoolean(property);
    }

    public static void assumeNodeManagerEnabled() {
        Assume.assumeTrue("Skipping this test because it requires Node Manager but it is disabled.",
                           nodeManagerEnabled());
    }

    public static ConstraintValidationService createConstraintValidator() {
        var validator = new ValidatorConfig().localValidatorFactoryBean();
        validator.afterPropertiesSet();
        return new ConstraintValidationService(validator);
    }

    public static MockMvc initMockMvc(Object controller) {
        var setup = MockMvcBuilders.standaloneSetup(controller);
        var converters = new ArrayList<HttpMessageConverter<?>>();
        converters.add(new ResourceHttpMessageConverter());
        new WebMvcConfig().extendMessageConverters(converters);
        setup.setMessageConverters(converters.toArray(HttpMessageConverter[]::new));
        return setup.build();
    }


    private static Condition<Future<?>> doesNotCompleteWithin(Duration duration) {
        return new Condition<>(
            f -> {
                try {
                    f.get(duration.toNanos(), TimeUnit.NANOSECONDS);
                    return false;
                }
                catch (TimeoutException e) {
                    return true;
                }
                catch (ExecutionException e) {
                    return false;
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            },
            "does not complete within %s",
            duration
        );
    }

    private static <T> FutureAssert<T> assertDoesNotCompleteWithin(
            Future<T> future, Duration duration) {
        return assertThat(future).satisfies(doesNotCompleteWithin(duration));
    }

    public static <T> FutureAssert<T> assertNotDone(Future<T> future) {
        return assertDoesNotCompleteWithin(future, FUTURE_DURATION);
    }

    public static boolean equalAfterTruncate(Instant x, Instant y) {
        return x.truncatedTo(ChronoUnit.MILLIS).equals(y.truncatedTo(ChronoUnit.MILLIS));
    }
}
