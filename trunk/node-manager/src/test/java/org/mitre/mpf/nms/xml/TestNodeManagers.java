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

package org.mitre.mpf.nms.xml;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class TestNodeManagers {

    private final String SERVICE_REFERENCE = "service reference=";

    @Test
    public void testToXml() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Service testService = new Service("SomeTestService", "SomeTestPath");
        testService.setLauncher("simple");
        List<String> argsList = Arrays.asList("SomeTestArg", "MPF.DETECTION_TEST_REQUEST");
        testService.setArgs(argsList);

        NodeManager testNode1 = new NodeManager("somehost1");
        testNode1.add(testService);

        NodeManager testNode2 = new NodeManager("somehost2");
        testNode2.add(testService);

        NodeManagers nodeManagers = new NodeManagers();
        nodeManagers.add(testNode1);
        nodeManagers.add(testNode2);

        NodeManagers.toXml(nodeManagers, outputStream);
        String content = outputStream.toString();

        Assert.assertFalse("XML should not be empty.", content.isEmpty());
        Assert.assertFalse("XML should not contain \"" + SERVICE_REFERENCE + "\".",
                content.contains(SERVICE_REFERENCE));

        outputStream.close();
    }
}