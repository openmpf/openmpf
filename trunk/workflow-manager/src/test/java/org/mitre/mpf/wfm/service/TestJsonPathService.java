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

package org.mitre.mpf.wfm.service;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestJsonPathService {

    private static final Set<String> ALL_STRINGS = Set.of(
            "hello", "world", "message", "a", "b",
            "content-1", "content-2", "content-3", "other");

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    private final JsonPathService _jsonPathService
            = new JsonPathService(_objectMapper);


    @Test
    public void testStringExtraction() throws IOException {
        var evaluator = _jsonPathService.load(createTestJson());

        assertStringsExtracted("$", ALL_STRINGS, evaluator);
        assertStringsExtracted("$.*", ALL_STRINGS, evaluator);
        assertStringsExtracted("$..*", ALL_STRINGS, evaluator);
        assertStringsExtracted("$.key1", ALL_STRINGS, evaluator);
        assertStringsExtracted("$.key1['key2', 'key3', 'key4', 'key8']", ALL_STRINGS, evaluator);

        assertStringsExtracted("$.key1.key2", Set.of("hello", "world"), evaluator);
        assertStringsExtracted("$.key1.key2[0]", Set.of("hello"), evaluator);
        assertStringsExtracted("$.key1.key2[1]", Set.of("world"), evaluator);

        assertStringsExtracted("$.key1.key3", Set.of("message"), evaluator);

        assertStringsExtracted("$.key1.key4.first()", Set.of("a"), evaluator);
        assertStringsExtracted("$.key1.key4.last()", Set.of("b"), evaluator);

        assertStringsExtracted(
                "$.key1.keys()",
                Set.of("key2", "key3", "key4", "key5", "key6", "key7", "key8"),
                evaluator);

        assertStringsExtracted(
                "$.key1.key8[?(@.type == 'message')].content",
                Set.of("content-1", "content-2"),
                evaluator);
    }

    @Test
    public void testNoMatchesForExtraction() throws IOException {
        var evaluator = _jsonPathService.load(createTestJson());
        assertStringsExtracted("$.asdf", Set.of(), evaluator);
    }

    private void assertStringsExtracted(
            String jsonPathExpr,
            Set<String> expected,
            JsonPathEvaluator evaluator) {
        var actual = evaluator.evalAndExtractStrings(jsonPathExpr)
            .collect(toSet());
        assertThat(actual).isEqualTo(expected);
    }


    @Test
    public void testReplaceAll() throws IOException {
        assertAllReplaced("$");
        assertAllReplaced("$.*");
        assertAllReplaced("$..*");
        assertAllReplaced("$.key1");
        assertAllReplaced("$.key1['key2', 'key3', 'key4', 'key7', 'key8']");
    }


    private void assertAllReplaced(String jsonPathExpr) throws IOException {
        var replacements = ALL_STRINGS
                .stream()
                .collect(toMap(Function.identity(), s -> s + '|' + s));
        var actual = getUpdated(jsonPathExpr, replacements);

        var expectedOutputString = """
            {
                "key1": {
                    "key2": [
                        "hello|hello",
                        "world|world"
                    ],
                    "key3": "message|message",
                    "key4": ["a|a", "b|b"],
                    "key5": 5,
                    "key6": 6.5,
                    "key7": "hello|hello",
                    "key8": [
                        {"type": "message|message", "content": "content-1|content-1"},
                        {"type": "message|message", "content": "content-2|content-2"},
                        {"type": "other|other", "content": "content-3|content-3"},
                    ]
                }
            }""";
        var expected = _objectMapper.readValue(expectedOutputString, Object.class);
        assertThat(actual).isEqualTo(expected);
    }


    private Object getUpdated(String jsonPathExpr, Map<String, String> replacements) throws IOException {
        var evaluator = _jsonPathService.load(createTestJson());
        evaluator.replaceStrings(jsonPathExpr, x -> Optional.ofNullable(replacements.get(x)));

        var outputPath = _tempFolder.newFile().toPath();
        try (var out = Files.newOutputStream(outputPath)) {
            evaluator.writeTo(out);
        }
        return _objectMapper.readValue(outputPath.toFile(), Object.class);
    }


    @Test
    public void testReplaceSpecific() throws IOException {
        var replacements = Map.of(
                "content-1", "qwer",
                "content-2", "asdf");
        var actual = getUpdated("$.key1.key8[?(@.type == 'message')].content", replacements);
        var expectedOutputString = """
            {
                "key1": {
                    "key2": [
                        "hello",
                        "world"
                    ],
                    "key3": "message",
                    "key4": ["a", "b"],
                    "key5": 5,
                    "key6": 6.5,
                    "key7": "hello",
                    "key8": [
                        {"type": "message", "content": "qwer"},
                        {"type": "message", "content": "asdf"},
                        {"type": "other", "content": "content-3"},
                    ]
                }
            }""";
        var expected = _objectMapper.readValue(expectedOutputString, Object.class);
        assertThat(actual).isEqualTo(expected);
    }


    private Path createTestJson() throws IOException {
        var json = """
            {
                "key1": {
                    "key2": [
                        "hello",
                        "world"
                    ],
                    "key3": "message",
                    "key4": ["a", "b"],
                    "key5": 5,
                    "key6": 6.5,
                    "key7": "hello",
                    "key8": [
                        {"type": "message", "content": "content-1"},
                        {"type": "message", "content": "content-2"},
                        {"type": "other", "content": "content-3"},
                    ]
                }
            }""";
        var path = _tempFolder.newFile().toPath();
        return Files.writeString(path, json);
    }
}
