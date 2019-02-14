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
import org.mitre.mpf.wfm.data.entities.transients.*;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.pipeline.xml.ActionDefinition;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinitionRef;
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

    @Inject
    public AggregateJobPropertiesUtil(PipelineService pipelineService) {
        _pipelineService = pipelineService;
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

        return k -> getPropertyValue(k, propertyMaps);
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

        return k -> getPropertyValue(k, propertyMaps, algoName);
    }



    private static String getPropertyValue(String key, Iterable<Map<String, String>> propertyMaps) {
        return getPropertyValue(key, propertyMaps, x -> null);
    }


    private String getPropertyValue(String key, Iterable<Map<String, String>> propertyMaps,
                                    String algoName) {
        return getPropertyValue(key, propertyMaps, pName -> lookupAlgorithmProperty(algoName, pName));
    }


    private String lookupAlgorithmProperty(String algoName, String propName) {
        return Optional.ofNullable(_pipelineService.getAlgorithm(algoName))
                .map(a -> a.getProvidesCollection().getAlgorithmProperty(propName))
                .map(PropertyDefinition::getDefaultValue)
                .orElse(null);
    }


    private static String getPropertyValue(String key, Iterable<Map<String, String>> propertyMaps,
                                           Function<String, String> lowestPriorityLookup) {
        for (Map<String, String> propertyMap : propertyMaps) {
            String value = propertyMap.get(key);
            // If value is null, it is either missing or actually mapped to null.
            if (value != null || propertyMap.containsKey(key)) {
                return value;
            }
        }
        return lowestPriorityLookup.apply(key);
    }
}