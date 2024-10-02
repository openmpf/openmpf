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

import java.util.Optional;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import org.mitre.mpf.rest.api.pipelines.ValueType;

@Entity
@IdClass(SubjectPropertyPrimaryKey.class)
public class DbSubjectComponentProperty {

    @Id
    @ManyToOne
    @JoinColumn(name="component_name", nullable=false)
    private DbSubjectComponent component;


    @Id
    private String name;
    public String getName() { return name; }


    @Column(nullable = false)
    private String description;
    public String getDescription() { return description; }


    private String defaultValue;
    public Optional<String> getDefaultValue() { return Optional.ofNullable(defaultValue); }


    private String propertiesKey;
    public Optional<String> getPropertiesKey() { return Optional.ofNullable(propertiesKey); }


    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ValueType type;
    public ValueType getType() { return type; }


    // Hibernate requires a no-arg constructor.
    public DbSubjectComponentProperty() {
    }

    public DbSubjectComponentProperty(
            DbSubjectComponent component,
            String name,
            String description,
            String defaultValue,
            String propertiesKey,
            ValueType type) {
        this.component = component;
        this.name = name;
        this.description = description;
        if (defaultValue != null) {
            if (propertiesKey != null) {
                throw new IllegalArgumentException(
                    "Only one of defaultValue and propertiesKey should be non-null.");
            }
            this.defaultValue = defaultValue;
        }
        else {
            this.propertiesKey = propertiesKey;
        }
        this.type = type;
    }
}
