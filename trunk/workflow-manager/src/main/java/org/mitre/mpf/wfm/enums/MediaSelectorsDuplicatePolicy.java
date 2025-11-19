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

package org.mitre.mpf.wfm.enums;

import static java.util.stream.Collectors.joining;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.mitre.mpf.wfm.WfmProcessingException;

public enum MediaSelectorsDuplicatePolicy implements Function<Set<String>, Optional<String>> {
    LONGEST {
        public Optional<String> apply(Set<String> items) {
            return items.stream()
                .max(Comparator.comparingInt(String::length));
        }
    },
    ERROR {
        public Optional<String> apply(Set<String> items) {
            if (items.size() < 2) {
                return items.stream().findAny();
            }
            var nonBlankItems = items.stream()
                .filter(s -> !s.isBlank())
                .limit(2)
                .toList();

            return switch (nonBlankItems.size()) {
                case 0 -> items.stream().findAny();
                case 1 -> nonBlankItems.stream().findAny();
                default -> throw new WfmProcessingException(
                    "Could not create media selector output because one selected element produced multiple outputs and %s was set to ERROR"
                    .formatted(MpfConstants.MEDIA_SELECTORS_DUPLICATE_POLICY));
            };
        }
    },
    JOIN {
        public Optional<String> apply(Set<String> items) {
            if (items.isEmpty()) {
                return Optional.empty();
            }
            var joined = items.stream().collect(joining(" | "));
            return Optional.of(joined);
        }
    };
}
