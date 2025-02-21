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


package org.mitre.mpf.rest.api.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class Utils {

    private Utils() {
    }

    public static String trimAndUpper(String s) {
        return s == null
                ? null
                : s.trim().toUpperCase();
    }

    public static String trim(String s) {
        return s == null
                ? null
                : s.trim();
    }

    public static Optional<String> trimAndUpper(Optional<String> optString) {
        return optString.map(Utils::trimAndUpper);
    }


    public static <R> R trimAndUpper(Collection<String> strings, Collector<String, ?, R> collector) {
        return Optional.ofNullable(strings)
                .stream()
                .flatMap(Collection::stream)
                .map(Utils::trimAndUpper)
                .collect(collector);
    }

    public static <T> ImmutableSet<T> toImmutableSet(Set<T> set) {
        return Optional.ofNullable(set)
            .map(ImmutableSet::copyOf)
            .orElseGet(ImmutableSet::of);
    }

    public static ImmutableList<String> trimAndUpper(List<String> strings) {
        return trimAndUpper(strings, ImmutableList.toImmutableList());
    }

    public static <T> ImmutableList<T> toImmutableList(Collection<T> collection) {
        return Optional.ofNullable(collection)
            .map(ImmutableList::copyOf)
            .orElseGet(ImmutableList::of);
    }

    public static <K, V> ImmutableMap<K, V> toImmutableMap(Map<K, V> map) {
        return Optional.ofNullable(map)
            .map(ImmutableMap::copyOf)
            .orElseGet(ImmutableMap::of);
    }
}
