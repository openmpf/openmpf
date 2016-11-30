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

package org.mitre.mpf.wfm.pipeline;

import com.thoughtworks.xstream.XStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.json.XML;
import org.mitre.mpf.interop.JsonAction;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.interop.JsonStage;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.pipeline.xml.*;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.TextUtils;
import org.mitre.mpf.wfm.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;

@Component(PipelineManager.REF)
public class PipelineManager {
    public static final String REF = "pipelineManager";
    private static final Logger log = LoggerFactory.getLogger(PipelineManager.class);

    @Autowired
    @Qualifier(PropertiesUtil.REF)
    private PropertiesUtil propertiesUtil;

    @Autowired
    @Qualifier("loadedProperties")
    private Properties properties;


    private XStream xStream;

    private final Map<String, AlgorithmDefinition> algorithms = new HashMap<String, AlgorithmDefinition>();
    private final Map<String, ActionDefinition> actions = new HashMap<String, ActionDefinition>();
    private final Map<String, TaskDefinition> tasks = new HashMap<String, TaskDefinition>();
    private final Map<String, PipelineDefinition> pipelines = new HashMap<String, PipelineDefinition>();
    private final Map<String, Set<StateDefinition>> providedTaskStates = new HashMap<String, Set<StateDefinition>>();
    private final Map<String, Set<StateDefinitionRef>> requiredTaskStates = new HashMap<String, Set<StateDefinitionRef>>();




    @PostConstruct
    public void init() {
        log.debug("Initializing PipelineManager");
        xStream = new XStream();

        xStream.processAnnotations(PipelineDefinitionCollection.class);
        xStream.processAnnotations(TaskDefinitionCollection.class);
        xStream.processAnnotations(ActionDefinitionCollection.class);
        xStream.processAnnotations(AlgorithmDefinitionCollection.class);

        try {
            AlgorithmDefinitionCollection algorithms = fromXStream(propertiesUtil.getAlgorithmDefinitions(),
                    AlgorithmDefinitionCollection.class);

            algorithms.getAlgorithms()
                    .stream()
                    .flatMap(ad -> ad.getProvidesCollection().getAlgorithmProperties().stream())
                    .forEach(pd -> pd.setDefaultValue(properties));

            for (AlgorithmDefinition algorithm : algorithms.getAlgorithms()) {
                if (addAlgorithm(algorithm)) {
                    log.debug("added algorithm {}", algorithm);
                } else {
                    log.warn("failed to add algorithm {}", algorithm);
                }
            }


            ActionDefinitionCollection actions = fromXStream(propertiesUtil.getActionDefinitions(),
                    ActionDefinitionCollection.class);

            for (ActionDefinition action : actions.getActionDefinitions()) {
                if (addAction(action)) {
                    log.debug("added action {}", action);
                } else {
                    log.warn("failed to add action {}", action);
                }
            }


            TaskDefinitionCollection tasks = fromXStream(propertiesUtil.getTaskDefinitions(),
                    TaskDefinitionCollection.class);

            for (TaskDefinition task : tasks.getTasks()) {
                if (addTask(task)) {
                    log.debug("added task {}", task);
                } else {
                    log.warn("failed to add task {}", task);
                }
            }


            PipelineDefinitionCollection pipelines = fromXStream(propertiesUtil.getPipelineDefinitions(),
                    PipelineDefinitionCollection.class);

            for (PipelineDefinition pipeline : pipelines.getPipelines()) {
                if (addPipeline(pipeline)) {
                    log.debug("added pipeline {}", pipeline);
                } else {
                    log.warn("failed to add pipeline {}", pipeline);
                }
            }
        } catch(IOException e) {
            // Throwing an uncaught exception in a @PostConstruct function prevents the Spring ApplicationContext from starting up.
            throw new UncheckedIOException(
                    "An exception occurred while trying to load an XML definitions file. Cannot start Workflow Manager without reading these files",
                    e);
        }
    }


    private <T> T fromXStream(InputStreamSource resource, Class<T> type) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            Object obj = xStream.fromXML(inputStream);
            if (type.isInstance(obj)) {
                //noinspection unchecked
                return (T) obj;
            }
            else {
                throw new IllegalArgumentException(String.format("Expected type to be %s but was %s",
                        type.getCanonicalName(), obj.getClass().getCanonicalName()));
            }
        }
    }


	public JsonPipeline createJsonPipeline(String pipeline) {
		String pipelineName = TextUtils.trimAndUpper(pipeline);
		if(!pipelines.containsKey(pipelineName)) {
			log.warn("A pipeline does not exist with the name '{}'.", pipeline);
			return null;
		}

        PipelineDefinition pipelineDef = pipelines.get(pipelineName);

        JsonPipeline jsonPipeline = new JsonPipeline(pipelineDef.getName(), pipelineDef.getDescription());
        pipelineDef.getTaskRefs().stream()
                .map(this::getTask)
                .map(this::convert)
                .forEach(stage -> jsonPipeline.getStages().add(stage));

        return jsonPipeline;
	}


	private JsonStage convert(TaskDefinition task) {
        // Peek at the first action in the task to determine the action type.
        String actionType = task.getActions().stream()
                .findAny()
                .map(adr -> getAlgorithm(adr).getActionType().name())
                .orElse(null);

        JsonStage jsonStage = new JsonStage(actionType, task.getName(), task.getDescription());
        task.getActions().stream()
                .map(this::getAction)
                .map(PipelineManager::convert)
                .forEach(jsAction -> jsonStage.getActions().add(jsAction));

        return jsonStage;
    }


	private static JsonAction convert(ActionDefinition action) {
        JsonAction jsonAction = new JsonAction(action.getAlgorithmRef(), action.getName(), action.getDescription());
        action.getProperties()
                .forEach(pdr -> jsonAction.getProperties().put(pdr.getName(), pdr.getValue()));

        return jsonAction;
    }


    /** Gets the alphabetically-sorted set of pipeline names. */
    public SortedSet<String> getPipelineNames() {
        return new TreeSet<String>(pipelines.keySet());
    }


    /** Gets the pipelines definition XML as an JSON string */
    public String getPipelineDefinitionAsJson() {
        try (InputStream inputStream = propertiesUtil.getPipelineDefinitions().getInputStream()) {
            String pipelinesXmlString = IOUtils.toString(inputStream);
            JSONObject xmlJSONObj = XML.toJSONObject(pipelinesXmlString);
            return xmlJSONObj.toString();
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Could not read pipeline definition file", ex);
        }
    }


    public Tuple<Boolean,String> save(String type) {
        Resource resource;
        AlgorithmDefinitionCollection algorithmDefinitionCollection = null;
        ActionDefinitionCollection actionDefinitionCollection = null;
        TaskDefinitionCollection taskDefinitionCollection = null;
        PipelineDefinitionCollection pipelineDefinitionCollection = null;

        switch (type) {
            case "algorithm":
                algorithmDefinitionCollection = new AlgorithmDefinitionCollection();
                for (Entry<String, AlgorithmDefinition> entry : algorithms.entrySet()) {
                    algorithmDefinitionCollection.getAlgorithms().add(entry.getValue());
                }
                resource = propertiesUtil.getAlgorithmDefinitions();
                break;
            case "action":
                //loop through the map
                actionDefinitionCollection = new ActionDefinitionCollection();
                for (Entry<String, ActionDefinition> entry : actions.entrySet()) {
                    actionDefinitionCollection.getActionDefinitions().add(entry.getValue());
                }
                resource = propertiesUtil.getActionDefinitions();
                break;
            case "task":
                taskDefinitionCollection = new TaskDefinitionCollection();
                for (Entry<String, TaskDefinition> entry : tasks.entrySet()) {
                    taskDefinitionCollection.getTasks().add(entry.getValue());
                }
                resource = propertiesUtil.getTaskDefinitions();
                break;
            case "pipeline":
                pipelineDefinitionCollection = new PipelineDefinitionCollection();
                for (Entry<String, PipelineDefinition> entry : pipelines.entrySet()) {
                    pipelineDefinitionCollection.getPipelines().add(entry.getValue());
                }
                resource = propertiesUtil.getPipelineDefinitions();
                break;
            default:
                return new Tuple<>(false, "Unable to save object type: " + type);
        }

        try (Writer outputWriter = Files.newBufferedWriter(resource.getFile().toPath())) {
            switch (type) {
                case "algorithm":
                    xStream.toXML(algorithmDefinitionCollection, outputWriter);
                    break;
                case "action":
                    xStream.toXML(actionDefinitionCollection, outputWriter);
                    break;
                case "task":
                    xStream.toXML(taskDefinitionCollection, outputWriter);
                    break;
                case "pipeline":
                    xStream.toXML(pipelineDefinitionCollection, outputWriter);
                    break;
                default:
                    return new Tuple<>(false, "Unable to save object type: " + type);
            }
            return new Tuple<>(true, null);
        }
        catch (IOException e) {
            log.error("Failed to write to xml with message: {}", e.getMessage());
        }
        return new Tuple<>(false, "error reading the xml file of type: " + type);
    }


    //
    // Algorithm
    //

    /** Gets a copy of the defined algorithms. */
    public Set<AlgorithmDefinition> getAlgorithms() {
        return new HashSet<AlgorithmDefinition>(algorithms.values());
    }

    /** Gets an AlgorithmDefinition by name (case-insensitive). */
    public AlgorithmDefinition getAlgorithm(String name) { return algorithms.get(TextUtils.trimAndUpper(name)); }

    /** Get the AlgorithmDefinition associated with an ActionDefinition. Returns null if this operation fails. */
    public AlgorithmDefinition getAlgorithm(ActionDefinition actionDefinition) {
        return actionDefinition == null ? null : getAlgorithm(actionDefinition.getAlgorithmRef());
    }

    /** Get the AlgorithmDefinition associated with an ActionDefinitionRef. Returns null if this operation fails. */
    public AlgorithmDefinition getAlgorithm(ActionDefinitionRef actionDefinitionRef) {
        return actionDefinitionRef == null ? null : getAlgorithm(getAction(actionDefinitionRef.getName()));
    }

    //
    // Action
    //

    public Set<ActionDefinition> getActions() {
        return new HashSet<ActionDefinition>(actions.values());
    }

    /** Gets an ActionDefinition by name (case-insensitive). */
    public ActionDefinition getAction(String name) {
        return actions.get(StringUtils.upperCase(name));
    }

    /** Gets an ActionDefinition by a reference to it. */
    public ActionDefinition getAction(ActionDefinitionRef name) {
        return name == null ? null : getAction(name.getName());
    }

    //
    // Task
    //

    public Set<TaskDefinition> getTasks() {
        return new HashSet<TaskDefinition>(tasks.values());
    }

    public List<TaskDefinition> getTasks(String pipelineName) {
        List<TaskDefinition> taskDefinitions = new ArrayList<TaskDefinition>();
        for(TaskDefinitionRef taskDefinitionRef : pipelines.get(StringUtils.upperCase(pipelineName)).getTaskRefs()) {
            taskDefinitions.add(getTask(taskDefinitionRef));
        }
        return taskDefinitions;
    }

    /**
     * Gets a TaskDefinition by name (case-insensitive).
     */
    public TaskDefinition getTask(String name) {
        return tasks.get(StringUtils.upperCase(name));
    }

    /**
     * Gets a TaskDefinition by a reference to it.
     */
    public TaskDefinition getTask(TaskDefinitionRef taskDefinitionRef) {
        return taskDefinitionRef == null ? null : getTask(taskDefinitionRef.getName());
    }

    //
    // Pipeline
    //

    public Set<PipelineDefinition> getPipelines() {
        return new HashSet<PipelineDefinition>(pipelines.values());
    }

    /** Gets a PipelineDefinition by name (case-insensitive). */
    public PipelineDefinition getPipeline(String name) {
        return pipelines.get(StringUtils.upperCase(name));
    }

    /** Forgets all of the previously-added pipelines, tasks, actions, and algorithms. */
    public void reset() {
        pipelines.clear();
        tasks.clear();
        actions.clear();
        algorithms.clear();
    }

    /** Adds an algorithm. This will return false if the algorithm could not be added. */
    public boolean addAlgorithm(AlgorithmDefinition algorithm) {
        if (isValidAlgorithm(algorithm)) {
            log.debug("{}: Adding algorithm", StringUtils.upperCase(algorithm.getName()));
            algorithms.put(StringUtils.upperCase(algorithm.getName()), algorithm);
            return true;
        } else {
            log.warn("{}: This algorithm was not added.", algorithm);
            return false;
        }
    }

    public void removeAlgorithm(String algorithmName) {
        if (algorithms.containsKey(algorithmName)) {
            algorithms.remove(algorithmName);
        }
    }

    /** Adds an action. This will return false if the action could not be added. */
    public boolean addAction(ActionDefinition actionNode) {
        if (isValidAction(actionNode)) {
            log.debug("{}: Adding action", StringUtils.upperCase(actionNode.getName()));
            actions.put(StringUtils.upperCase(actionNode.getName()), actionNode);
            return true;
        } else {
            log.warn("{}: This action was not added.", actionNode);
            return false;
        }
    }

    public void removeAction(String actionName) {
        if (actions.containsKey(actionName)) {
            actions.remove(actionName);
        }
    }

    /** Adds a task. This will return false if the task could not be added. */
    public boolean addTask(TaskDefinition task) {
        if (!isValidTask(task)) {
            log.warn("{}: This task was not added.", task);
            return false;
        }

        log.debug("{}: Adding task", StringUtils.upperCase(task.getName()));
        tasks.put(StringUtils.upperCase(task.getName()), task);
        for (ActionDefinitionRef actionRef : task.getActions()) {
            AlgorithmDefinition algorithm = getAlgorithm(actionRef);
            String taskName = StringUtils.upperCase(task.getName());

            if (algorithm.getProvidesCollection() != null && algorithm.getProvidesCollection().getStates() != null) {
                providedTaskStates.put(taskName, algorithm.getProvidesCollection().getStates());
            } else {
                providedTaskStates.put(taskName, Collections.emptySet());
            }

            if (algorithm.getRequiresCollection() != null && algorithm.getRequiresCollection().getStateRefs() != null) {
                requiredTaskStates.put(taskName, algorithm.getRequiresCollection().getStateRefs());
            } else {
                requiredTaskStates.put(taskName, Collections.emptySet());
            }
        }
        return true;
    }

    public void removeTask(String taskName) {
        if (tasks.containsKey(taskName)) {
            tasks.remove(taskName);
        }
    }

    /** Adds a pipeline. This will return false if the pipeline could not be added. */
    public boolean addPipeline(PipelineDefinition pipeline) {
        if (isValidPipeline(pipeline)) {
            log.debug("{}: Adding pipeline", StringUtils.upperCase(pipeline.getName()));
            pipelines.put(StringUtils.upperCase(pipeline.getName()), pipeline);
            return true;
        } else {
            log.warn("{}: This pipeline was not added.", pipeline);
            return false;
        }
    }

    public void removePipeline(String pipelineName) {
        if (pipelines.containsKey(pipelineName)) {
            pipelines.remove(pipelineName);
        }
    }

    /** Check that the algorithm is not null, is valid, and has a name which has not already been added to the PipelineManager instance. Returns false if the algorithm is not valid.*/
    private boolean isValidAlgorithm(AlgorithmDefinition algorithm) {
        if (algorithm == null) {
            log.warn("algorithm cannot be null");
            return false;
        } else if (!algorithm.isValid()) {
            log.error("{}: algorithm is not valid", algorithm);
            return false;
        } else if (getAlgorithm(algorithm.getName()) != null) {
            log.error("{}: algorithm name is already in use", StringUtils.upperCase(algorithm.getName()));
            return false;
        } else {
            return true;
        }
    }

    /** Check that the task is not null, is valid, references an algorithm which already exists in the PipelineManager, has a name which has not already been added to the PipelineManager instance, and has properties associated with it which are valid for the referenced algorithm. Returns false if the action is invalid. */
    private boolean isValidAction(ActionDefinition actionDefinition) {
        if (actionDefinition == null) {
            log.warn("action was null");
            return false;
        } else if (!actionDefinition.isValid()) {
            log.error("{}: action is not valid", actionDefinition);
            return false;
        } else if (getAlgorithm(actionDefinition) == null) {
            log.error("{}: referenced algorithm {} does not exist", actionDefinition.getName(), StringUtils.upperCase(actionDefinition.getAlgorithmRef()));
            return false;
        } else if (getAction(actionDefinition.getName()) != null) {
            log.error("{}: action name is already in use", actionDefinition.getName());
            return false;
        } else {
            // If this action provided any properties...
            if (actionDefinition.getProperties() != null) {
                AlgorithmDefinition algorithm = getAlgorithm(actionDefinition);

                // For each property (we've already verified the completeness of this set)...
                for (PropertyDefinitionRef propertyValue : actionDefinition.getProperties()) {

                    if (!algorithm.getProvidesCollection().getAlgorithmProperties().contains(propertyValue)) {
                        // If the referenced property in the action doesn't exist in the algorithm's definition...
                        log.error("{}: The referenced algorithm ({}) does not expose the property {}.",
                                actionDefinition.getName(),
                                actionDefinition.getAlgorithmRef(),
                                propertyValue.getName());
                        return false;
                    } else if (!isValidValueForType(propertyValue.getValue(), algorithm.getProvidesCollection().getAlgorithmProperty(propertyValue.getName()).getType())) {
                        // Otherwise, if the provided value for the property is not acceptable given the property's defined type...
                        log.error("{}.{}: The provided value of '{}' cannot be converted to type {}.",
                                actionDefinition.getName(),
                                propertyValue.getName(),
                                propertyValue.getValue(),
                                algorithm.getProvidesCollection().getAlgorithmProperty(propertyValue.getName()).getType());
                        return false;
                    }
                }
            } else {
                log.debug("{}: no properties were provided", actionDefinition.getName());
            }
        }

        return true;
    }

    /** Check that the task is not null, is valid, has a name which has not already been added to the PipelineManager, references only actions which have already been added to the PipelineManager, and if more than one action is specified, check that all referenced actions are of the same Operation. Returns false if the task is invalid. */
    private boolean isValidTask(TaskDefinition task) {
        if (task == null) {
            log.warn("task must not be null");
            return false;
        } else if (!task.isValid()) {
            log.error("{}: task is not valid", task);
            return false;
        } else if (getTask(task.getName()) != null) {
            log.error("{}: task name is already in use", StringUtils.upperCase(task.getName()));
            return false;
        } else {
            ActionType actionType = null;

            for (ActionDefinitionRef actionRef : task.getActions()) {
                if (getAction(actionRef) == null) {
                    log.error("{}: The referenced action ({}) does not exist",
                            StringUtils.upperCase(task.getName()),
                            StringUtils.upperCase(actionRef.getName()));
                    return false;
                } else if (actionType == null) {
                    // First time through - set the action type. Remember, algorithms MUST have an ActionType associated with them, so this is okay.
	                actionType = getAlgorithm(actionRef).getActionType();
                } else {
                    // Not first time through - check that the current action uses the same action type as the first action in the task.
                    ActionType otherActionType = null;
                    if (actionType != (otherActionType = getAlgorithm(actionRef).getActionType())) {
                        log.error("{}: task cannot contain actions which have different ActionTypes. The first ActionType for this task was {}, but {} has an ActionType of {}.",
                                task.getName(),
		                        actionType,
                                actionRef.getName(),
                                otherActionType);
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /** Rudimentary check that the given value can be parsed from the given type using the underlying Java types. Ex: Given ("17", ValueType.BOOLEAN) => false, ("17", ValueType.INT) => true. */
    private static boolean isValidValueForType(String value, ValueType type) {
        try {
            switch (type) {
                case BOOLEAN:
                    Boolean.valueOf(value);
                    break;
                case DOUBLE:
                    Double.valueOf(value);
                    break;
                case FLOAT:
                    Float.valueOf(value);
                    break;
                case INT:
                    Integer.valueOf(value);
                    break;
                case LONG:
                    Long.valueOf(value);
                    break;
                case STRING:
                    break;
            }

            return true;
        } catch (NumberFormatException ex) {
            log.warn(String.format("%s is not a valid %s", value, type), ex);
            return false;
        }
    }

    /** Check that the pipeline is not null, is valid, has a name which is unique in the PipelineManager, references valid tasks, and that the states (using provides/requires) are valid for the proposed sequence of tasks. Returns false if the pipeline. */
    private boolean isValidPipeline(PipelineDefinition pipeline) {
        if (pipeline == null) {
            log.warn("pipeline must not be null");
            return false;
        } else if (!pipeline.isValid()) {
            log.error("{}: pipeline is not valid");
            return false;
        } else if (getPipeline(pipeline.getName()) != null) {
            log.error("{}: pipeline name is already in use", StringUtils.upperCase(pipeline.getName()));
            return false;
        }
        return areTasksValid(StringUtils.upperCase(pipeline.getName()), pipeline.getTaskRefs());
    }

    private ActionType getTaskType(TaskDefinition taskDefinition) {
        return taskDefinition == null ? null : getAlgorithm(getAction(taskDefinition.getActions().get(0))).getActionType();
    }

    private ActionType getTaskType(TaskDefinitionRef taskDefinitionRef) {
        return taskDefinitionRef == null ? null : getTaskType(getTask(taskDefinitionRef));
    }

    private boolean areTasksValid(String pipelineName, List<TaskDefinitionRef> taskRefs) {
        if(getTask(taskRefs.get(0)) == null) {
            log.error("{}: Task with name {} does not exist.",
                    pipelineName,
                    taskRefs.get(0).getName());
            return false;
        } else {
            switch (getTaskType(taskRefs.get(0))) {
                case DETECTION:
                    return isValidDetectionPipeline(pipelineName, taskRefs);
                default:
                    log.error("{}: {} - pipelines may not start with tasks of the type {}",
                            pipelineName,
                            getTaskType(taskRefs.get(0)),
                            taskRefs.get(0).getName());
                    return false;
            }
        }
    }

    private boolean isValidDetectionPipeline(String pipelineName, List<TaskDefinitionRef> taskDefinitions) {
        return isValidDetectionPipeline(pipelineName, taskDefinitions, new HashSet<StateDefinition>(10));
    }

    private boolean isValidDetectionPipeline(String pipelineName, List<TaskDefinitionRef> taskDefinitions, Set<StateDefinition> currentStates) {
        if (taskDefinitions.size() == 1) {
            // There's only one task in the pipeline, and we know it to be detection.
            boolean rValue = true;
            Set<StateDefinitionRef> requiredStates = requiredTaskStates.get(StringUtils.upperCase(taskDefinitions.get(0).getName()));
            if(!currentStates.containsAll(requiredStates)) {
                log.error("{}: The states for {} are not satisfied. Current: {}. Required: {}.",
                        pipelineName,
                        taskDefinitions.get(0).getName(),
                        currentStates,
                        requiredStates);
                rValue = false;
            }
            return rValue;
        } else if (getTask(taskDefinitions.get(0)).getActions().size() > 1) {
            // There's more than one task, and the number of actions in the first task exceeds 1.
            log.error("{}: No tasks may follow the multi-detection task of {}.", pipelineName, taskDefinitions.get(0).getName());
            return false;
        } else if(getTask(taskDefinitions.get(1)) == null) {
            // At this point, we've determined there's at least one more task. The next task name must exist.
            log.error("{}: Task with name {} does not exist.", pipelineName, taskDefinitions.get(1).getName());
            return false;
        } else {
            currentStates.addAll(providedTaskStates.get(StringUtils.upperCase(taskDefinitions.get(0).getName())));
            ActionType nextTaskType = getTaskType(taskDefinitions.get(1));
            switch (nextTaskType) {
                case DETECTION:
                    currentStates.clear(); // If the next task is detection-based, we should disregard the current states as we're downselecting.
                    return isValidDetectionPipeline(pipelineName, taskDefinitions.subList(1, taskDefinitions.size()), currentStates);
                case MARKUP:
                    return isValidMarkupPipeline(pipelineName, taskDefinitions.subList(1, taskDefinitions.size()), currentStates);
                default:
                    log.error("{}: {} is a detection task and may not be followed by {}.",
                            pipelineName,
                            taskDefinitions.get(0).getName(),
                            taskDefinitions.get(1).getName());
                    return false;
            }
        }
    }

    private boolean isValidMarkupPipeline(String pipelineName, List<TaskDefinitionRef> taskDefinitions, Set<StateDefinition> currentStates) {
        if (taskDefinitions.size() == 1) {
            // There's only one task in the pipeline, and we know it to be markup.
            boolean rValue = true;
            Set<StateDefinitionRef> requiredStates = requiredTaskStates.get(StringUtils.upperCase(taskDefinitions.get(0).getName()));
            if(!currentStates.containsAll(requiredStates)) {
                log.error("{}: The states for {} are not satisfied. Current: {}. Required: {}.",
                        pipelineName,
                        taskDefinitions.get(0).getName(),
                        currentStates,
                        requiredStates);
                rValue = false;
            }
            return rValue;
        } else {
            log.error("{}: No tasks may follow a markup task of {}.", pipelineName, taskDefinitions.get(0).getName());
            return false;
        }
    }
}
