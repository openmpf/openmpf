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

import org.mitre.mpf.rest.api.pipelines.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toCollection;
import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.*;

public class TestDescriptorFactory {

    private TestDescriptorFactory() {}

    public static JsonComponentDescriptor get() {
        return get(false);
    }

    private static JsonComponentDescriptor get(boolean withCustomPipeline) {
        JsonComponentDescriptor.EnvironmentVariable envVar1 = new JsonComponentDescriptor.EnvironmentVariable(
                "env var1 name",
                "env var1 value",
                ":");

        JsonComponentDescriptor.EnvironmentVariable envVar2 = new JsonComponentDescriptor.EnvironmentVariable(
                "env var2 name",
                "env var2 value",
                ":");


        Algorithm.Requires requiresCollection = new Algorithm.Requires(Arrays.asList("r-state1", "r-state2"));

        Algorithm.Property property1 = new Algorithm.Property(
                ALGO_PROP_NAMES.get(0),
                "test property1 description",
                ValueType.INT,
                "100",
                null);

        Algorithm.Property property2 = new Algorithm.Property(
                ALGO_PROP_NAMES.get(1),
                "test property2 description",
                ValueType.STRING,
                null,
                "my.test.key");

        Algorithm.Provides providesCollection = new Algorithm.Provides(
                Arrays.asList("p-state1", "p-state2"),
                Arrays.asList(property1, property2));


        Algorithm algorithm = new Algorithm(
                "Test Algorithm Name",
                "Test Algorithm Description",
                ActionType.DETECTION,
                requiresCollection,
                providesCollection,
                true,
                false);

        List<Action> actions = new ArrayList<>();
        List<Task> tasks = new ArrayList<>();
        List<Pipeline> pipelines = new ArrayList<>();
        if (withCustomPipeline) {

            Action.Property actionProp1 = new Action.Property(ACTION1_PROP_NAMES.get(0), ACTION1_PROP_VALUES.get(0));
            Action.Property actionProp2 = new Action.Property(ACTION1_PROP_NAMES.get(1), ACTION1_PROP_VALUES.get(1));

            Action action1 = new Action(
                    ACTION_NAMES.get(0),
                    ACTION_NAMES.get(0) + " description",
                    REFERENCED_ALGO_NAME,
                    Arrays.asList(actionProp1, actionProp2));
            actions.add(action1);

            actions.add(new Action(
                    ACTION_NAMES.get(1),
                    ACTION_NAMES.get(1) + " description",
                    REFERENCED_ALGO_NAME,
                    Collections.emptyList()));

            actions.add(new Action(
                    ACTION_NAMES.get(2),
                    ACTION_NAMES.get(2) + " description",
                    REFERENCED_ALGO_NAME,
                    Collections.emptyList()));

            tasks.add(new Task(
                    TASK_NAMES.get(0),
                    TASK_NAMES.get(0) + " description",
                    Collections.singletonList(actions.get(0).getName())));

            tasks.add(new Task(
                    TASK_NAMES.get(1),
                    TASK_NAMES.get(1) + " description",
                    Arrays.asList(actions.get(1).getName(), actions.get(2).getName())));

            pipelines.add(new Pipeline(
                    PIPELINE_NAME,
                    "Pipeline description",
                    Arrays.asList(tasks.get(0).getName(), tasks.get(1).getName())));

        }

        JsonComponentDescriptor descriptor = new JsonComponentDescriptor(
                COMPONENT_NAME,
                "4.0.0",
                "4.0.0",
                null,
                null,
                ComponentLanguage.CPP,
                "/path/to/batch/lib.so",
                "/path/to/stream/lib.so",
                Arrays.asList(envVar1, envVar2),
                algorithm,
                actions,
                tasks,
                pipelines);


        return descriptor;
    }

    public static JsonComponentDescriptor getWithCustomPipeline() {
        return get(true);
    }

    public static Algorithm getReferencedAlgorithm() {

        List<Algorithm.Property> propertyList = ACTION1_PROP_NAMES
                .stream()
                .map(n -> new Algorithm.Property(n, "1", ValueType.STRING, "default_Value", null))
                .collect(toCollection(ArrayList::new));

        propertyList.add(new Algorithm.Property("foo", "2", ValueType.INT, "0", null));

        return new Algorithm(
                REFERENCED_ALGO_NAME, "description", ActionType.DETECTION,
                new Algorithm.Requires(Collections.emptyList()),
                new Algorithm.Provides(Collections.emptyList(), propertyList),
                true, false);
    }
}
