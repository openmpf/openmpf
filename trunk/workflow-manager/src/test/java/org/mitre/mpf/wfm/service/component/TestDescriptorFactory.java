/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

import com.google.common.collect.Lists;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.pipeline.xml.ValueType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mitre.mpf.wfm.service.component.TestDescriptorConstants.*;

public class TestDescriptorFactory {




    private TestDescriptorFactory() {

    }

    public static JsonComponentDescriptor get() {
        JsonComponentDescriptor descriptor = new JsonComponentDescriptor();
        descriptor.launchArgs = Arrays.asList("launch-arg1", "launch-arg2");
        descriptor.sourceLanguage = ComponentLanguage.CPP;
        descriptor.componentName = COMPONENT_NAME;

        JsonComponentDescriptor.EnvironmentVariable envVar1 = new JsonComponentDescriptor.EnvironmentVariable();
        envVar1.name = "env var1 name";
        envVar1.value = "env var1 value";
        envVar1.sep = ":";

        JsonComponentDescriptor.EnvironmentVariable envVar2 = new JsonComponentDescriptor.EnvironmentVariable();
        envVar2.name = "env var1 name";
        envVar2.value = "env var1 value";
        envVar2.sep = ":";
        descriptor.environmentVariables = Arrays.asList(envVar1, envVar2);

        descriptor.algorithm = new JsonComponentDescriptor.Algorithm();
        descriptor.algorithm.name = "Test Algorithm Name";
        descriptor.algorithm.actionType = ActionType.DETECTION;
        descriptor.algorithm.description = "Test Algorithm Description";

        descriptor.algorithm.requiresCollection = new JsonComponentDescriptor.AlgoRequires();
        descriptor.algorithm.requiresCollection.states = Arrays.asList("r-state1", "r-state2");

        descriptor.algorithm.providesCollection = new JsonComponentDescriptor.AlgoProvides();
        descriptor.algorithm.providesCollection.states = Arrays.asList("p-state1", "p-state2");

        JsonComponentDescriptor.AlgoProvidesProp property1 = new JsonComponentDescriptor.AlgoProvidesProp();
        property1.name = ALGO_PROP_NAMES.get(0);
        property1.type = ValueType.INT;
        property1.description = "test property1 description";
        property1.defaultValue = "100";

        JsonComponentDescriptor.AlgoProvidesProp property2 = new JsonComponentDescriptor.AlgoProvidesProp();
        property2.name = ALGO_PROP_NAMES.get(1);
        property2.type = ValueType.STRING;
        property2.description = "test property2 description";
        property2.defaultValue = "property2-value";
        descriptor.algorithm.providesCollection.properties = Arrays.asList(property1, property2);
        return descriptor;
    }

    public static JsonComponentDescriptor getWithCustomPipeline() {
        List<JsonComponentDescriptor.Action> actions = new ArrayList<>();
        for (String actionName : ACTION_NAMES) {
            JsonComponentDescriptor.Action action = new JsonComponentDescriptor.Action();
            action.name = actionName;
            action.description = actionName + " description";
            action.properties = new ArrayList<>();
            action.algorithm = REFERENCED_ALGO_NAME;
            actions.add(action);
        }

        JsonComponentDescriptor.ActionProperty actionProp1 = new JsonComponentDescriptor.ActionProperty();
        actionProp1.name = ACTION1_PROP_NAMES.get(0);
        actionProp1.value = ACTION1_PROP_VALUES.get(0);
        actions.get(0).properties.add(actionProp1);

        JsonComponentDescriptor.ActionProperty actionProp2 = new JsonComponentDescriptor.ActionProperty();
        actionProp2.name = ACTION1_PROP_NAMES.get(1);
        actionProp2.value = ACTION1_PROP_VALUES.get(1);
        actions.get(0).properties.add(actionProp2);

        List<JsonComponentDescriptor.Task> tasks = new ArrayList<>();
        for (String taskName : TASK_NAMES) {
            JsonComponentDescriptor.Task task = new JsonComponentDescriptor.Task();
            task.name = taskName;
            task.description = taskName + " description";
            tasks.add(task);
        }
        tasks.get(0).actions = Lists.newArrayList(actions.get(0).name);
        tasks.get(1).actions = Arrays.asList(actions.get(1).name, actions.get(2).name);

        JsonComponentDescriptor.Pipeline pipeline = new JsonComponentDescriptor.Pipeline();
        pipeline.name = PIPELINE_NAME;
        pipeline.description = "Pipeline description";
        pipeline.tasks = Lists.newArrayList(tasks.get(0).name, tasks.get(1).name);

        JsonComponentDescriptor descriptor = get();
        descriptor.pipelines = Lists.newArrayList(pipeline);
        descriptor.tasks = tasks;
        descriptor.actions = actions;

        return descriptor;
    }
}
