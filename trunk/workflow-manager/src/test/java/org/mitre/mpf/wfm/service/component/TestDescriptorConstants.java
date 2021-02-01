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

package org.mitre.mpf.wfm.service.component;

import com.google.common.collect.ImmutableList;

import java.nio.file.Paths;
import java.util.List;

public class TestDescriptorConstants {
    public static final String COMPONENT_NAME = "TestComponent";

    public static final String DESCRIPTOR_PATH = Paths
            .get("tmp", COMPONENT_NAME, "descriptor", "descriptor.json")
            .toAbsolutePath()
            .toString();

    public static final List<String> ACTION_NAMES = ImmutableList.of(
            "ACTION 1 NAME", "ACTION 2 NAME", "ACTION 3 NAME");

    public static final List<String> TASK_NAMES = ImmutableList.of("TASK 1 NAME", "TASK 2 NAME");

    public static final String REFERENCED_ALGO_NAME = "REFERENCED ALGORITHM NAME";


    public static final List<String> ACTION1_PROP_NAMES = ImmutableList.of(
            "ACTION1 PROP1 NAME", "ACTION1 PROP2 NAME");
    public static final List<String> ACTION1_PROP_VALUES = ImmutableList.of("prop1 value", "prop2 value");

    public static final String PIPELINE_NAME = "TEST PIPELINE NAME";

    public static final List<String> ALGO_PROP_NAMES = ImmutableList.of("PROPERTY1-NAME", "PROPERTY2-NAME");

    private TestDescriptorConstants() {

    }
}
