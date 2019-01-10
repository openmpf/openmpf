/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.pipeline.xml.ValueType;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.*;

public class TestJsonDescriptor {

    private static final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    @Test
    public void canParseJsonDescriptorForCppComponent() throws IOException {
        JsonComponentDescriptor descriptor = loadDescriptor("CplusplusHelloWorldComponent.json");

        assertEquals("CplusplusHelloWorld", descriptor.componentName);
        assertEquals(ComponentLanguage.CPP, descriptor.sourceLanguage);
        assertEquals("3.0.0", descriptor.componentVersion);
        assertEquals("${MPF_HOME}/plugins/CplusplusHelloWorld/lib/libmpfCplusplusHelloWorld.so", descriptor.batchLibrary);
        assertEquals("${MPF_HOME}/plugins/CplusplusHelloWorld/lib/libmpfStreamingCplusplusHelloWorld.so", descriptor.streamLibrary);

        assertEquals(3, descriptor.algorithm.providesCollection.properties.size());

        boolean propertiesLoaded = descriptor.algorithm.providesCollection.properties
                .stream()
                .anyMatch(p -> p.description.equals("my prop 1")
                        && p.name.equals("prop1")
                        && p.type == ValueType.INT
                        && p.defaultValue.equals("2"));
        assertTrue(propertiesLoaded);
    }


    @Test
    public void canParseJsonDescriptorForCppComponentWithCustomPipeline() throws IOException {
        JsonComponentDescriptor descriptor = loadDescriptor("CplusplusHelloCustomPipelinesComponent.json");

        assertEquals("CplusplusHelloCustomPipelinesComponent", descriptor.componentName);
        assertEquals(ComponentLanguage.CPP, descriptor.sourceLanguage);
        assertEquals("3.0.0", descriptor.componentVersion);
        assertEquals("${MPF_HOME}/plugins/CplusplusHelloCustomPipelinesComponent/lib/libmpfHelloWorldTest.so", descriptor.batchLibrary);
        assertNull(descriptor.streamLibrary);

        assertEquals(3, descriptor.algorithm.providesCollection.properties.size());

        boolean propertiesLoaded = descriptor.algorithm.providesCollection.properties
                .stream()
                .anyMatch(p -> p.description.equals("my prop 1")
                        && p.name.equals("prop1")
                        && p.type == ValueType.INT
                        && p.defaultValue.equals("2"));
        assertTrue(propertiesLoaded);


        assertEquals(2, descriptor.tasks.size());

        assertEquals(3, descriptor.actions.size());


        long numActionProperties = descriptor
                .actions
                .stream()
                .mapToInt(a -> a.properties.size())
                .sum();
        assertEquals(3, numActionProperties);


        long numReferencedActions = descriptor
                .tasks
                .stream()
                .mapToInt(t -> t.actions.size())
                .sum();
        assertEquals(3, numReferencedActions);

        long numReferencedTasks = descriptor
                .pipelines
                .stream()
                .mapToInt(p -> p.tasks.size())
                .sum();
        assertEquals(2, numReferencedTasks);

    }

    @Test
    public void canParseJsonDescriptorForJavaComponent() throws IOException {
        JsonComponentDescriptor descriptor = loadDescriptor("JavaTestDetection.json");

        assertNull(descriptor.pipelines);
        assertEquals("JavaTestDetection", descriptor.componentName);
        assertEquals(ComponentLanguage.JAVA, descriptor.sourceLanguage);
        assertEquals("3.0.0", descriptor.componentVersion);
        assertEquals("3.0.0", descriptor.middlewareVersion);
        assertEquals("mpf-java-test-detection-component-3.0.0.jar", descriptor.batchLibrary);
        assertNull(descriptor.streamLibrary);
        assertEquals(1, descriptor.environmentVariables.size());
        JsonComponentDescriptor.EnvironmentVariable envVar = descriptor.environmentVariables.get(0);
        assertTrue(envVar.name.equals("DUMMY_VAR")
                && envVar.value.equals("nothing")
                && envVar.sep == null);

        assertEquals(3, descriptor.algorithm.providesCollection.properties.size());

        boolean propertiesLoaded = descriptor.algorithm.providesCollection.properties
                .stream()
                .anyMatch(p -> p.description.equals("my prop 1")
                        && p.name.equals("prop1")
                        && p.type == ValueType.INT
                        && p.defaultValue.equals("2"));
        assertTrue(propertiesLoaded);
    }



    private JsonComponentDescriptor loadDescriptor(String fileName) throws IOException {
        URL resource = getClass().getClassLoader().getResource(fileName);
        return _objectMapper.readValue(resource, JsonComponentDescriptor.class);
    }
}
