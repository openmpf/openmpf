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

package org.mitre.mpf.wfm.data.entities.persistent;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import org.mitre.mpf.interop.util.CompareUtils;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.rest.api.util.Utils;

public record MediaSelector(
        String expression,
        MediaSelectorType type,
        Map<String, String> selectionProperties,
        String resultDetectionProperty,
        UUID id) {

    public MediaSelector {
        selectionProperties = Utils.toImmutableMap(selectionProperties);
    }

    public MediaSelector(
            String expression,
            MediaSelectorType type,
            Map<String, String> selectionProperties,
            String resultDetectionProperty) {
        this(expression, type, selectionProperties, resultDetectionProperty, UUID.randomUUID());
    }


    private static final Comparator<MediaSelector> DEFAULT_COMPARATOR = Comparator
            .nullsFirst(Comparator
                .comparing(MediaSelector::expression)
                .thenComparing(MediaSelector::type)
                .thenComparing(MediaSelector::resultDetectionProperty)
                .thenComparing(MediaSelector::selectionProperties, CompareUtils.MAP_COMPARATOR));

    public static Comparator<MediaSelector> comparator() {
        // We return a Comparator here instead of making the class implement Comparable because
        // when implementing Comparable, you generally need to also override .equals() and
        // .hashCode() to make them consistent with .compareTo().
        return DEFAULT_COMPARATOR;
    }
}
