/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

import org.mitre.mpf.wfm.data.entities.transients.TransientAction;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJob;
import org.mitre.mpf.wfm.pipeline.xml.ActionDefinition;
import org.mitre.mpf.wfm.pipeline.xml.AlgorithmDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinition;
import org.mitre.mpf.wfm.pipeline.xml.PropertyDefinitionRef;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

// Updated calculateValue methods to support new algorithmProperties.  Note the need to support actionProperties both as
// a Map<String,String> and as a Collection<PropertyDefinitionRef>.  Also, need to allow for passing in which algorithm is to be checked
// in algorithmProperties, where the currently processed (or defined) algorithm could originate from a
// TransientAction or an ActionDefinition.  Both of these Action objects would include the algorithm that is currently being processed,
// and should be used to set the scope for which algorithmProperty (key: AlgorithmName) is to be used to
// check for the propertyName being provided


public class AggregateJobPropertiesUtil {

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
     * @return value of the property name after checking for that property within the prioritized categories of property containers or null if not found
     */
    private static String calculateValue(String propertyName, Map<String,String> actionProperties, Map<String, String> jobProperties,
                                         String algorithmNameFromAction,
                                         Map<String,Map> algorithmProperties) {
        if (algorithmNameFromAction != null && algorithmProperties.containsKey(algorithmNameFromAction) &&
                algorithmProperties.get(algorithmNameFromAction).containsKey(propertyName) ) {
            // if the job-algorithm properties includes the algorithm currently being run, then get the property value from
            // the job-algorithm properties
            Map<String, String> m = algorithmProperties.get(algorithmNameFromAction);
            return m.get(propertyName);
        } else if (jobProperties.containsKey(propertyName)) {
            return jobProperties.get(propertyName);
        } else {
            return actionProperties.get(propertyName);
        }
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
     * @return value of the property name after checking for that property within the prioritized categories of property containers or null if not found
     */
    public static String calculateValue(String propertyName, Map<String,String> actionProperties, Map<String, String> jobProperties,
                                        ActionDefinition actionDefinition,
                                        Map<String,Map> algorithmProperties,
                                        Map<String, String> mediaProperties) {
        return mediaProperties.containsKey(propertyName) ? mediaProperties.get(propertyName) : calculateValue(propertyName, actionProperties, jobProperties, actionDefinition.getAlgorithmRef(), algorithmProperties);
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
     * @return value of the property name after checking for that property within the prioritized categories of property containers or null if not found
     */
    public static String calculateValue(String propertyName, Map<String,String> actionProperties, Map<String, String> jobProperties,
                                        TransientAction transientAction,
                                        Map<String,Map> algorithmProperties,
                                        Map<String, String> mediaProperties) {
        return mediaProperties.containsKey(propertyName) ? mediaProperties.get(propertyName) : calculateValue(propertyName, actionProperties, jobProperties, transientAction.getAlgorithm(), algorithmProperties);
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
     * @return value of the property name after checking for that property within the prioritized categories of property containers or null if not found
     */
    public static String calculateValue(String propertyName, Collection<PropertyDefinitionRef> actionProperties, Map<String, String> jobProperties,
                                        ActionDefinition actionDefinition,
                                        Map<String,Map> algorithmProperties,
                                        Map<String, String> mediaProperties) {
        String value = null;
        if (mediaProperties.containsKey(propertyName)) {
            return mediaProperties.get(propertyName);
        } else if (actionDefinition.getAlgorithmRef() != null && algorithmProperties.containsKey(actionDefinition.getAlgorithmRef()) &&
                   algorithmProperties.get(actionDefinition.getAlgorithmRef()).containsKey(propertyName) ) {
            // if the algorithm properties includes the algorithm identified in the action definition, then get the property value from
            // the algorithm properties
            Map<String,String> m = algorithmProperties.get(actionDefinition.getAlgorithmRef());
            return m.get(propertyName);
        } else if (jobProperties.containsKey(propertyName)) {
            return jobProperties.get(propertyName);
        } else {
            for (PropertyDefinitionRef prop : actionProperties) {
                if (propertyName.equals(prop.getName())) {
                    return prop.getValue();
                }
            }
        }
        return null;
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
     * @return value of the property name after checking for that property within the prioritized categories of property containers or null if not found
     */
    public static String calculateValue(String propertyName, Collection<PropertyDefinitionRef> actionProperties,
                                        Map<String, String> jobProperties,
                                        TransientAction transientAction,
                                        Map<String,Map> algorithmProperties,
                                        Map<String, String> mediaProperties) {
        String value = null;
        if (mediaProperties.containsKey(propertyName)) {
            return mediaProperties.get(propertyName);
        } else if (transientAction.getAlgorithm() != null && algorithmProperties.containsKey(transientAction.getAlgorithm()) &&
                   algorithmProperties.get(transientAction.getAlgorithm()).containsKey(propertyName) ) {
            // if the algorithm properties includes the algorithm currently being run, then get the property value from
            // the algorithm properties
            Map<String,String> m = algorithmProperties.get(transientAction.getAlgorithm());
            return m.get(propertyName);
        } else if (jobProperties.containsKey(propertyName)) {
            return jobProperties.get(propertyName);
        } else {
            for (PropertyDefinitionRef prop : actionProperties) {
                if (propertyName.equals(prop.getName())) {
                    return prop.getValue();
                }
            }
        }
        return null;
    }


    public static Map<String, String> getCombinedJobProperties(
            AlgorithmDefinition algorithm,
            TransientAction action,
            TransientStreamingJob job) {

    	Map<String, String> overriddenJobProps = job.getOverriddenJobProperties();
        Map<String, String> overriddenAlgoProps = job.getOverriddenAlgorithmProperties().get(algorithm.getName());
        if (overriddenAlgoProps == null) {
            overriddenAlgoProps = Collections.emptyMap();
        }
        Map<String, String> mediaSpecificProps = job.getStream().getMediaProperties();
        return getCombinedJobProperties(algorithm, action, overriddenJobProps, overriddenAlgoProps, mediaSpecificProps);
    }


    public static Map<String, String> getCombinedJobProperties(
            AlgorithmDefinition algorithm,
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


    private static Map<String, String> getAlgorithmProperties(AlgorithmDefinition algorithm) {
        return algorithm
                .getProvidesCollection()
                .getAlgorithmProperties()
                .stream()
                .collect(toMap(PropertyDefinition::getName,
                               PropertyDefinition::getDefaultValue,
                               (x, y) -> y, HashMap::new));
    }
}
