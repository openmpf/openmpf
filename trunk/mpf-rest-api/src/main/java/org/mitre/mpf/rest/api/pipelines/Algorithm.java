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


package org.mitre.mpf.rest.api.pipelines;

import java.util.List;
import java.util.OptionalInt;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotBlank;
import org.mitre.mpf.interop.util.ValidName;
import org.mitre.mpf.rest.api.util.AllNotBlank;
import org.mitre.mpf.rest.api.util.MethodReturnsTrue;
import org.mitre.mpf.rest.api.util.Utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;


@MethodReturnsTrue(
        method = "supportsBatchOrStreaming",
        message = "must support batch processing, stream processing, or both")
public record Algorithm(
        @ValidName String name,
        @NotBlank String description,
        @NotNull ActionType actionType,
        @NotBlank String trackType,
        OptionalInt outputChangedCounter,
        @NotNull @Valid Requires requiresCollection,
        @NotNull @Valid Provides providesCollection,
        boolean supportsBatchProcessing,
        boolean supportsStreamProcessing
        ) implements PipelineElement {

    public Algorithm {
        name = Utils.trimAndUpper(name);
        description = Utils.trim(description);
        trackType = Utils.trimAndUpper(trackType);
    }

    public AlgorithmProperty property(String name) {
        return providesCollection.properties()
                .stream()
                .filter(p -> p.name().equalsIgnoreCase(name))
                .findAny()
                .orElse(null);
    }

    @JsonIgnore
    public boolean supportsBatchOrStreaming() {
        return supportsBatchProcessing || supportsStreamProcessing;
    }


    public record Requires(@NotNull @Valid List<@AllNotBlank String> states) {
        public Requires {
            states = Utils.trimAndUpper(states, ImmutableList.toImmutableList());
        }
    }


    public record Provides(
            @NotNull @Valid List<@AllNotBlank String> states,
            @NotNull @Valid List<AlgorithmProperty> properties
    ) {
        public Provides {
            states = Utils.trimAndUpper(states, ImmutableList.toImmutableList());
            properties = ImmutableList.copyOf(properties);
        }
    }
}
