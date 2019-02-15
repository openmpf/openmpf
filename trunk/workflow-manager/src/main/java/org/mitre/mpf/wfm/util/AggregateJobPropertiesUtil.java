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

package org.mitre.mpf.wfm.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import org.mitre.mpf.interop.*;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.pipeline.xml.*;
import org.mitre.mpf.wfm.service.PipelineService;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

// Updated calculateValue methods to support new algorithmProperties.  Note the need to support actionProperties both as
// a Map<String,String> and as a Collection<PropertyDefinitionRef>.  Also, need to allow for passing in which algorithm is to be checked
// in algorithmProperties, where the currently processed (or defined) algorithm could originate from a
// TransientAction or an ActionDefinition.  Both of these Action objects would include the algorithm that is currently being processed,
// and should be used to set the scope for which algorithmProperty (key: AlgorithmName) is to be used to
// check for the propertyName being provided

@Component
public class AggregateJobPropertiesUtil {

    private final PipelineService _pipelineService;

    private final HibernateJobRequestDao _jobRequestDao;

    private final JsonUtils _jsonUtils;

    @Inject
    public AggregateJobPropertiesUtil(
            PipelineService pipelineService,
            HibernateJobRequestDao jobRequestDao,
            JsonUtils jsonUtils) {
        _pipelineService = pipelineService;
        _jobRequestDao = jobRequestDao;
        _jsonUtils = jsonUtils;
    }


    public enum PropertyLevel { NONE, SYSTEM, ACTION, JOB, ALGORITHM, MEDIA }; // in order of precedence

    public static class PropertyInfo {
        private String name;
        public String getName() {
            return name;
        }

        private String value;
        public String getValue() {
            return value;
        }

        private PropertyLevel level;
        public PropertyLevel getLevel() {
            return level;
        }

        public double getNumericValue() {
            return Double.parseDouble(value);
        }

        public boolean isLessThanOrEqualTo(double compare) {
            return getNumericValue() <= compare;
        }

        public PropertyInfo(String name, String value, PropertyLevel level) {
            this.name = name;
            this.value = value;
            this.level = level;
        }
    }

    /** private method to return the value of the named property, checking for that property in each of the categories of property collections,
     * using the priority scheme:
     * action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
     * where the job-algorithm properties are restricted to the algorithm defined in the Action currently being processed.
     * Note: changed this method to private since this version of the calculateValue method is only being called within this class
     * @param propertyName property name to check for
     * @param actionProperties lowest priority action properties (i.e. may also be called pipeline properties)
     * @param jobProperties job properties which are possed in to the JSON job request
     * @param algorithmNameFromAction algorithm name from the currently defined action
     * @param algorithmProperties algorithm properties (algorithm properties specific to this job)
     * @return property info after checking for that property within the prioritized categories of property containers
     */
    private static PropertyInfo calculateValue(String propertyName, Map<String,String> actionProperties,
                                               Map<String, String> jobProperties,
                                               String algorithmNameFromAction,
                                               Table<String, String, String> algorithmProperties) {
        String algoPropValue = algorithmProperties.get(algorithmNameFromAction, propertyName);
        if (algoPropValue != null) {
            return new PropertyInfo(propertyName, algoPropValue, PropertyLevel.ALGORITHM);
        }

        if (jobProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, jobProperties.get(propertyName), PropertyLevel.JOB);
        }

        if (actionProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, actionProperties.get(propertyName), PropertyLevel.ACTION);
        }

        return new PropertyInfo(propertyName, null, PropertyLevel.NONE);
    }

    /** Return the value of the named property, checking for that property in each of the categories of property collections,
     * using the priority scheme:
     * action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
     * where the algorithm properties are restricted to the algorithm identified in the Action definition.
     * @param propertyName property name to check for
     * @param actionProperties lowest priority action properties (i.e. may also be called pipeline properties)
     * @param jobProperties job properties which are possed in to the JSON job request
     * @param actionDefinition Action definition
     * @param algorithmProperties algorithm properties (algorithm properties specific to this job)
     * @param mediaProperties highest priority media properties
     * @return property info after checking for that property within the prioritized categories of property containers
     */
    public static PropertyInfo calculateValue(String propertyName, Map<String,String> actionProperties,
                                              Map<String, String> jobProperties,
                                              ActionDefinition actionDefinition,
                                              Table<String, String, String> algorithmProperties,
                                              Map<String, String> mediaProperties) {

        if (mediaProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, mediaProperties.get(propertyName), PropertyLevel.MEDIA);
        }

        return calculateValue(propertyName, actionProperties, jobProperties, actionDefinition.getAlgorithmRef(), algorithmProperties);
    }

    /** Return the value of the named property, checking for that property in each of the categories of property collections,
     * using the priority scheme:
     * action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
     * where the algorithm properties are restricted to the algorithm defined in the Action currently being processed.
     * @param propertyName property name to check for
     * @param actionProperties lowest priority action properties (i.e. may also be called pipeline properties)
     * @param jobProperties job properties which are possed in to the JSON job request
     * @param transientAction Action currently being processed
     * @param algorithmProperties algorithm properties (algorithm properties specific to this job)
     * @param mediaProperties highest priority media properties
     * @return property info after checking for that property within the prioritized categories of property containers
     */
    public static PropertyInfo calculateValue(String propertyName, Map<String,String> actionProperties,
                                              Map<String, String> jobProperties,
                                              TransientAction transientAction,
                                              Table<String, String, String> algorithmProperties,
                                              Map<String, String> mediaProperties) {

        if (mediaProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, mediaProperties.get(propertyName), PropertyLevel.MEDIA);
        }

        return calculateValue(propertyName, actionProperties, jobProperties, transientAction.getAlgorithm(), algorithmProperties);
    }

    /** Return the value of the named property, checking for that property in each of the categories of property collections,
     * using the priority scheme:
     * action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
     * where the algorithm properties are restricted to the algorithm identified in the Action definition.
     * @param propertyName property name to check for
     * @param actionProperties lowest priority action properties (i.e. may also be called pipeline properties)
     * @param jobProperties job properties which are possed in to the JSON job request
     * @param actionDefinition Action definition
     * @param algorithmProperties algorithm properties (algorithm properties specific to this job)
     * @param mediaProperties highest priority media properties
     * @return property info after checking for that property within the prioritized categories of property containers
     */
    public static PropertyInfo calculateValue(String propertyName, Collection<PropertyDefinitionRef> actionProperties,
                                              Map<String, String> jobProperties,
                                              ActionDefinition actionDefinition,
                                              Table<String, String, String> algorithmProperties,
                                              Map<String, String> mediaProperties) {

        if (mediaProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, mediaProperties.get(propertyName), PropertyLevel.MEDIA);
        }

        String algoPropVal = algorithmProperties.get(actionDefinition.getAlgorithmRef(), propertyName);
        if (algoPropVal != null) {
            return new PropertyInfo(propertyName, algoPropVal, PropertyLevel.ALGORITHM);
        }

        if (jobProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, jobProperties.get(propertyName), PropertyLevel.JOB);
        }

        for (PropertyDefinitionRef prop : actionProperties) {
            if (propertyName.equals(prop.getName())) {
                return new PropertyInfo(propertyName, prop.getValue(), PropertyLevel.ACTION);
            }
        }

        return new PropertyInfo(propertyName, null, PropertyLevel.NONE);
    }

    /** Return the value of the named property, checking for that property in each of the categories of property collections,
     * using the priority scheme:
     * action-property defaults (lowest) -> action-properties -> job-properties -> algorithm-properties -> media-properties (highest)
     * where the algorithm properties are restricted to the algorithm defined in the Action currently being processed.
     * @param propertyName property name to check for
     * @param actionProperties lowest priority action properties (i.e. may also be called pipeline properties)
     * @param jobProperties job properties which are possed in to the JSON job request
     * @param transientAction Action currently being processed
     * @param algorithmProperties algorithm properties (algorithm properties specific to this job)
     * @param mediaProperties highest priority media properties
     * @return property info after checking for that property within the prioritized categories of property containers
     */
    public static PropertyInfo calculateValue(String propertyName, Collection<PropertyDefinitionRef> actionProperties,
                                              Map<String, String> jobProperties,
                                              TransientAction transientAction,
                                              Map<String,Map> algorithmProperties,
                                              Map<String, String> mediaProperties) {

        if (mediaProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, mediaProperties.get(propertyName), PropertyLevel.MEDIA);
        }

        if (transientAction.getAlgorithm() != null &&
                algorithmProperties.containsKey(transientAction.getAlgorithm()) &&
                algorithmProperties.get(transientAction.getAlgorithm()).containsKey(propertyName) ) {
            // if the algorithm properties includes the algorithm currently being run, then get the property value from
            // the algorithm properties
            Map<String,String> m = algorithmProperties.get(transientAction.getAlgorithm());
            return new PropertyInfo(propertyName, m.get(propertyName), PropertyLevel.ALGORITHM);
        }

        if (jobProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, jobProperties.get(propertyName), PropertyLevel.JOB);
        }

        for (PropertyDefinitionRef prop : actionProperties) {
            if (propertyName.equals(prop.getName())) {
                return new PropertyInfo(propertyName, prop.getValue(), PropertyLevel.ACTION);
            }
        }

        return new PropertyInfo(propertyName, null, PropertyLevel.NONE);
    }


    public static Map<String, String> getCombinedJobProperties(AlgorithmDefinition algorithm,
                                                               TransientAction action,
                                                               TransientStreamingJob job) {

    	Map<String, String> overriddenJobProps = job.getOverriddenJobProperties();
        Map<String, String> overriddenAlgoProps = job.getOverriddenAlgorithmProperties().row(algorithm.getName());
        Map<String, String> mediaSpecificProps = job.getStream().getMediaProperties();
        return getCombinedJobProperties(algorithm, action, overriddenJobProps, overriddenAlgoProps, mediaSpecificProps);
    }


    public static Map<String, String> getCombinedJobProperties(AlgorithmDefinition algorithm,
                                                               TransientAction action,
                                                               Map<String, String> overriddenJobProperties,
                                                               Map<String, String> overriddenAlgorithmProperties,
                                                               Map<String, String> mediaSpecificProperties) {

    	Map<String, String> combined = getAlgorithmProperties(algorithm);
    	combined.putAll(action.getProperties());
    	combined.putAll(overriddenJobProperties);
    	combined.putAll(overriddenAlgorithmProperties);
    	combined.putAll(mediaSpecificProperties);
    	return combined;
    }


    public static Map<String, String> getAlgorithmProperties(AlgorithmDefinition algorithm) {
        return algorithm
                .getProvidesCollection()
                .getAlgorithmProperties()
                .stream()
                .collect(toMap(PropertyDefinition::getName,
                               PropertyDefinition::getDefaultValue,
                               (x, y) -> y, HashMap::new));
    }


    public static String calculateFrameInterval(TransientAction transientAction, TransientJob transientJob,
                                                TransientMedia transientMedia,
                                                int systemFrameInterval, int systemFrameRateCap, double mediaFPS) {

        PropertyInfo frameIntervalPropInfo = AggregateJobPropertiesUtil.calculateValue(
                MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY,
                transientAction.getProperties(),
                transientJob.getOverriddenJobProperties(),
                transientAction,
                transientJob.getOverriddenAlgorithmProperties(),
                transientMedia.getMediaSpecificProperties());

        PropertyInfo frameRateCapPropInfo = AggregateJobPropertiesUtil.calculateValue(
                MpfConstants.FRAME_RATE_CAP_PROPERTY,
                transientAction.getProperties(),
                transientJob.getOverriddenJobProperties(),
                transientAction,
                transientJob.getOverriddenAlgorithmProperties(),
                transientMedia.getMediaSpecificProperties());

        if (frameIntervalPropInfo.getLevel() == PropertyLevel.NONE) {
            frameIntervalPropInfo = new PropertyInfo(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY,
                    Integer.toString(systemFrameInterval), PropertyLevel.SYSTEM);
        }

        if (frameRateCapPropInfo.getLevel() == PropertyLevel.NONE) {
            frameRateCapPropInfo = new PropertyInfo(MpfConstants.FRAME_RATE_CAP_PROPERTY,
                    Integer.toString(systemFrameRateCap), PropertyLevel.SYSTEM);
        }

        PropertyInfo propInfoToUse;
        if (frameRateCapPropInfo.getLevel().ordinal() >= frameIntervalPropInfo.getLevel().ordinal()) {
            propInfoToUse = frameRateCapPropInfo; // prefer frame rate cap
        } else {
            propInfoToUse = frameIntervalPropInfo;
        }

        if (propInfoToUse.isLessThanOrEqualTo(0)) {
            if (propInfoToUse.getName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)) {
                propInfoToUse = frameRateCapPropInfo;
            } else {
                propInfoToUse = frameIntervalPropInfo;
            }
        }

        if (propInfoToUse.isLessThanOrEqualTo(0)) {
            return "1"; // frame interval and frame rate cap are both disabled
        }

        if (propInfoToUse.getName().equals(MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY)) {
            return propInfoToUse.getValue();
        }

        int calcFrameInterval = (int) Math.max(1, Math.floor(mediaFPS / frameRateCapPropInfo.getNumericValue()));
        return Integer.toString(calcFrameInterval);
    }



    public static Function<String, String> getCombinedProperties(TransientJob job, TransientMedia media) {
        List<Map<String, String>> propertyMaps = Arrays.asList(
                media.getMediaSpecificProperties(),
                job.getOverriddenJobProperties());

        return propName -> getPropertyValue(propName, propertyMaps);
    }


    public Function<String, String> getCombinedProperties(TransientJob job, long mediaId, int stageIndex,
                                                          int actionIndex) {
        TransientMedia media = job.getMedia(mediaId);
        TransientStage stage = job.getPipeline().getStages().get(stageIndex);
        TransientAction action = stage.getActions().get(actionIndex);
        String algoName = action.getAlgorithm();
        ImmutableMap<String, String> overriddenAlgoProps = job.getOverriddenAlgorithmProperties().row(algoName);

        List<Map<String, String>> propertyMaps = Arrays.asList(
                media.getMediaSpecificProperties(),
                overriddenAlgoProps,
                job.getOverriddenJobProperties(),
                action.getProperties());

        return propName -> getPropertyValue(propName, propertyMaps, algoName);
    }





    private static String getPropertyValue(String propName, Iterable<Map<String, String>> propertyMaps) {
        return getPropertyValue(propName, propertyMaps, x -> null);
    }


    private String getPropertyValue(String propName, Iterable<Map<String, String>> propertyMaps,
                                    String algoName) {
        return getPropertyValue(propName, propertyMaps, pName -> lookupAlgorithmProperty(algoName, pName));
    }


    private String lookupAlgorithmProperty(String algoName, String propName) {
        return Optional.ofNullable(_pipelineService.getAlgorithm(algoName))
                .map(a -> a.getProvidesCollection().getAlgorithmProperty(propName))
                .map(PropertyDefinition::getDefaultValue)
                .orElse(null);
    }


    private static String getPropertyValue(String propName, Iterable<Map<String, String>> propertyMaps,
                                           Function<String, String> lowestPriorityLookup) {
        for (Map<String, String> propertyMap : propertyMaps) {
            String value = propertyMap.get(propName);
            // If value is null, it is either missing or actually mapped to null.
            if (value != null || propertyMap.containsKey(propName)) {
                return value;
            }
        }
        return lowestPriorityLookup.apply(propName);
    }


    public Function<String, String> getCombinedPropertiesAfterJobCompletion(MarkupResult markup) {
        JsonJobRequest jsonJobRequest = Optional.ofNullable(_jobRequestDao.findById(markup.getJobId()))
                .map(JobRequest::getInputObject)
                .map(b -> _jsonUtils.deserialize(b, JsonJobRequest.class))
                .orElse(null);

        if (jsonJobRequest == null) {
           return propName -> null;
        }
        else {
            Map<String, String> mediaProperties = jsonJobRequest.getMedia()
                    .stream()
                    .filter(m -> m.getMediaUri().equals(markup.getSourceUri()))
                    .map(JsonMediaInputObject::getProperties)
                    .findAny()
                    .orElseGet(Collections::emptyMap);

            return propName -> getPropertyValueAfterJobCompletion(propName, markup, mediaProperties, jsonJobRequest);
        }
    }


    private String getPropertyValueAfterJobCompletion(
            String propName, MarkupResult markup, Map<String, String> mediaProperties, JsonJobRequest jobRequest)  {

        String mediaPropVal = mediaProperties.get(propName);
        // If value is null, it is either missing or actually mapped to null.
        if (mediaPropVal != null || mediaProperties.containsKey(propName)) {
            return mediaPropVal;
        }
        JsonAction jsonAction = getAction(jobRequest.getPipeline(), markup.getTaskIndex(), markup.getActionIndex());
        if (jsonAction != null) {
            // Check overridden algorithm properties
            Map<?, ?> overriddenAlgoProps = jobRequest.getAlgorithmProperties().get(jsonAction.getName());
            if (overriddenAlgoProps != null) {
                Object overriddenAlgoPropVal = overriddenAlgoProps.get(propName);
                if (overriddenAlgoPropVal != null) {
                    return overriddenAlgoPropVal.toString();
                }
                if (overriddenAlgoProps.containsKey(propName)) {
                    return null;
                }
            }
        }

        // Job Properties
        String jobPropVal = jobRequest.getJobProperties().get(propName);
        if (jobPropVal != null || jobRequest.getJobProperties().containsKey(propName)) {
            return jobPropVal;
        }

        if (jsonAction == null) {
            return null;
        }

        // Overridden Action Properties
        String actionPropVal = jsonAction.getProperties().get(propName);
        if (actionPropVal != null || jsonAction.getProperties().containsKey(propName)) {
            return actionPropVal;
        }

        // Default Action Properties
        ActionDefinition actionDef = _pipelineService.getAction(jsonAction.getName());
        if (actionDef != null) {
            PropertyDefinitionRef propDefRef = actionDef.getProperties()
                    .stream()
                    .filter(ap -> propName.equals(ap.getName()))
                    .findAny()
                    .orElse(null);
            if (propDefRef != null) {
                return propDefRef.getValue();
            }
        }

        // Default Algorithm Properties
        AlgorithmDefinition algorithm = _pipelineService.getAlgorithm(jsonAction.getName());
        if (algorithm == null) {
            return null;
        }
        PropertyDefinition algorithmProperty = algorithm.getProvidesCollection().getAlgorithmProperty(propName);
        if (algorithmProperty != null) {
            return algorithmProperty.getDefaultValue();
        }
        return null;
    }


    private static JsonAction getAction(JsonPipeline pipeline, int taskIndex, int actionIndex) {
        if (pipeline.getStages().size() <= taskIndex) {
            return null;
        }
        JsonStage stage = pipeline.getStages().get(taskIndex);
        if (stage.getActions().size() <= actionIndex) {
            return null;
        }
        return stage.getActions().get(actionIndex);
    }
}