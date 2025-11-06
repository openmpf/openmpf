/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.mitre.mpf.rest.api.pipelines.Algorithm;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaSelector;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.MediaRange;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;

@Service
public class JobConfigHasher {

    private static final Logger LOG = LoggerFactory.getLogger(JobConfigHasher.class);

    private final PropertiesUtil _propertiesUtil;

    private final WorkflowPropertyService _workflowPropertyService;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final IgnorableProperties _ignorableProperties;

    private final String _outputVersion;

    @Inject
    public JobConfigHasher(
            PropertiesUtil propertiesUtil,
            WorkflowPropertyService workflowPropertyService,
            ObjectMapper objectMapper,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil) throws IOException {
        _propertiesUtil = propertiesUtil;
        _workflowPropertyService = workflowPropertyService;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _outputVersion = getMajorMinorVersion(propertiesUtil.getOutputObjectVersion());

        var ignorablePropsResource = propertiesUtil.getTiesDbCheckIgnorablePropertiesResource();
        try (var inStream = ignorablePropsResource.getInputStream()) {
            var ignorablePropertyGroups = objectMapper.readValue(
                    inStream, new TypeReference<List<IgnorablePropertyGroup>>() { });
            _ignorableProperties = new IgnorableProperties(ignorablePropertyGroups);
        }
    }


    public String getJobConfigHash(BatchJob job) {
        var sortedMedia = job.getMedia().stream()
                .filter(m -> !m.isDerivative())
                .sorted(Comparator.comparing(m -> m.getLinkedHash().orElseThrow()))
                .toList();
        var pipelineElements = job.getPipelineElements();

        var hasher = new Hasher();
        hasher.add(_outputVersion);
        hasher.add(_propertiesUtil.getOutputChangedCounter());
        for (var medium : sortedMedia) {
            // When calculating the hash we include all of the media, so in order to find a
            // matching job, the job will have to include all of the same media.
            // Most users who use TiesDb submit jobs with one piece of media, so it isn't worth it
            // to try to find jobs with some of the media in common.
            hasher.add(medium.getLinkedHash().orElseThrow());
            hashMediaRanges(medium.getFrameRanges(), hasher);
            hashMediaRanges(medium.getTimeRanges(), hasher);
            hashMediaSelectors(medium, pipelineElements, hasher);
            var mediaType = medium.getType().orElseThrow();

            for (var task : pipelineElements.getTasksInOrder()) {
                for (var action : pipelineElements.getActionsInOrder(task)) {
                    var algorithm = pipelineElements.getAlgorithm(action.algorithm());
                    hasher.add(algorithm.name());
                    algorithm.outputChangedCounter()
                            .ifPresentOrElse(
                                    ov -> hasher.add(String.valueOf(ov)),
                                    () -> hasher.add("none"));
                    var props = _aggregateJobPropertiesUtil.getPropertyMap(job, medium, action);
                    hashProperties(props, algorithm, mediaType, hasher);

                }
                // Add separator so actions in the same task get a different value from actions in
                // different tasks.
                hasher.add("$");
            }
        }
        var hash = hasher.getHash();
        LOG.info("The job config hash is: {}", hash);
        return hash;
    }


    private void hashProperties(
            Map<String, String> properties,
            Algorithm algorithm,
            MediaType mediaType,
            Hasher hasher) {
        properties.entrySet()
                .stream()
                .filter(createIsRequiredFilter(algorithm, mediaType))
                .sorted(Map.Entry.comparingByKey())
                .forEach(hasher::add);
    }


    private Predicate<Map.Entry<String, String>> createIsRequiredFilter(
            Algorithm algorithm, MediaType mediaType) {
        var algoPropertySet = algorithm.providesCollection()
            .properties()
            .stream()
            .map(ap -> ap.name())
            .collect(toSet());

        return entry -> {
            var propName = entry.getKey();
            if (_ignorableProperties.canIgnore(propName, mediaType)) {
                return false;
            }
            var isWorkflowProp= null != _workflowPropertyService.getProperty(propName, mediaType);
            if (isWorkflowProp) {
                return true;
            }
            return algoPropertySet.contains(propName);
        };
    }


    private static void hashMediaRanges(Collection<MediaRange> mediaRanges, Hasher hasher) {
        if (mediaRanges.isEmpty()) {
            hasher.add("none");
            return;
        }
        mediaRanges.stream()
                .sorted()
                .forEach(r -> hasher.add(r.getStartInclusive() + " - " + r.getEndInclusive()));
    }


    private void hashMediaSelectors(
            Media media,
            JobPipelineElements pipelineElements,
            Hasher hasher) {
        if (media.getMediaSelectors().isEmpty()) {
            hasher.add("none");
            return;
        }

        var algo = pipelineElements.getAlgorithm(0, 0);
        var mediaType = media.getType().orElseThrow();

        media.getMediaSelectors()
            .stream()
            .sorted(MediaSelector.comparator())
            .forEach(selector -> {
                hasher.add(selector.expression())
                    .add(selector.type().toString())
                    .add(selector.resultDetectionProperty());
                hashProperties(selector.selectionProperties(), algo, mediaType, hasher);
            });
    }


    private static class Hasher {
        private static final byte SEPARATOR = '/';

        private final MessageDigest _digest = DigestUtils.getSha256Digest();

        public Hasher add(String data) {
            _digest.update(data.getBytes(StandardCharsets.UTF_8));
            _digest.update(SEPARATOR);
            return this;
        }

        public Hasher add(Map.Entry<String, String> mapEntry) {
            add(mapEntry.getKey());
            if (mapEntry.getValue() != null) {
                add(mapEntry.getValue());
            }
            return this;
        }

        public String getHash() {
            return Hex.encodeHexString(_digest.digest());
        }
    }


    private static String getMajorMinorVersion(String version) {
        var parts = version.split("\\.", 3);
        var result = parts[0];
        if (parts.length > 1) {
            result += parts[1];
        }
        return result;
    }

    private record IgnorablePropertyGroup(
            List<MediaType> ignorableForMediaTypes, List<String> names) {
    }

    public static class IgnorableProperties {

        private Multimap<MediaType, String> _properties;

        public IgnorableProperties(List<IgnorablePropertyGroup> groups) {
            var builder = ImmutableSetMultimap.<MediaType, String>builder();
            for (var group : groups) {
                for (var name : group.names) {
                    var nameUpper = name.strip().toUpperCase();
                    for (var mediaType : group.ignorableForMediaTypes) {
                        builder.put(mediaType, nameUpper);
                    }
                }
            }
            _properties = builder.build();
        }

        public boolean canIgnore(String property, MediaType mediaType) {
            return _properties.containsEntry(mediaType, property.toUpperCase());
        }
    }

}
