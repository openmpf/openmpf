/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.hibernate.validator.constraints.NotBlank;
import org.mitre.mpf.rest.api.util.AllNotBlank;
import org.mitre.mpf.rest.api.util.MethodReturnsTrue;
import org.mitre.mpf.rest.api.util.Utils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Objects;
import java.util.OptionalInt;

@MethodReturnsTrue(
        method = "supportsBatchOrStreaming",
        message = "must support batch processing, stream processing, or both")
public class Algorithm implements PipelineElement {

    private final String _name;
    @Override
    @NotBlank
    public String getName() {
        return _name;
    }

    private final String _description;
    @NotBlank
    public String getDescription() {
        return _description;
    }

    private final ActionType _actionType;
    @NotNull
    public ActionType getActionType() {
        return _actionType;
    }

    private final String _trackType;
    @NotBlank
    public String getTrackType() {
        return _trackType;
    }

    private final OptionalInt _outputChangedCounter;
    public OptionalInt getOutputChangedCounter() {
        return _outputChangedCounter;
    }

    private final Requires _requiresCollection;
    @NotNull @Valid
    public Requires getRequiresCollection() {
        return _requiresCollection;
    }

    private final Provides _providesCollection;
    @NotNull @Valid
    public Provides getProvidesCollection() {
        return _providesCollection;
    }

    private final boolean _supportsBatchProcessing;
    public boolean getSupportsBatchProcessing() {
        return _supportsBatchProcessing;
    }

    private final boolean _supportsStreamProcessing;
    public boolean getSupportsStreamProcessing() {
       return _supportsStreamProcessing;
    }


    public Algorithm(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("actionType") ActionType actionType,
            @JsonProperty("trackType") String trackType,
            @JsonProperty("outputChangedCounter") OptionalInt outputChangedCounter,
            @JsonProperty("requiresCollection") Requires requiresCollection,
            @JsonProperty("providesCollection") Provides providesCollection,
            @JsonProperty("supportsBatchProcessing") boolean supportsBatchProcessing,
            @JsonProperty("supportsStreamProcessing") boolean supportsStreamProcessing) {
        _name = Utils.trimAndUpper(name);
        _description = Utils.trim(description);
        _actionType = actionType;
        _trackType = Utils.trimAndUpper(trackType);
        _outputChangedCounter = outputChangedCounter;
        _requiresCollection = requiresCollection;
        _providesCollection = providesCollection;
        _supportsBatchProcessing = supportsBatchProcessing;
        _supportsStreamProcessing = supportsStreamProcessing;
    }


    public AlgorithmProperty getProperty(String name) {
        return _providesCollection.getProperties()
                .stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findAny()
                .orElse(null);
    }

    @JsonIgnore
    public boolean supportsBatchOrStreaming() {
        return getSupportsBatchProcessing() || getSupportsStreamProcessing();
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Algorithm)) {
            return false;
        }
        var other = (Algorithm) obj;
        return Objects.equals(_name, other._name)
                && Objects.equals(_description, other._description)
                && _actionType == other._actionType
                && Objects.equals(_trackType, other._trackType)
                && Objects.equals(_outputChangedCounter, other._outputChangedCounter)
                && Objects.equals(_requiresCollection, other._requiresCollection)
                && Objects.equals(_providesCollection, other._providesCollection)
                && Objects.equals(_supportsBatchProcessing, other._supportsBatchProcessing)
                && Objects.equals(_supportsStreamProcessing, other._supportsStreamProcessing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                _name, _description, _actionType, _trackType, _outputChangedCounter,
                _requiresCollection, _providesCollection, _supportsBatchProcessing,
                _supportsStreamProcessing);
    }




    public static class Requires {
        private final ImmutableList<String> _states;
        @NotNull @Valid
        public ImmutableList<@AllNotBlank String> getStates() {
            return _states;
        }

        public Requires(
                @JsonProperty("states") Collection<String> states) {
            _states = Utils.trimAndUpper(states, ImmutableList.toImmutableList());
        }


        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Requires)) {
                return false;
            }
            Requires other = (Requires) obj;
            return Objects.equals(_states, other._states);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_states);
        }
    }


    public static class Provides {
        private final ImmutableList<String> _states;
        @NotNull @Valid
        public ImmutableList<@AllNotBlank String> getStates() {
            return _states;
        }

        private final ImmutableList<AlgorithmProperty> _properties;
        @NotNull @Valid
        public ImmutableList<AlgorithmProperty> getProperties() {
            return _properties;
        }


        public Provides(
                @JsonProperty("states") Collection<String> states,
                @JsonProperty("properties") Collection<AlgorithmProperty> properties) {
            _states = Utils.trimAndUpper(states, ImmutableList.toImmutableList());
            _properties = ImmutableList.copyOf(properties);
        }


        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Provides)) {
                return false;
            }
            var other = (Provides) obj;
            return Objects.equals(_states, other._states)
                    && Objects.equals(_properties, other._properties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_states, _properties);
        }
    }
}
