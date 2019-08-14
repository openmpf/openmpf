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

import com.google.common.collect.ImmutableMap;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.transients.SystemPropertiesSnapshot;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.data.entities.transients.TransientStreamingJob;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    private final JobRequestDao _jobRequestDao;

    private final JsonUtils _jsonUtils;

    @Inject
    public AggregateJobPropertiesUtil(
            PropertiesUtil propertiesUtil,
            JobRequestDao jobRequestDao,
            JsonUtils jsonUtils) {
        _propertiesUtil = propertiesUtil;
        _jobRequestDao = jobRequestDao;
        _jsonUtils = jsonUtils;
    }


    private enum PropertyLevel { NONE, SYSTEM, ACTION, JOB, ALGORITHM, MEDIA }; // in order of precedence

    private static class PropertyInfo {
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
     * @param action Action currently being processed
     * @param media Media currently being processed
     * @param job Job currently being processed
     * @return property info after checking for that property within the prioritized categories of property containers
     */
    private static PropertyInfo calculateValue(String propertyName,
                                               Action action,
                                               Media media,
                                               BatchJob job) {

        Map<String, String> mediaProperties = media.getMediaSpecificProperties();
        if (mediaProperties.containsKey(propertyName)) {
            return new PropertyInfo(propertyName, mediaProperties.get(propertyName), PropertyLevel.MEDIA);
        }

        ImmutableMap<String, String> algoProps = job.getOverriddenAlgorithmProperties().get(action.getAlgorithm());
        if (algoProps != null) {
            String algoPropVal = algoProps.get(propertyName);
            if (algoPropVal != null) {
                return new PropertyInfo(propertyName, algoPropVal, PropertyLevel.ALGORITHM);
            }

        }

        Map<String, String> jobProperties = job.getJobProperties();
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

        combined.putAll(job.getJobProperties());
        Map<String, String> algoProps = job.getOverriddenAlgorithmProperties().get(action.getAlgorithm());
        if (algoProps != null) {
            combined.putAll(algoProps);
        }
        combined.putAll(job.getStream().getMediaProperties());
        return combined;
    }




    public static String calculateFrameInterval(Action action, BatchJob job,
                                                Media media,
                                                int systemFrameInterval, int systemFrameRateCap, double mediaFPS) {

        PropertyInfo frameIntervalPropInfo = AggregateJobPropertiesUtil.calculateValue(
                MpfConstants.MEDIA_SAMPLING_INTERVAL_PROPERTY,
                action,
                media,
                job);

        PropertyInfo frameRateCapPropInfo = AggregateJobPropertiesUtil.calculateValue(
                MpfConstants.FRAME_RATE_CAP_PROPERTY,
                action,
                media,
                job);

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




    // Priority:
    // media props > overridden algorithm props > job props > action props > default algo props >
    // snapshot props > system props
    public String calculateValue(String propertyName, BatchJob job, Media media,
                                 Action action) {
        return calculateValue(
                propertyName,
                media.getMediaSpecificProperties(),
                action,
                job.getTransientPipeline(),
                job.getOverriddenAlgorithmProperties(),
                job.getJobProperties(),
                job.getSystemPropertiesSnapshot());
    }


    public Function<String, String> getCombinedProperties(
            Action action,
            TransientPipeline pipeline,
            Media media,
            Map<String, String> jobProperties,
            Map<String, ? extends Map<String, String>> overriddenAlgoProps,
            SystemPropertiesSnapshot propertiesSnapshot) {
        return propertyName -> calculateValue(
                propertyName,
                media.getMediaSpecificProperties(),
                action,
                pipeline,
                overriddenAlgoProps,
                jobProperties,
                propertiesSnapshot);
    }


    private String calculateValue(
            String propertyName,
            Map<String, String> mediaProperties,
            Action action,
            TransientPipeline transientPipeline,
            Map<String, ? extends Map<String, String>> overriddenAlgorithmProperties,
            Map<String, String> jobProperties,
            SystemPropertiesSnapshot systemPropertiesSnapshot) {
        String mediaPropVal = mediaProperties.get(propertyName);
        if (mediaPropVal != null) {
            return mediaPropVal;
        }

        if (action != null) {
            Map<String, String> algoProperties = overriddenAlgorithmProperties.get(action.getAlgorithm());
            if (algoProperties != null) {
                String propVal = algoProperties.get(propertyName);
                if (propVal != null) {
                    return propVal;
                }
            }
        }

        String jobPropVal = jobProperties.get(propertyName);
        if (jobPropVal != null) {
            return jobPropVal;
        }

        if (action == null) {
            return null;
        }

        String actionPropVal = action.getPropertyValue(propertyName);
        if (actionPropVal != null) {
            return actionPropVal;
        }

        Algorithm algorithm = transientPipeline.getAlgorithm(action.getAlgorithm());

        Algorithm.Property property = algorithm.getProperty(propertyName);
        if (property != null) {
            if (property.getDefaultValue() != null) {
                return property.getDefaultValue();
            }
            String snapshotValue = systemPropertiesSnapshot.lookup(property.getPropertiesKey());
            if (snapshotValue != null) {
                return snapshotValue;
            }
            return _propertiesUtil.lookup(property.getPropertiesKey());
        }

        return null;
    }


    public Function<String, String> getCombinedProperties(BatchJob job, long mediaId, int taskIndex,
                                                          int actionIndex) {
        return getCombinedProperties(job, job.getMedia(mediaId),
                                     job.getTransientPipeline().getAction(taskIndex, actionIndex));

    }

    // Priority:
    // media props > overridden algorithm props > job props > action props > default algo props >
    // snapshot props > system props
    public Function<String, String> getCombinedProperties(BatchJob job, Media media,
                                                          Action action) {
        return propName -> calculateValue(propName, job, media, action);
    }



    public Function<String, String> getCombinedProperties(BatchJob job, Media media) {
        return propName -> calculateValue(
                propName,
                media.getMediaSpecificProperties(),
                null,
                job.getTransientPipeline(),
                job.getOverriddenAlgorithmProperties(),
                job.getJobProperties(),
                job.getSystemPropertiesSnapshot());
    }



    public Function<String, String> getCombinedProperties(BatchJob job, URI mediaUri) {
        var mediaProperties = Map.<String, String>of();
        for (Media media : job.getMedia()) {
            try {
                if (mediaUri.equals(new URI(media.getUri()))) {
                    mediaProperties = media.getMediaSpecificProperties();
                    break;
                }
            }
            catch (URISyntaxException ignored) {
                // Continue searching for matching media since a job could have a combination of good and bad media.
            }
        }
        final var finalMediaProps = mediaProperties;
        return propName -> calculateValue(
                propName,
                finalMediaProps,
                null,
                job.getTransientPipeline(),
                job.getOverriddenAlgorithmProperties(),
                job.getJobProperties(),
                job.getSystemPropertiesSnapshot());
    }




    public Function<String, String> getCombinedProperties(MarkupResult markup) {
        BatchJob transientJob = Optional.ofNullable(_jobRequestDao.findById(markup.getJobId()))
                .map(JobRequest::getInputObject)
                .map(bytes -> _jsonUtils.deserialize(bytes, BatchJob.class))
                .orElse(null);

        if (transientJob == null) {
            return x -> null;
        }

        Map<String, String> mediaProps = transientJob.getMedia()
                .stream()
                .filter(m -> URI.create(m.getUri()).equals(URI.create(markup.getSourceUri())))
                .findAny()
                .map(Media::getMediaSpecificProperties)
                .orElseGet(ImmutableMap::of);

        Action action = transientJob.getTransientPipeline().getAction(markup.getTaskIndex(), markup.getActionIndex());
        return propName -> calculateValue(
                propName,
                mediaProps,
                action,
                transientJob.getTransientPipeline(),
                transientJob.getOverriddenAlgorithmProperties(),
                transientJob.getJobProperties(),
                transientJob.getSystemPropertiesSnapshot());
    }
}
