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

package org.mitre.mpf.wfm.service;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.pipelines.ValueType;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.entities.persistent.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.core.io.PathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestWorkflowPropertyService {

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    @Rule
    public final TemporaryFolder _temporaryFolder = new TemporaryFolder();

    private Path workflowPropertiesFile;

    @Before
    public void init() throws IOException {
        workflowPropertiesFile = _temporaryFolder.newFile().toPath();
        when(_mockPropertiesUtil.getWorkflowPropertiesFile())
                .thenReturn(new PathResource(workflowPropertiesFile));
    }


    // Not initialized in init() so tests have a chance to populate workflowPropertiesFile
    private WorkflowPropertyService getWorkflowPropertyService() throws IOException {
        return new WorkflowPropertyService(_mockPropertiesUtil, ObjectMapperFactory.customObjectMapper());
    }

    private WorkflowPropertyService getServiceWithValidProperties() throws IOException {
        when(_mockPropertiesUtil.lookup("my.property"))
                .thenReturn("my property2 value");

        return getServiceWithPropertyJson(
                propertyJson("PROP1", "descr1", "INT", "5", null, "VIDEO"),
                propertyJson("PROP2", "descr2", "STRING", null, "my.property", "VIDEO", "AUDIO"));
    }


    private void writeProperties(String... propertyJson) throws IOException {
        String combinedJson = '[' + String.join(", ", propertyJson) + ']';

        Files.writeString(workflowPropertiesFile, combinedJson);
    }


    private WorkflowPropertyService getServiceWithPropertyJson(String... propertyJson) throws IOException {
        writeProperties(propertyJson);
        return getWorkflowPropertyService();
    }


    private static String propertyJson(String name, String description, String type, String defaultValue,
                                       String propertiesKey, String... mediaTypes) {
        String mediaTypesJson = Stream.of(mediaTypes)
                .map(TestWorkflowPropertyService::quote)
                .collect(joining(",", "[", "]"));
        return String.format(
                "{ \"name\": %s, \"description\": %s, \"type\": %s, \"defaultValue\": %s, \"propertiesKey\": %s, \"mediaTypes\": %s}",
                quote(name), quote(description), quote(type), quote(defaultValue), quote(propertiesKey),
                mediaTypesJson);
    }


    private static String quote(String s) {
        return s == null
                ? null
                : '"' + s + '"';
    }


    @Test
    public void canDeserializeProperties() throws IOException {
        var propertyService = getServiceWithValidProperties();

        assertEquals(2, propertyService.getProperties().size());

        var prop1 = propertyService.getProperty("PROP1");
        assertEquals("PROP1", prop1.getName());
        assertEquals("descr1", prop1.getDescription());
        assertEquals(ValueType.INT, prop1.getType());
        assertEquals("5", prop1.getDefaultValue());
        assertNull(prop1.getPropertiesKey());
        assertEquals(Set.of(MediaType.VIDEO), prop1.getMediaTypes());


        var prop2 = propertyService.getProperty("PROP2");
        assertEquals("PROP2", prop2.getName());
        assertEquals("descr2", prop2.getDescription());
        assertEquals(ValueType.STRING, prop2.getType());
        assertNull(prop2.getDefaultValue());
        assertEquals("my.property", prop2.getPropertiesKey());
        assertEquals(Set.of(MediaType.VIDEO, MediaType.AUDIO), prop2.getMediaTypes());
    }


    @Test
    public void canGetAllPropertiesForMediaType() throws IOException {
        var propertyService = getServiceWithValidProperties();
        var prop1 = propertyService.getProperty("PROP1");
        var prop2 = propertyService.getProperty("PROP2");

        assertEquals(Set.of(prop1, prop2), new HashSet<>(propertyService.getProperties(MediaType.VIDEO)));
        assertEquals(List.of(prop2), propertyService.getProperties(MediaType.AUDIO));

        assertTrue(propertyService.getProperties(MediaType.IMAGE).isEmpty());
        assertTrue(propertyService.getProperties(MediaType.UNKNOWN).isEmpty());
    }


    @Test
    public void canGetPropertyByNameAndMediaType() throws IOException {
        var propertyService = getServiceWithValidProperties();
        var prop1 = propertyService.getProperty("PROP1");
        var prop2 = propertyService.getProperty("PROP2");

        assertEquals(prop1, propertyService.getProperty("PROP1", MediaType.VIDEO));
        assertNull(propertyService.getProperty("PROP1", MediaType.AUDIO));

        assertEquals(prop2, propertyService.getProperty("PROP2", MediaType.VIDEO));
        assertEquals(prop2, propertyService.getProperty("PROP2", MediaType.AUDIO));
        assertNull(propertyService.getProperty("PROP2", MediaType.IMAGE));
    }


    @Test
    public void canGetValueOfPropertyWithDefaultValue() throws IOException {
        var propertyService = getServiceWithValidProperties();

        assertEquals("5", propertyService.getPropertyValue("PROP1"));

        assertEquals("5", propertyService.getPropertyValue("PROP1", MediaType.VIDEO,
                                                           new SystemPropertiesSnapshot(Map.of())));
        assertNull(propertyService.getPropertyValue("PROP1", MediaType.AUDIO,
                                                    new SystemPropertiesSnapshot(Map.of())));
    }


    @Test
    public void canGetValueOfPropertyWithPropertiesKey() throws IOException {
        var propertyService = getServiceWithValidProperties();

        assertEquals("my property2 value", propertyService.getPropertyValue("PROP2"));
        assertEquals("my property2 value",
                     propertyService.getPropertyValue("PROP2", MediaType.VIDEO,
                                                      new SystemPropertiesSnapshot(Map.of())));
        assertEquals("my property2 value",
                     propertyService.getPropertyValue("PROP2", MediaType.AUDIO,
                                                      new SystemPropertiesSnapshot(Map.of())));

        assertNull(propertyService.getPropertyValue("PROP2", MediaType.IMAGE,
                                                    new SystemPropertiesSnapshot(Map.of())));

        assertEquals("sys prop val",
                     propertyService.getPropertyValue(
                             "PROP2", MediaType.VIDEO,
                             new SystemPropertiesSnapshot(Map.of("my.property", "sys prop val"))));
    }



    @Test
    public void throwsWhenPropertyNameIsEmpty() {
        var ex = TestUtil.assertThrows(
                IllegalStateException.class,
                () -> getServiceWithPropertyJson(
                        propertyJson("", "descr1", "INT", "5", null, "VIDEO")
                ));
        assertEquals("There is a workflow property that is missing a name.", ex.getMessage());
    }


    @Test
    public void throwsWhenPropertyNameIsNull() {
        var ex = TestUtil.assertThrows(
                IllegalStateException.class,
                () -> getServiceWithPropertyJson(
                        propertyJson(null, "descr1", "INT", "5", null, "VIDEO")
                ));
        assertEquals("There is a workflow property that is missing a name.", ex.getMessage());
    }


    @Test
    public void throwsWhenDuplicateNames() {
        var ex = TestUtil.assertThrows(
                IllegalStateException.class,
                () -> getServiceWithPropertyJson(
                        propertyJson("PROP1", "descr1", "INT", "5", null, "VIDEO"),
                        propertyJson("PROP1", "descr1", "INT", "5", null, "VIDEO")
                ));

        assertEquals("There are multiple workflow properties named: PROP1", ex.getMessage());
    }


    @Test
    public void throwsWhenMissingType() {
        var ex = TestUtil.assertThrows(
                IllegalStateException.class,
                () -> getServiceWithPropertyJson(
                        propertyJson("PROP1", "descr1", null, "5", null, "VIDEO")
                ));
        assertEquals("The type is not set for the workflow property named: PROP1", ex.getMessage());
    }


    @Test(expected = JsonMappingException.class)
    public void throwsWhenInvalidPropertyType() throws IOException {
        getServiceWithPropertyJson(
                propertyJson("PROP1", "descr1", "INVALID", "5", null, "VIDEO")
        );
    }


    @Test
    public void throwsWhenDefaultValueAndPropertiesKeyBothSet() {
        var ex = TestUtil.assertThrows(
                IllegalStateException.class,
                () -> getServiceWithPropertyJson(
                        propertyJson("PROP1", "descr1", "INT", "5", "my.property", "VIDEO")
                ));
        assertEquals("The \"PROP1\" workflow property has both defaultValue and propertiesKey set, but only one or the other may be set.",
                     ex.getMessage());
    }


    @Test
    public void throwsWhenDefaultValueAndPropertiesKeyBothNotSet() {
        var ex = TestUtil.assertThrows(
                IllegalStateException.class,
                () -> getServiceWithPropertyJson(
                        propertyJson("PROP1", "descr1", "INT", null, null, "VIDEO")
                ));
        assertEquals("Neither defaultValue nor propertiesKey was set for the \"PROP1\" workflow property, but one or the other must be set.",
                     ex.getMessage());
    }



    @Test
    public void throwsWhenNoSystemPropertyMatchesPropertiesKey() {
        var ex = TestUtil.assertThrows(
                IllegalStateException.class,
                () -> getServiceWithPropertyJson(
                        propertyJson("PROP1", "descr1", "INT", null, "missing.property", "VIDEO")
                ));
        assertEquals("The \"PROP1\" workflow property has a propertiesKey of \"missing.property\", but that property does not exist.",
                     ex.getMessage());
    }


    @Test
    public void throwsWhenNoMediaTypes() {
        var ex = TestUtil.assertThrows(
                IllegalStateException.class,
                () -> getServiceWithPropertyJson(
                        propertyJson("PROP1", "descr1", "INT", "5", null)
                ));
        assertEquals("The \"PROP1\" workflow property's mediaTypes field may not be empty.",
                     ex.getMessage());
    }


    @Test(expected = JsonMappingException.class)
    public void throwsWhenInvalidMediaType() throws IOException {
        getServiceWithPropertyJson(
                propertyJson("PROP1", "descr1", "INT", "5", null, "VIDEO", "INVALID_MEDIA_TYPE")
        );
    }
}