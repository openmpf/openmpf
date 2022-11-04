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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.util.MediaActionProps;
import org.mitre.mpf.wfm.util.MediaRange;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;

@Service
public class JobConfigHasher {

    private final ImportantProperties _importantProperties;

    @Inject
    public JobConfigHasher(
            ObjectMapper objectMapper,
            PropertiesUtil propertiesUtil) throws IOException {

        var importantPropsResource = propertiesUtil.getImportantPropertiesResource();
        try (var inStream = importantPropsResource.getInputStream()) {
            _importantProperties = objectMapper.readValue(inStream, ImportantProperties.class);
        }
    }


    public String getJobConfigHash(
            Collection<Media> media,
            JobPipelineElements pipelineElements,
            MediaActionProps props) {

        var sortedMedia = media.stream()
                .sorted(Comparator.comparing(m -> m.getHash().orElseThrow()))
                .toList();

        var hasher = new Hasher();
        for (var medium : sortedMedia) {
            hasher.add(medium.getHash().orElseThrow());
            hashMediaRanges(medium.getFrameRanges(), hasher);
            hashMediaRanges(medium.getTimeRanges(), hasher);

            for (var task : pipelineElements.getTasksInOrder()) {
                for (var action : pipelineElements.getActionsInOrder(task)) {
                    var algorithm = pipelineElements.getAlgorithm(action.getAlgorithm());
                    hasher.add(algorithm.getName());
                    algorithm.getOutputVersion()
                            .ifPresentOrElse(
                                    ov -> hasher.add(String.valueOf(ov)),
                                    () -> hasher.add("none"));

                    var importantProperties = _importantProperties.get(
                            medium.getType().orElseThrow(), algorithm.getName());
                    for (var propName : importantProperties) {
                        hasher.add(propName);
                        var propVal = props.get(propName, medium, action);
                        if (propVal != null) {
                            hasher.add(propVal);
                        }
                    }
                }
                // Add separator so actions in the same task get a different value from actions in
                // different tasks.
                hasher.add("$");
            }
        }
        return hasher.getHash();
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

        public String getHash() {
            return Hex.encodeHexString(_digest.digest());
        }
    }


    public static class ImportantProperties {
        private final ImmutableSet<String> _all;
        private final ImmutableMultimap<String, String> _mediaType;
        private final ImmutableMultimap<String, String> _algorithm;


        public ImportantProperties(
                @JsonProperty("all") List<String> all,
                @JsonProperty("mediaType") Map<String, Set<String>> mediaType,
                @JsonProperty("algorithm") Map<String, Set<String>> algorithm) {
            _all = toUpperSet(all);
            _mediaType = toUpperMultimap(mediaType);
            _algorithm = toUpperMultimap(algorithm);
        }


        public SortedSet<String> get(MediaType mediaType, String algorithmName) {
            var props = new TreeSet<>(_all);
            props.addAll(_mediaType.get(mediaType.toString()));
            props.addAll(_algorithm.get(algorithmName.toUpperCase()));
            return props;
        }

        private static ImmutableMultimap<String, String> toUpperMultimap(
                Map<String, Set<String>> map) {
            return map.entrySet().stream()
                    .collect(ImmutableSetMultimap.flatteningToImmutableSetMultimap(
                            e -> e.getKey().toUpperCase(),
                            e -> e.getValue().stream().map(String::toUpperCase)));
        }

        private static ImmutableSet<String> toUpperSet(Collection<String> set) {
            return set.stream()
                    .map(String::toUpperCase)
                    .collect(ImmutableSet.toImmutableSet());
        }
    }
}
