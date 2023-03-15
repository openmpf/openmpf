/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.MediaActionProps;
import org.mitre.mpf.wfm.util.MediaRange;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@Service
public class JobConfigHasher {

    private static final Logger LOG = LoggerFactory.getLogger(JobConfigHasher.class);

    private final IgnorableProperties _ignorableProperties;

    private final String _outputVersion;

    @Inject
    public JobConfigHasher(
            ObjectMapper objectMapper,
            PropertiesUtil propertiesUtil) throws IOException {
        _outputVersion = getMajorMinorVersion(propertiesUtil.getOutputObjectVersion());

        var ignorablePropsResource = propertiesUtil.getTiesDbCheckIgnorablePropertiesResource();
        try (var inStream = ignorablePropsResource.getInputStream()) {
            var ignorablePropertyList = objectMapper.readValue(
                    inStream, new TypeReference<List<IgnorableProperty>>() { });
            _ignorableProperties = new IgnorableProperties(ignorablePropertyList);
        }
    }


    public String getJobConfigHash(
            Collection<Media> media,
            JobPipelineElements pipelineElements,
            MediaActionProps mediaActionProps) {

        var sortedMedia = media.stream()
                .filter(m -> !m.isDerivative())
                .sorted(Comparator.comparing(m -> m.getHash().orElseThrow()))
                .toList();

        var hasher = new Hasher();
        hasher.add(_outputVersion);
        for (var medium : sortedMedia) {
            hasher.add(medium.getHash().orElseThrow());
            hashMediaRanges(medium.getFrameRanges(), hasher);
            hashMediaRanges(medium.getTimeRanges(), hasher);
            var mediaType = medium.getType().orElseThrow();

            for (var task : pipelineElements.getTasksInOrder()) {
                for (var action : pipelineElements.getActionsInOrder(task)) {
                    var algorithm = pipelineElements.getAlgorithm(action.getAlgorithm());
                    hasher.add(algorithm.getName());
                    algorithm.getOutputVersion()
                            .ifPresentOrElse(
                                    ov -> hasher.add(String.valueOf(ov)),
                                    () -> hasher.add("none"));

                    mediaActionProps.get(medium, action)
                        .entrySet()
                        .stream()
                        .filter(e -> _ignorableProperties.isRequired(e.getKey(), mediaType, action))
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(hasher::add);
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


    private static void hashMediaRanges(Collection<MediaRange> mediaRanges, Hasher hasher) {
        if (mediaRanges.isEmpty()) {
            hasher.add("none");
            return;
        }
        mediaRanges.stream()
                .sorted()
                .forEach(r -> hasher.add(r.getStartInclusive() + " - " + r.getEndInclusive()));
    }


    private static class Hasher {
        private static final byte SEPARATOR = '/';

        private final MessageDigest _digest = DigestUtils.getSha256Digest();

        public void add(String data) {
            _digest.update(data.getBytes(StandardCharsets.UTF_8));
            _digest.update(SEPARATOR);
        }

        public void add(Map.Entry<String, String> mapEntry) {
            add(mapEntry.getKey());
            if (mapEntry.getValue() != null) {
                add(mapEntry.getValue());
            }
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

    public static class IgnorableProperties {

        private final ImmutableMap<String, IgnorableProperty> _properties;

        public IgnorableProperties(List<IgnorableProperty> ignorableProperties) {
            _properties = ignorableProperties.stream()
                .collect(ImmutableMap.toImmutableMap(
                            IgnorableProperty::name,
                            Function.identity()));
        }

        public boolean isRequired(String propertyName, MediaType mediaType, Action action) {
            var property = _properties.get(propertyName.toUpperCase());
            if (property == null) {
                return true;
            }
            if (property.requiredByMediaTypes.contains(mediaType)) {
                return true;
            }
            if (property.ignorableByAlgorithms.isEmpty()) {
                return false;
            }
            return !property.ignorableByAlgorithms.contains(action.getAlgorithm());
        }
    }

    private record IgnorableProperty(
            String name, Set<MediaType> requiredByMediaTypes, Set<String> ignorableByAlgorithms) {

        IgnorableProperty {
            name = name.toUpperCase();
            if (requiredByMediaTypes == null) {
                requiredByMediaTypes = EnumSet.noneOf(MediaType.class);
            }
            else {
                requiredByMediaTypes = EnumSet.copyOf(requiredByMediaTypes);
            }

            if (ignorableByAlgorithms == null) {
                ignorableByAlgorithms = ImmutableSet.of();
            }
            else {
                ignorableByAlgorithms = ignorableByAlgorithms.stream()
                    .map(String::toUpperCase)
                    .collect(ImmutableSet.toImmutableSet());
            }
        }
    }
}
