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


package org.mitre.mpf.rest.api.pipelines;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.ScriptAssert;
import org.mitre.mpf.rest.api.util.AllNotBlank;
import org.mitre.mpf.rest.api.util.Utils;
import org.mitre.mpf.rest.api.util.ValidAlgoPropValue;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Objects;

@ScriptAssert(lang = "javascript", script = "_this.getSupportsBatchProcessing() || _this.getSupportsStreamProcessing()",
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
            @JsonProperty("requiresCollection") Requires requiresCollection,
            @JsonProperty("providesCollection") Provides providesCollection,
            @JsonProperty("supportsBatchProcessing") boolean supportsBatchProcessing,
            @JsonProperty("supportsStreamProcessing") boolean supportsStreamProcessing) {
        _name = Utils.trimAndUpper(name);
        _description = Utils.trim(description);
        _actionType = actionType;
        _requiresCollection = requiresCollection;
        _providesCollection = providesCollection;
        _supportsBatchProcessing = supportsBatchProcessing;
        _supportsStreamProcessing = supportsStreamProcessing;
    }


    public Algorithm.Property getProperty(String name) {
        return _providesCollection.getProperties()
                .stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findAny()
                .orElse(null);
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
                && Objects.equals(_requiresCollection, other._requiresCollection)
                && Objects.equals(_providesCollection, other._providesCollection)
                && Objects.equals(_supportsBatchProcessing, other._supportsBatchProcessing)
                && Objects.equals(_supportsStreamProcessing, other._supportsStreamProcessing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _description, _actionType, _requiresCollection, _providesCollection,
                            _supportsBatchProcessing, _supportsStreamProcessing);
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

        private final ImmutableList<Property> _properties;
        @NotNull @Valid
        public ImmutableList<Property> getProperties() {
            return _properties;
        }


        public Provides(
                @JsonProperty("states") Collection<String> states,
                @JsonProperty("properties") Collection<Property> properties) {
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



    @ValidAlgoPropValue
    public static class Property {

        private final String _name;
        @NotBlank
        public String getName() {
            return _name;
        }

        private final String _description;
        @NotBlank
        public String getDescription() {
            return _description;
        }

        private final ValueType _type;
        @NotNull
        public ValueType getType() {
            return _type;
        }

        // option A
        private final String _defaultValue;
        public String getDefaultValue() {
            return _defaultValue;
        }

        // option B
        private final String _propertiesKey;
        public String getPropertiesKey() {
            return _propertiesKey;
        }


        public Property(
                @JsonProperty("name") String name,
                @JsonProperty("description") String description,
                @JsonProperty("type") ValueType type,
                @JsonProperty("defaultValue") String defaultValue,
                @JsonProperty("propertiesKey") String propertiesKey) {
            _name = Utils.trimAndUpper(name);
            _description = Utils.trim(description);
            _type = type;
            _defaultValue = Utils.trim(defaultValue);
            _propertiesKey = Utils.trim(propertiesKey);
        }


        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Property)) {
                return false;
            }
            var other = (Property) obj;
            return Objects.equals(_name, other._name)
                    && Objects.equals(_description, other._description)
                    && _type == other._type
                    && Objects.equals(_defaultValue, other._defaultValue)
                    && Objects.equals(_propertiesKey, other._propertiesKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_name, _description, _type, _defaultValue, _propertiesKey);
        }
    }
}
