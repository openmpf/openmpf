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

package org.mitre.mpf.wfm.service.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mitre.mpf.wfm.pipeline.ValueType;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.*;

public class TestJsonDescriptor {

    private static final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();


    @Test
    public void canParseJsonDescriptorForCppComponent() throws IOException {
        JsonComponentDescriptor descriptor = loadDescriptor("CplusplusHelloWorldComponent.json");

        assertEquals("CplusplusHelloWorld", descriptor.getComponentName());
        assertEquals(ComponentLanguage.CPP, descriptor.getSourceLanguage());
        assertEquals("4.1.0", descriptor.getComponentVersion());
        assertEquals("${MPF_HOME}/plugins/CplusplusHelloWorld/lib/libmpfCplusplusHelloWorld.so",
                     descriptor.getBatchLibrary());
        assertEquals("${MPF_HOME}/plugins/CplusplusHelloWorld/lib/libmpfStreamingCplusplusHelloWorld.so",
                     descriptor.getStreamLibrary());

        assertEquals(3, descriptor.getAlgorithm().getProvidesCollection().getProperties().size());

        boolean propertiesLoaded = descriptor.getAlgorithm().getProvidesCollection().getProperties()
                .stream()
                .anyMatch(p -> p.getDescription().equals("my prop 1")
                        && p.getName().equals("PROP1")
                        && p.getType() == ValueType.INT
                        && p.getDefaultValue().equals("2"));
        assertTrue(propertiesLoaded);
    }


    @Test
    public void canParseJsonDescriptorForCppComponentWithCustomPipeline() throws IOException {
        JsonComponentDescriptor descriptor = loadDescriptor("CplusplusHelloCustomPipelinesComponent.json");

        assertEquals("CplusplusHelloCustomPipelinesComponent", descriptor.getComponentName());
        assertEquals(ComponentLanguage.CPP, descriptor.getSourceLanguage());
        assertEquals("4.1.0", descriptor.getComponentVersion());
        assertEquals("${MPF_HOME}/plugins/CplusplusHelloCustomPipelinesComponent/lib/libmpfHelloWorldTest.so",
                     descriptor.getBatchLibrary());
        assertNull(descriptor.getStreamLibrary());

        assertEquals(3, descriptor.getAlgorithm().getProvidesCollection().getProperties().size());

        boolean propertiesLoaded = descriptor.getAlgorithm().getProvidesCollection().getProperties()
                .stream()
                .anyMatch(p -> p.getDescription().equals("my prop 1")
                        && p.getName().equals("PROP1")
                        && p.getType() == ValueType.INT
                        && p.getDefaultValue().equals("2"));
        assertTrue(propertiesLoaded);


        assertEquals(2, descriptor.getTasks().size());

        assertEquals(3, descriptor.getActions().size());


        long numActionProperties = descriptor
                .getActions()
                .stream()
                .mapToInt(a -> a.getProperties().size())
                .sum();
        assertEquals(3, numActionProperties);


        long numReferencedActions = descriptor
                .getTasks()
                .stream()
                .mapToInt(t -> t.getActions().size())
                .sum();
        assertEquals(3, numReferencedActions);

        long numReferencedTasks = descriptor
                .getPipelines()
                .stream()
                .mapToInt(p -> p.getTasks().size())
                .sum();
        assertEquals(2, numReferencedTasks);

    }

    @Test
    public void canParseJsonDescriptorForJavaComponent() throws IOException {
        JsonComponentDescriptor descriptor = loadDescriptor("JavaTestDetection.json");

        assertTrue(descriptor.getPipelines().isEmpty());
        assertEquals("JavaTestDetection", descriptor.getComponentName());
        assertEquals(ComponentLanguage.JAVA, descriptor.getSourceLanguage());
        assertEquals("4.1.0", descriptor.getComponentVersion());
        assertEquals("4.1.0", descriptor.getMiddlewareVersion());
        assertEquals("mpf-java-test-detection-component-4.1.0.jar", descriptor.getBatchLibrary());
        assertNull(descriptor.getStreamLibrary());
        assertEquals(1, descriptor.getEnvironmentVariables().size());
        JsonComponentDescriptor.EnvironmentVariable envVar = descriptor.getEnvironmentVariables().get(0);
        assertTrue(envVar.getName().equals("DUMMY_VAR")
                && envVar.getValue().equals("nothing")
                && envVar.getSep() == null);

        assertEquals(3, descriptor.getAlgorithm().getProvidesCollection().getProperties().size());

        boolean propertiesLoaded = descriptor.getAlgorithm().getProvidesCollection().getProperties()
                .stream()
                .anyMatch(p -> p.getDescription().equals("my prop 1")
                        && p.getName().equals("PROP1")
                        && p.getType() == ValueType.INT
                        && p.getDefaultValue().equals("2"));
        assertTrue(propertiesLoaded);
    }



    private JsonComponentDescriptor loadDescriptor(String fileName) throws IOException {
        URL resource = getClass().getClassLoader().getResource(fileName);
        return _objectMapper.readValue(resource, JsonComponentDescriptor.class);
    }
}
