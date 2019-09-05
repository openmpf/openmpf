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
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.ArgumentMatchers;
import org.springframework.core.io.PathResource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtil {

    private TestUtil() {

    }

    public static String nonBlank() {
        return ArgumentMatchers.argThat(s -> s != null && !s.trim().isEmpty());
    }


    public static <T, C extends Collection<T>> C collectionContaining(Predicate<T> matchPredicate) {
        return ArgumentMatchers.argThat(c -> c.stream().anyMatch(matchPredicate));
    }

    public static <T, C extends Collection<T>> C nonEmptyCollection() {
        return ArgumentMatchers.argThat(c -> !c.isEmpty());
    }

    public static <K, V, M extends Map<K, V>> M nonEmptyMap() {
        return ArgumentMatchers.argThat(m -> !m.isEmpty());
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
}


