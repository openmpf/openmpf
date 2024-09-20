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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;

public class JsonPathEvaluator {

    private final DocumentContext _parsedJson;

    private final ObjectMapper _objectMapper;


    public JsonPathEvaluator(DocumentContext parsedJson, ObjectMapper objectMapper) {
        _parsedJson = parsedJson;
        _objectMapper = objectMapper;
    }

    public Object json() {
        return _parsedJson.json();
    }

    public Stream<String> evalAndExtractStrings(String jsonPathExpression) {
        try {
            var evalResult = _parsedJson.read(jsonPathExpression);
            return extractStrings(evalResult);
        }
        catch (PathNotFoundException e) {
            return Stream.of();
        }
    }


    private static Stream<String> extractStrings(Object obj) {
        if (obj instanceof String string) {
            return Stream.of(string);
        }
        else if (obj instanceof Collection<?> collection) {
            return collection.stream().flatMap(JsonPathEvaluator::extractStrings);
        }
        else if (obj instanceof Map<?, ?> map) {
            return map.values().stream().flatMap(JsonPathEvaluator::extractStrings);
        }
        else {
            return Stream.of();
        }
    }


    public void replaceStrings(String jsonPathExpression, UnaryOperator<String> stringMapper) {
        if (jsonPathExpression.equals("$")) {
            // The JSON path library does not allow you to call .map() when the expression is just
            // "$".
            replaceStringsInternal(_parsedJson.json(), stringMapper);
        }
        else {
            _parsedJson.map(
                    jsonPathExpression, (obj, config) -> replaceStringsInternal(obj, stringMapper));
        }
    }

    private static Object replaceStringsInternal(
            Object current,
            UnaryOperator<String> stringMapper) {
        if (current instanceof String string) {
            return stringMapper.apply(string);
        }
        else if (current instanceof List<?> list) {
            asObjList(list).replaceAll(o -> replaceStringsInternal(o, stringMapper));
            return list;
        }
        else if (current instanceof Map<?, ?> map) {
            asObjMap(map).replaceAll((k, v) -> replaceStringsInternal(v, stringMapper));
            return map;
        }
        else {
            return current;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asObjList(List<?> wildcardList) {
        return (List<Object>) wildcardList;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> asObjMap(Map<?, ?> wildcardMap) {
        return (Map<Object, Object>) wildcardMap;
    }

    public void writeTo(OutputStream out) throws IOException {
        _objectMapper.writeValue(out, _parsedJson.json());
    }
}
