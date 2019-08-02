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

package org.mitre.mpf.wfm.util;

import com.google.common.collect.Table;
import org.mitre.mpf.interop.JsonAction;
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.rest.api.JobCreationMediaData;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJob;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.pipeline.Action;
import org.mitre.mpf.wfm.pipeline.Algorithm;
import org.mitre.mpf.wfm.pipeline.PipelineService;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Function;

// Updated calculateValue methods to support new algorithmProperties.  Note the need to support actionProperties both as
// a Map<String,String> and as a Collection<PropertyDefinitionRef>.  Also, need to allow for passing in which algorithm is to be checked
// in algorithmProperties, where the currently processed (or defined) algorithm could originate from a
// TransientAction or an ActionDefinition.  Both of these Action objects would include the algorithm that is currently being processed,
// and should be used to set the scope for which algorithmProperty (key: AlgorithmName) is to be used to
// check for the propertyName being provided

@Component
public class AggregateJobPropertiesUtil {

    private final PropertiesUtil _propertiesUtil;

    private final PipelineService _pipelineService;

    private final HibernateJobRequestDao _jobRequestDao;

    private final JsonUtils _jsonUtils;

    @Inject
    public AggregateJobPropertiesUtil(
            PropertiesUtil propertiesUtil,
            PipelineService pipelineService,
            HibernateJobRequestDao jobRequestDao,
            JsonUtils jsonUtils) {
        _propertiesUtil = propertiesUtil;
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
    public static PropertyInfo calculateValue(String propertyName,
                                              Action action,
                                              TransientMedia media,
                                              TransientJob job) {

        Map<String, String> mediaProperties = media.getMediaSpecificProperties();
        if (mediaProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, mediaProperties.get(propertyName), PropertyLevel.MEDIA);
        }

        Table<String, String, String> algorithmProperties = job.getOverriddenAlgorithmProperties();
        String algoPropValue = algorithmProperties.get(action.getAlgorithm(), propertyName);
        if (algoPropValue != null) {
            return new PropertyInfo(propertyName, algoPropValue, PropertyLevel.ALGORITHM);
        }

        Map<String, String> jobProperties = job.getOverriddenJobProperties();
        if (jobProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, jobProperties.get(propertyName), PropertyLevel.JOB);
        }

        String actionPropertyValue = action.getPropertyValue(propertyName);
        if (actionPropertyValue != null) {
            return new PropertyInfo(propertyName, actionPropertyValue, PropertyLevel.ACTION);
        }

        return new PropertyInfo(propertyName, null, PropertyLevel.NONE);
    }



    public Map<String, String> getCombinedJobProperties(Action action,
                                                        TransientStreamingJob job) {
        var algoName = action.getAlgorithm();
        var algorithm = job.getTransientPipeline().getAlgorithm(algoName);

        var combined = new HashMap<String, String>();
        for (Algorithm.Property property : algorithm.getProvidesCollection().getProperties()) {
            String defaultValue = property.getDefaultValue();
            if (defaultValue != null) {
                combined.put(property.getName(), defaultValue);
            }
            else {
                combined.put(property.getName(), _propertiesUtil.lookup(property.getPropertiesKey()));
            }
        }

        for (Action.Property property : action.getProperties()) {
            combined.put(property.getName(), property.getValue());
        }

        combined.putAll(job.getOverriddenJobProperties());
        combined.putAll(job.getOverriddenAlgorithmProperties().row(action.getAlgorithm()));
        combined.putAll(job.getStream().getMediaProperties());
        return combined;
    }




    public static String calculateFrameInterval(Action action, TransientJob transientJob,
                                                TransientMedia transientMedia,
                                                int systemFrameInterval, int systemFrameRateCap, double mediaFPS) {

        PropertyInfo frameIntervalPropInfo = AggregateJobPropertiesUtil.calculateValue(
                MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY,
                action,
                transientMedia,
                transientJob);

        PropertyInfo frameRateCapPropInfo = AggregateJobPropertiesUtil.calculateValue(
                MpfConstants.FRAME_RATE_CAP_PROPERTY,
                action,
                transientMedia,
                transientJob);

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





    public String calculateValue(String propertyName, TransientJob job, TransientMedia media,
                                 Action action) {
        String mediaPropVal = media.getMediaSpecificProperty(propertyName);
        if (mediaPropVal != null) {
            return mediaPropVal;
        }

        TransientPipeline transientPipeline = job.getTransientPipeline();
        String algoName = action.getAlgorithm();
        String overriddenAlgoPropVal = job.getOverriddenAlgorithmProperties().get(algoName, propertyName);
        if (overriddenAlgoPropVal != null) {
            return overriddenAlgoPropVal;
        }

        String jobPropVal = job.getOverriddenJobProperties().get(propertyName);
        if (jobPropVal != null) {
            return jobPropVal;
        }

        for (Action.Property property : action.getProperties()) {
            if (property.getName().equalsIgnoreCase(propertyName)) {
                return property.getValue();
            }
        }

        Algorithm algorithm = transientPipeline.getAlgorithm(algoName);

        Algorithm.Property property = algorithm.getProperty(propertyName);
        if (property != null) {
            if (property.getDefaultValue() != null) {
                return property.getDefaultValue();
            }
            String snapshotValue = job.getSystemPropertiesSnapshot().lookup(property.getPropertiesKey());
            if (snapshotValue != null) {
                return snapshotValue;
            }
            return _propertiesUtil.lookup(property.getPropertiesKey());
        }

        return null;
    }


    public Function<String, String> getCombinedProperties(TransientJob job, long mediaId, int taskIndex,
                                                          int actionIndex) {
        return getCombinedProperties(job, job.getMedia(mediaId),
                                     job.getTransientPipeline().getAction(taskIndex, actionIndex));

    }

    // Priority:
    // media props > overridden algorithm props > job props > action props > default algo props >
    // snapshot props > system props

    public Function<String, String> getCombinedProperties(TransientJob job, TransientMedia media,
                                                          Action action) {
        return propName -> calculateValue(propName, job, media, action);
    }




    public static Function<String, String> getCombinedProperties(TransientJob job, TransientMedia media) {
        List<Map<String, String>> propertyMaps = Arrays.asList(
                media.getMediaSpecificProperties(),
                job.getOverriddenJobProperties());

        return propName -> getPropertyValue(propName, propertyMaps);
    }


    public static Function<String, String> getCombinedProperties(JsonJobRequest jobRequest, URI mediaUri) {
        Map<String, String> mediaProperties = Collections.emptyMap();
        for (JsonMediaInputObject media : jobRequest.getMedia()) {
            try {
                if (mediaUri.equals(new URI(media.getMediaUri()))) {
                    mediaProperties = media.getProperties();
                    break;
                }
            }
            catch (URISyntaxException ignored) {
                // Continue searching for matching media since a job could have a combination of good and bad media.
            }
        }

        List<Map<String, String>> propertyMaps = Arrays.asList(mediaProperties, jobRequest.getJobProperties());

        return propName -> getPropertyValue(propName, propertyMaps);
    }


    public Function<String, String> getCombinedProperties(
            JobCreationRequest jobCreationRequest,
            JobCreationMediaData media,
            Action action) {

        Map<String, String> overriddenAlgoProps
                = jobCreationRequest.getAlgorithmProperties().get(action.getAlgorithm());

        List<Map<String, String>>  propertyMaps;
        if (overriddenAlgoProps == null) {
            propertyMaps = Arrays.asList(media.getProperties(),
                                         jobCreationRequest.getJobProperties());
        }
        else {
            propertyMaps = Arrays.asList(media.getProperties(),
                                         overriddenAlgoProps,
                                         jobCreationRequest.getJobProperties());
        }
        return propName -> getPropertyValue(propName, propertyMaps, action.getName());
    }


    private static String getPropertyValue(String propName, Iterable<Map<String, String>> propertyMaps) {
        return getPropertyValue(propName, propertyMaps, x -> null);
    }


    private String getPropertyValue(String propName, Iterable<Map<String, String>> propertyMaps,
                                    String actionName) {
        return getPropertyValue(propName, propertyMaps, pName -> getDefaultActionProperty(pName, actionName));
    }


    private String getDefaultActionProperty(String propName, String actionName) {
        Action action = _pipelineService.getAction(actionName);
        if (action == null) {
            return null;
        }

        Action.Property actionProperty = action.getProperties()
                .stream()
                .filter(ap -> propName.equals(ap.getName()))
                .findAny()
                .orElse(null);
        if (actionProperty != null) {
            return actionProperty.getValue();
        }


        Algorithm.Property property = Optional.ofNullable(_pipelineService.getAlgorithm(action.getAlgorithm()))
                .map(a -> a.getProperty(propName))
                .orElse(null);
        if (property == null) {
            return null;
        }
        if (property.getDefaultValue() != null) {
            return property.getDefaultValue();
        }
        return _propertiesUtil.lookup(property.getPropertiesKey());
    }


    private static String getPropertyValue(String propName, Iterable<Map<String, String>> propertyMaps,
                                           Function<String, String> lowestPriorityLookup) {
        for (Map<String, String> propertyMap : propertyMaps) {
            if (propertyMap.containsKey(propName)) {
                return propertyMap.get(propName);
            }
        }
        return lowestPriorityLookup.apply(propName);
    }


    // Best effort attempt to recover as many job properties as possible after a job has already completed.
    // Since a component could have been un-registered since the job completed, some pipeline elements may not
    // be present. Any missing pipeline elements will be skipped.
    public Function<String, String> getCombinedPropertiesAfterJobCompletion(MarkupResult markup) {
        JsonJobRequest jsonJobRequest = Optional.ofNullable(_jobRequestDao.findById(markup.getJobId()))
                .map(JobRequest::getInputObject)
                .map(b -> _jsonUtils.deserialize(b, JsonJobRequest.class))
                .orElse(null);

        if (jsonJobRequest == null) {
           return propName -> null;
        }

        Map<String, String> mediaProperties = jsonJobRequest.getMedia()
                .stream()
                .filter(m -> URI.create(m.getMediaUri()).equals(URI.create(markup.getMarkupUri())))
                .map(JsonMediaInputObject::getProperties)
                .findAny()
                .orElseGet(Collections::emptyMap);

        return propName -> getPropertyValueAfterJobCompletion(propName, markup, mediaProperties, jsonJobRequest);
    }


    private String getPropertyValueAfterJobCompletion(
            String propName, MarkupResult markup, Map<String, String> mediaProperties, JsonJobRequest jobRequest)  {

        if (mediaProperties.containsKey(propName)) {
            return mediaProperties.get(propName);
        }

        JsonAction jsonAction = jobRequest.getPipeline()
                .getStages()
                .get(markup.getTaskIndex())
                .getActions()
                .get(markup.getActionIndex());

        // Check overridden algorithm properties
        if (jobRequest.getAlgorithmProperties().containsKey(jsonAction.getName())) {
            Map<String, String> overriddenAlgoProps = jobRequest.getAlgorithmProperties().get(jsonAction.getName());
            if (overriddenAlgoProps.containsKey(propName)) {
                return overriddenAlgoProps.get(propName);
            }
        }

        // Job Properties
        if (jobRequest.getJobProperties().containsKey(propName)) {
            return jobRequest.getJobProperties().get(propName);
        }

        // Overridden Action Properties
        if (jsonAction.getProperties().containsKey(propName)) {
            return jsonAction.getProperties().get(propName);
        }

        // Default Action Properties
        Action action = _pipelineService.getAction(jsonAction.getName());
        if (action != null) {
            String propVal = action.getPropertyValue(propName);
            if (propVal != null) {
                return propVal;
            }
        }


        // Default Algorithm Properties
        Algorithm algorithm = _pipelineService.getAlgorithm(jsonAction.getAlgorithm());
        if (algorithm != null) {
            Algorithm.Property property = algorithm.getProperty(propName);
            if (property != null) {
                if (property.getDefaultValue() != null) {
                    return property.getDefaultValue();
                }
                else {
                    _propertiesUtil.lookup(property.getPropertiesKey());
                }
            }
        }
        return null;
    }
}
