/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;

@Service
public class WorkflowPropertyService {

    private final PropertiesUtil _propertiesUtil;

    private final ImmutableMap<String, WorkflowProperty> _indexedByName;

    private final ImmutableMultimap<MediaType, WorkflowProperty> _indexedByMediaType;


    @Inject
    public WorkflowPropertyService(PropertiesUtil propertiesUtil, ObjectMapper objectMapper) throws IOException {
        this(propertiesUtil, objectMapper, propertiesUtil.getWorkflowPropertiesFile());
    }

    protected WorkflowPropertyService(PropertiesUtil propertiesUtil, ObjectMapper objectMapper, Resource propertiesFile)
            throws IOException {
        _propertiesUtil = propertiesUtil;

        List<WorkflowProperty> workflowPropertiesList;
        try (InputStream inputStream = propertiesFile.getInputStream()) {
            workflowPropertiesList = objectMapper.readValue(inputStream,
                    new TypeReference<List<WorkflowProperty>>() { });
        }
        validate(workflowPropertiesList, propertiesUtil);

        _indexedByName = Maps.uniqueIndex(workflowPropertiesList, WorkflowProperty::getName);


        var multimapBuilder = ImmutableMultimap.<MediaType, WorkflowProperty>builder();
        for (var property : workflowPropertiesList) {
            for (var mediaType : property.getMediaTypes()) {
                multimapBuilder.put(mediaType, property);
            }
        }
        _indexedByMediaType = multimapBuilder.build();
    }


    private static void validate(Iterable<WorkflowProperty> properties, PropertiesUtil propertiesUtil) {
        var propertyNames = new HashSet<String>();

        for (var property : properties) {
            if (StringUtils.isBlank(property.getName())) {
                throw new IllegalStateException("There is a workflow property that is missing a name.");
            }

            if (!propertyNames.add(property.getName())) {
                throw new IllegalStateException("There are multiple workflow properties named: " + property.getName());
            }

            if (property.getType() == null) {
                throw new IllegalStateException(
                        "The type is not set for the workflow property named: " + property.getName());
            }

            if (property.getDefaultValue() != null && property.getPropertiesKey() != null) {
                throw new IllegalStateException(String.format(
                        "The \"%s\" workflow property has both defaultValue and propertiesKey set, but only one or the other may be set.",
                        property.getName()));

            }

            if (property.getDefaultValue() == null && property.getPropertiesKey() == null) {
                throw new IllegalStateException(String.format(
                        "Neither defaultValue nor propertiesKey was set for the \"%s\" workflow property, but one or the other must be set.",
                        property.getName()));

            }

            if (property.getPropertiesKey() != null && propertiesUtil.lookup(property.getPropertiesKey()) == null) {
                throw new IllegalStateException(String.format(
                        "The \"%s\" workflow property has a propertiesKey of \"%s\", but that property does not exist.",
                        property.getName(), property.getPropertiesKey()));
            }

            if (property.getMediaTypes().isEmpty()) {
                throw new IllegalStateException(String.format(
                        "The \"%s\" workflow property's mediaTypes field may not be empty.", property.getName()));
            }
        }
    }


    public ImmutableCollection<WorkflowProperty> getProperties() {
        return _indexedByName.values();
    }


    public ImmutableCollection<WorkflowProperty> getProperties(MediaType mediaType) {
        return _indexedByMediaType.get(mediaType);
    }


    public WorkflowProperty getProperty(String name, MediaType type) {
        var property = _indexedByName.get(name.toUpperCase());
        return property != null && property.getMediaTypes().contains(type)
                ? property
                : null;
    }

    public WorkflowProperty getProperty(String name) {
        return _indexedByName.get(name.toUpperCase());
    }


    public String getPropertyValue(String propertyName, MediaType mediaType,
                                   SystemPropertiesSnapshot propertiesSnapshot) {

        return getPropertyValue(getProperty(propertyName, mediaType), propertiesSnapshot);
    }


    public String getPropertyValue(String propertyName) {
        return getPropertyValue(_indexedByName.get(propertyName.toUpperCase()), null);
    }


    private String getPropertyValue(WorkflowProperty property, SystemPropertiesSnapshot systemPropertiesSnapshot) {
        if (property == null) {
            return null;
        }

        if (property.getDefaultValue() != null) {
            return property.getDefaultValue();
        }

        if (systemPropertiesSnapshot != null) {
            String snapshotValue = systemPropertiesSnapshot.lookup(property.getPropertiesKey());
            if (snapshotValue != null) {
                return snapshotValue;
            }
        }

        return _propertiesUtil.lookup(property.getPropertiesKey());
    }
}
