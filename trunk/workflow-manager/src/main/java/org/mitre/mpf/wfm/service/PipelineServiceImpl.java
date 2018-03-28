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

package org.mitre.mpf.wfm.service;

import com.thoughtworks.xstream.XStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.json.XML;
import org.mitre.mpf.interop.JsonAction;
import org.mitre.mpf.interop.JsonPipeline;
import org.mitre.mpf.interop.JsonStage;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.exceptions.*;
import org.mitre.mpf.wfm.pipeline.xml.*;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.WritableResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class PipelineServiceImpl implements PipelineService {
    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    @Autowired
    @Qualifier(PropertiesUtil.REF)
    private PropertiesUtil propertiesUtil;

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

            for (AlgorithmDefinition algorithm : algorithms.getAlgorithms()) {
                try {
                    addAlgorithm(algorithm);
                    log.debug("added algorithm {}", algorithm);
                } catch (WfmProcessingException ex) {
                    log.warn("failed to add algorithm {}", algorithm);
                }
            }


            ActionDefinitionCollection actions = fromXStream(propertiesUtil.getActionDefinitions(),
                    ActionDefinitionCollection.class);

            for (ActionDefinition action : actions.getActionDefinitions()) {
                try {
                    addAction(action);
                    log.debug("added action {}", action);
                } catch (WfmProcessingException ex) {
                    log.warn("failed to add action {}", action);
                }
            }


            TaskDefinitionCollection tasks = fromXStream(propertiesUtil.getTaskDefinitions(),
                    TaskDefinitionCollection.class);

            for (TaskDefinition task : tasks.getTasks()) {
                try {
                    addTask(task);
                    log.debug("added task {}", task);
                } catch (WfmProcessingException ex) {
                    log.warn("failed to add task {}", task);
                }
            }


            PipelineDefinitionCollection pipelines = fromXStream(propertiesUtil.getPipelineDefinitions(),
                    PipelineDefinitionCollection.class);

            for (PipelineDefinition pipeline : pipelines.getPipelines()) {
                try {
                    addPipeline(pipeline);
                    log.debug("added pipeline {}", pipeline);
                } catch (WfmProcessingException ex) {
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


	@Override
    public JsonPipeline createJsonPipeline(String pipeline) {
		String pipelineName = TextUtils.trimAndUpper(pipeline);
		if(!pipelines.containsKey(pipelineName)) {
                        throw new InvalidPipelineObjectWfmProcessingException("Missing pipeline: \"" + pipeline + "\".");
		}

        PipelineDefinition pipelineDef = pipelines.get(pipelineName);

        JsonPipeline jsonPipeline = new JsonPipeline(pipelineDef.getName(), pipelineDef.getDescription());

        List<TaskDefinitionRef> missingTasks = pipelineDef.getTaskRefs().stream()
                .filter(td -> (getTask(td) == null))
                .collect(Collectors.toList());

        List<TaskDefinitionRef> availableTasks = pipelineDef.getTaskRefs().stream()
                .filter(td -> (getTask(td) != null))
                .collect(Collectors.toList());;

        List<ActionDefinitionRef> missingActions = availableTasks.stream()
                .flatMap(td -> getTask(td).getActions().stream())
                .filter(ad -> (getAction(ad) == null))
                .collect(Collectors.toList());

        List<ActionDefinitionRef> availableActions = availableTasks.stream()
                .flatMap(td -> getTask(td).getActions().stream())
                .filter(ad -> (getAction(ad) != null))
                .collect(Collectors.toList());

        List<String> missingAlgorithms = availableActions.stream()
                .map(ad -> getAction(ad).getAlgorithmRef())
                .filter(ad -> (getAlgorithm(ad) == null))
                .collect(Collectors.toList());

        String message = "";
        if (!missingAlgorithms.isEmpty()) {
            message += missingAlgorithms.stream()
                    .collect(Collectors.joining("\", \"", "Missing algorithms: \"", "\". "));
        }

        if (!missingActions.isEmpty()) {
            message += missingActions.stream()
                    .map(ActionDefinitionRef::getName)
                    .collect(Collectors.joining("\", \"", "Missing actions: \"", "\". "));
        }

        if (!missingTasks.isEmpty()) {
            message += missingTasks.stream()
                    .map(TaskDefinitionRef::getName)
                    .collect(Collectors.joining("\", \"", "Missing tasks: \"", "\". "));
        }

        if (!message.isEmpty()) {
            throw new InvalidPipelineObjectWfmProcessingException(message.trim());
        }

        availableTasks.stream()
                .map(td -> convert(getTask(td)))
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
                .map(PipelineServiceImpl::convert)
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
    @Override
    public SortedSet<String> getPipelineNames() {
        return new TreeSet<>(pipelines.keySet());
    }

    @Override
    public SortedSet<String> getActionNames() {
        return new TreeSet<>(actions.keySet());
    }

    @Override
    public SortedSet<String> getAlgorithmNames() {
        return new TreeSet<>(algorithms.keySet());
    }

    @Override
    public SortedSet<String> getTaskNames() {
        return new TreeSet<>(tasks.keySet());
    }


    /** Gets the pipelines definition XML as an JSON string */
    @Override
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



    //
    // Algorithm
    //

    /** Gets a copy of the defined algorithms. */
    @Override
    public Set<AlgorithmDefinition> getAlgorithms() {
        return new HashSet<AlgorithmDefinition>(algorithms.values());
    }

    /** Gets an AlgorithmDefinition by name (case-insensitive). */
    @Override
    public AlgorithmDefinition getAlgorithm(String name) { return algorithms.get(TextUtils.trimAndUpper(name)); }

    /** Get the AlgorithmDefinition associated with an ActionDefinition. Returns null if this operation fails. */
    @Override
    public AlgorithmDefinition getAlgorithm(ActionDefinition actionDefinition) {
        return actionDefinition == null ? null : getAlgorithm(actionDefinition.getAlgorithmRef());
    }

    /** Get the AlgorithmDefinition associated with an ActionDefinitionRef. Returns null if this operation fails. */
    @Override
    public AlgorithmDefinition getAlgorithm(ActionDefinitionRef actionDefinitionRef) {
        return actionDefinitionRef == null ? null : getAlgorithm(getAction(actionDefinitionRef.getName()));
    }

    //
    // Action
    //

    @Override
    public Set<ActionDefinition> getActions() {
        return new HashSet<ActionDefinition>(actions.values());
    }

    /** Gets an ActionDefinition by name (case-insensitive). */
    @Override
    public ActionDefinition getAction(String name) {
        return actions.get(StringUtils.upperCase(name));
    }

    /** Gets an ActionDefinition by a reference to it. */
    @Override
    public ActionDefinition getAction(ActionDefinitionRef name) {
        return name == null ? null : getAction(name.getName());
    }

    //
    // Task
    //

    @Override
    public Set<TaskDefinition> getTasks() {
        return new HashSet<TaskDefinition>(tasks.values());
    }

    @Override
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
    @Override
    public TaskDefinition getTask(String name) {
        return tasks.get(StringUtils.upperCase(name));
    }

    /**
     * Gets a TaskDefinition by a reference to it.
     */
    @Override
    public TaskDefinition getTask(TaskDefinitionRef taskDefinitionRef) {
        return taskDefinitionRef == null ? null : getTask(taskDefinitionRef.getName());
    }

    //
    // Pipeline
    //

    @Override
    public Set<PipelineDefinition> getPipelines() {
        return new HashSet<PipelineDefinition>(pipelines.values());
    }

    /** Gets a PipelineDefinition by name (case-insensitive). */
    @Override
    public PipelineDefinition getPipeline(String name) {
        return pipelines.get(StringUtils.upperCase(name));
    }


    @Override
    public boolean pipelineSupportsBatch(String pipelineName) {
    	return pipelineSupportsProcessingType(pipelineName, AlgorithmDefinition::supportsBatchProcessing);
    }


    @Override
    public boolean pipelineSupportsStreaming(String pipelineName) {
        return pipelineSupportsProcessingType(pipelineName, AlgorithmDefinition::supportsStreamProcessing);
    }


    private boolean pipelineSupportsProcessingType(String pipelineName, Predicate<AlgorithmDefinition> supportsPred) {
        PipelineDefinition pipeline = getPipeline(pipelineName);
        if (pipeline == null) {
	        throw new InvalidPipelineObjectWfmProcessingException("Missing pipeline: \"" + pipelineName + "\".");
        }
        return pipeline.getTaskRefs().stream()
                .map(TaskDefinitionRef::getName)
		        .allMatch(tName -> taskSupportsProcessingType(tName, supportsPred));
    }


    @Override
    public boolean taskSupportsBatch(String taskName) {
    	return taskSupportsProcessingType(taskName, AlgorithmDefinition::supportsBatchProcessing);
    }

    @Override
    public boolean taskSupportsStreaming(String taskName) {
        return taskSupportsProcessingType(taskName, AlgorithmDefinition::supportsStreamProcessing);

    }

    private boolean taskSupportsProcessingType(String taskName, Predicate<AlgorithmDefinition> supportsPred) {
        TaskDefinition task = getTask(taskName);
        return task.getActions().stream()
                .map(ActionDefinitionRef::getName)
		        .allMatch(actionName -> actionSupportsProcessingType(actionName, supportsPred));
    }

    @Override
    public boolean actionSupportsBatch(String actionName) {
        return actionSupportsProcessingType(actionName, AlgorithmDefinition::supportsBatchProcessing);
    }

    @Override
    public boolean actionSupportsStreaming(String actionName) {
        return actionSupportsProcessingType(actionName, AlgorithmDefinition::supportsStreamProcessing);
    }


    private boolean actionSupportsProcessingType(String actionName, Predicate<AlgorithmDefinition> supportsPred) {
        ActionDefinition action = getAction(actionName);
        AlgorithmDefinition algorithm = getAlgorithm(action);
        return supportsPred.test(algorithm);
    }


    /** Forgets all of the previously-added pipelines, tasks, actions, and algorithms. */
    @Override
    public void reset() {
        pipelines.clear();
        tasks.clear();
        actions.clear();
        algorithms.clear();
    }

    private void addAlgorithm(AlgorithmDefinition algorithm) {
        algorithm.getProvidesCollection().getAlgorithmProperties()
                .stream()
                .filter(pd -> pd.getPropertiesKey() != null)
                .forEach(pd -> pd.setDefaultValue(propertiesUtil.lookup(pd.getPropertiesKey())));

        validateAlgorithm(algorithm);
        log.debug("{}: Adding algorithm", StringUtils.upperCase(algorithm.getName()));
        algorithms.put(StringUtils.upperCase(algorithm.getName()), algorithm);
    }


    @Override
    public void deleteAlgorithm(String algorithmName) {
        algorithms.remove(algorithmName);
        writeAlgorithmsToDisk();
    }


    @Override
    public void saveAlgorithm(AlgorithmDefinition algorithmDefinition) {
        addAlgorithm(algorithmDefinition);
        writeAlgorithmsToDisk();
    }


    private void writeAlgorithmsToDisk() {
        AlgorithmDefinitionCollection algoDefs = new AlgorithmDefinitionCollection();
        algorithms.forEach((n, a) -> algoDefs.getAlgorithms().add(a));
        xStreamSave(algoDefs, propertiesUtil.getAlgorithmDefinitions());
    }



    private void addAction(ActionDefinition actionNode) {
        validateAction(actionNode);
        log.debug("{}: Adding action", StringUtils.upperCase(actionNode.getName()));
        actions.put(StringUtils.upperCase(actionNode.getName()), actionNode);
    }

    @Override
    public void deleteAction(String actionName) {
        actions.remove(actionName);
        writeActionsToDisk();
    }

    @Override
    public void saveAction(ActionDefinition action) {
    	addAction(action);
        writeActionsToDisk();
    }

    private void writeActionsToDisk() {
        ActionDefinitionCollection actionDefs = new ActionDefinitionCollection();
        actions.forEach((n, a) -> actionDefs.getActionDefinitions().add(a));
        xStreamSave(actionDefs, propertiesUtil.getActionDefinitions());
    }


    private void addTask(TaskDefinition task) {
        validateTask(task);

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
    }

    @Override
    public void deleteTask(String taskName) {
        tasks.remove(taskName);
        writeTasksToDisk();
    }

    @Override
    public void saveTask(TaskDefinition task) {
        addTask(task);
        writeTasksToDisk();
    }

    private void writeTasksToDisk() {
        TaskDefinitionCollection tasksDefs = new TaskDefinitionCollection();
        tasks.forEach((n, t) -> tasksDefs.getTasks().add(t));
        xStreamSave(tasksDefs, propertiesUtil.getTaskDefinitions());
    }


    private void addPipeline(PipelineDefinition pipeline) {
        validatePipeline(pipeline);
        log.debug("{}: Adding pipeline", StringUtils.upperCase(pipeline.getName()));
        pipelines.put(StringUtils.upperCase(pipeline.getName()), pipeline);
    }

    @Override
    public void deletePipeline(String pipelineName) {
        pipelines.remove(pipelineName);
        writePipelinesToDisk();
    }

    @Override
    public void savePipeline(PipelineDefinition pipeline) {
        addPipeline(pipeline);
        writePipelinesToDisk();
    }

    private void writePipelinesToDisk() {
        PipelineDefinitionCollection pipelineDefs = new PipelineDefinitionCollection();
        pipelines.forEach((n, p) -> pipelineDefs.add(p));
        xStreamSave(pipelineDefs, propertiesUtil.getPipelineDefinitions());
    }


    private void xStreamSave(Object object, WritableResource resource) {
        try (OutputStream outStream = resource.getOutputStream()) {
            xStream.toXML(object, outStream);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Check that the algorithm is not null, is valid, and has a name which has not already been added to the PipelineManager instance. Returns false if the algorithm is not valid.*/
    private void validateAlgorithm(AlgorithmDefinition algorithm) {
        if (algorithm == null) {
            throw new CannotBeNullWfmProcessingException("Algorithm cannot be null.");
        } else if (!algorithm.isValid()) {
            throw new InvalidPipelineObjectWfmProcessingException("Algorithm is not valid.");
        } else if (getAlgorithm(algorithm.getName()) != null) {
            throw new DuplicateNameWfmProcessingException(StringUtils.upperCase(algorithm.getName()) + ": algorithm name is already in use.");
        }
    }

    /** Check that the task is not null, is valid, references an algorithm which already exists in the PipelineManager, has a name which has not already been added to the PipelineManager instance, and has properties associated with it which are valid for the referenced algorithm. Returns false if the action is invalid. */
    private void validateAction(ActionDefinition actionDefinition) {
        if (actionDefinition == null) {
            throw new CannotBeNullWfmProcessingException("Action cannot be null.");
        } else if (!actionDefinition.isValid()) {
            throw new InvalidPipelineObjectWfmProcessingException("Action is not valid.");
        } else if (getAlgorithm(actionDefinition) == null) {
            throw new InvalidPropertyWfmProcessingException(StringUtils.upperCase(actionDefinition.getName()) + ": referenced algorithm " + actionDefinition.getAlgorithmRef() + " does not exist.");
        } else if (getAction(actionDefinition.getName()) != null) {
            throw new DuplicateNameWfmProcessingException(StringUtils.upperCase(actionDefinition.getName()) + ": action name is already in use.");
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
                        throw new InvalidPropertyWfmProcessingException(new StringBuilder().append(actionDefinition.getName())
                                .append(": The referenced algorithm (")
                                .append(actionDefinition.getAlgorithmRef())
                                .append(") does not expose the property ")
                                .append(propertyValue.getName())
                                .append(".").toString());
                    } else if (!isValidValueForType(propertyValue.getValue(), algorithm.getProvidesCollection().getAlgorithmProperty(propertyValue.getName()).getType())) {
                        // Otherwise, if the provided value for the property is not acceptable given the property's defined type...
                        log.error("{}.{}: The provided value of '{}' cannot be converted to type {}.",
                                actionDefinition.getName(),
                                propertyValue.getName(),
                                propertyValue.getValue(),
                                algorithm.getProvidesCollection().getAlgorithmProperty(propertyValue.getName()).getType());
                        throw new InvalidPropertyWfmProcessingException(new StringBuilder().append(actionDefinition.getName())
                                .append(".")
                                .append(propertyValue.getName())
                                .append(": The provided value of '")
                                .append(propertyValue.getValue())
                                .append("' cannot be converted to type ")
                                .append(algorithm.getProvidesCollection().getAlgorithmProperty(propertyValue.getName()).getType())
                                .append(".").toString());
                    }
                }
            } else {
                log.debug("{}: no properties were provided", actionDefinition.getName());
            }
        }
    }

    /** Check that the task is not null, is valid, has a name which has not already been added to the PipelineManager,
     *  references only actions which have already been added to the PipelineManager,
     *  all actions support either batch or streaming, and if more than one action is specified,
     *  check that all referenced actions are of the same Operation.
     */
    private void validateTask(TaskDefinition task) {
        if (task == null) {
            throw new CannotBeNullWfmProcessingException("Task cannot be null.");
        } else if (!task.isValid()) {
            throw new InvalidPipelineObjectWfmProcessingException("Task is not valid.");
        } else if (getTask(task.getName()) != null) {
            throw new DuplicateNameWfmProcessingException(StringUtils.upperCase(task.getName()) + ": task name is already in use.");
        } else {
            ActionType actionType = null;

            for (ActionDefinitionRef actionRef : task.getActions()) {
                if (getAction(actionRef) == null) {
                    log.error("{}: The referenced action ({}) does not exist",
                            StringUtils.upperCase(task.getName()),
                            StringUtils.upperCase(actionRef.getName()));
                    throw new InvalidActionWfmProcessingException(new StringBuilder().append(task.getName())
                            .append(": The referenced action (")
                            .append(actionRef.getName())
                            .append(") does not exist.").toString());
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
                        throw new InvalidActionWfmProcessingException(new StringBuilder().append(task.getName())
                                .append(": task cannot contain actions which have different ActionTypes. The first ActionType for this task was ")
                                .append(actionType)
                                .append(", but ")
                                .append(actionRef.getName())
                                .append(" has an ActionType of ")
                                .append(otherActionType)
                                .append(".").toString());
                    }
                }
            }

            boolean supportsBatch = task.getActions().stream()
                    .allMatch(adr -> actionSupportsBatch(adr.getName()));
            if (!supportsBatch) {
                boolean supportsStreaming = task.getActions().stream()
                        .allMatch(adr -> actionSupportsStreaming(adr.getName()));
                if (!supportsStreaming) {
                    throw new InvalidTaskWfmProcessingException(String.format(
                            "The %s task does not fully support batch or stream processing", task.getName()));
                }
            }
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

    /** Check that the pipeline is not null, is valid, has a name which is unique in the PipelineManager,
     *  references valid tasks, that the states (using provides/requires) are valid for the proposed sequence of
     *  tasks, and all of the tasks support either batch or streaming.
     */
    private void validatePipeline(PipelineDefinition pipeline) {
        if (pipeline == null) {
            throw new CannotBeNullWfmProcessingException("Pipeline cannot be null.");
        } else if (!pipeline.isValid()) {
            throw new InvalidPipelineObjectWfmProcessingException("Pipeline is not valid.");
        } else if (getPipeline(pipeline.getName()) != null) {
            throw new DuplicateNameWfmProcessingException(StringUtils.upperCase(pipeline.getName()) + ": pipeline name is already in use.");
        }
        validateTasks(StringUtils.upperCase(pipeline.getName()), pipeline.getTaskRefs());

        boolean supportsBatch = pipeline.getTaskRefs().stream()
                .allMatch(tdr -> taskSupportsBatch(tdr.getName()));
        if (!supportsBatch) {
            boolean supportsStreaming = pipeline.getTaskRefs().stream()
                    .allMatch(tdr -> taskSupportsStreaming(tdr.getName()));

            if (!supportsStreaming) {
                throw new InvalidPipelineObjectWfmProcessingException(String.format(
                        "The %s pipeline does not full support batch processing or stream processing",
                        pipeline.getName()));
            }
        }
    }

    private ActionType getTaskType(TaskDefinition taskDefinition) {
        return taskDefinition == null ? null : getAlgorithm(getAction(taskDefinition.getActions().get(0))).getActionType();
    }

    private ActionType getTaskType(TaskDefinitionRef taskDefinitionRef) {
        return taskDefinitionRef == null ? null : getTaskType(getTask(taskDefinitionRef));
    }

    private void validateTasks(String pipelineName, List<TaskDefinitionRef> taskRefs) {
        if(getTask(taskRefs.get(0)) == null) {
            log.error("{}: Task with name {} does not exist.",
                    pipelineName,
                    taskRefs.get(0).getName());

            throw new InvalidTaskWfmProcessingException(new StringBuilder().append(pipelineName)
                    .append(": Task with name ")
                    .append(taskRefs.get(0).getName())
                    .append(" does not exist.").toString());
        } else {
            switch (getTaskType(taskRefs.get(0))) {
                case DETECTION:
                    validateDetectionPipeline(pipelineName, taskRefs);
                    return;
                default:
                    log.error("{}: {} - pipelines may not start with tasks of the type {}.",
                            pipelineName,
                            getTaskType(taskRefs.get(0)),
                            taskRefs.get(0).getName());
                    throw new InvalidTaskWfmProcessingException(new StringBuilder().append(pipelineName)
                            .append(": ")
                            .append(taskRefs.get(0).getName())
                            .append(" - pipelines may not start with tasks of the type ")
                            .append(getTaskType(taskRefs.get(0)))
                            .append(".").toString());
            }
        }
    }

    private void validateDetectionPipeline(String pipelineName, List<TaskDefinitionRef> taskDefinitions) {
        validateDetectionPipeline(pipelineName, taskDefinitions, new HashSet<StateDefinition>(10));
    }

    private void validateDetectionPipeline(String pipelineName, List<TaskDefinitionRef> taskDefinitions, Set<StateDefinition> currentStates) {
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
                throw new InvalidTaskWfmProcessingException(new StringBuilder()
                        .append(pipelineName)
                        .append(": The states for ")
                        .append(taskDefinitions.get(0).getName())
                        .append(" are not satisfied. Current: ")
                        .append(currentStates)
                        .append(". Required: ")
                        .append(requiredStates)
                        .append(".").toString());
            }
        } else if (getTask(taskDefinitions.get(0)).getActions().size() > 1) {
            // There's more than one task, and the number of actions in the first task exceeds 1.
            log.error("{}: No tasks may follow the multi-detection task of {}.", pipelineName, taskDefinitions.get(0).getName());
            throw new InvalidTaskWfmProcessingException(new StringBuilder()
                    .append(pipelineName)
                    .append(": No tasks may follow the multi-detection task of ")
                    .append(taskDefinitions.get(0).getName())
                    .append(".").toString());
        } else if(getTask(taskDefinitions.get(1)) == null) {
            // At this point, we've determined there's at least one more task. The next task name must exist.
            log.error("{}: Task with name {} does not exist.", pipelineName, taskDefinitions.get(1).getName());
            throw new InvalidTaskWfmProcessingException(new StringBuilder()
                    .append(pipelineName)
                    .append(": Task with name ")
                    .append(taskDefinitions.get(1).getName())
                    .append(" does not exist.").toString());
        } else {
            currentStates.addAll(providedTaskStates.get(StringUtils.upperCase(taskDefinitions.get(0).getName())));
            ActionType nextTaskType = getTaskType(taskDefinitions.get(1));
            switch (nextTaskType) {
                case DETECTION:
                    currentStates.clear(); // If the next task is detection-based, we should disregard the current states as we're downselecting.
                    validateDetectionPipeline(pipelineName, taskDefinitions.subList(1, taskDefinitions.size()), currentStates);
                    return;
                case MARKUP:
                    validateMarkupPipeline(pipelineName, taskDefinitions.subList(1, taskDefinitions.size()), currentStates);
                    return;
                default:
                    log.error("{}: {} is a detection task and may not be followed by {}.",
                            pipelineName,
                            taskDefinitions.get(0).getName(),
                            taskDefinitions.get(1).getName());
                    throw new InvalidTaskWfmProcessingException(new StringBuilder()
                            .append(pipelineName)
                            .append(": ")
                            .append(taskDefinitions.get(0).getName())
                            .append(" is a detection task and may not be followed by ")
                            .append(taskDefinitions.get(1).getName())
                            .append(".").toString());
            }
        }
    }

    private void validateMarkupPipeline(String pipelineName, List<TaskDefinitionRef> taskDefinitions, Set<StateDefinition> currentStates) {
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
                throw new InvalidTaskWfmProcessingException(new StringBuilder()
                        .append(pipelineName)
                        .append(": The states for ")
                        .append(taskDefinitions.get(0).getName())
                        .append(" are not satisfied. Current: ")
                        .append(currentStates)
                        .append(". Required: ")
                        .append(requiredStates)
                        .append(".").toString());
            }
        } else {
            log.error("{}: No tasks may follow a markup task of {}.", pipelineName, taskDefinitions.get(0).getName());
            throw new InvalidTaskWfmProcessingException(new StringBuilder()
                    .append(pipelineName)
                    .append(": No tasks may follow a markup task of ")
                    .append(taskDefinitions.get(0).getName())
                    .append(".").toString());
        }
    }
}
