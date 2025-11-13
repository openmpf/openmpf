/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2025 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2025 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel.operations;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import org.mitre.mpf.wfm.data.entities.persistent.MediaSelector;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MediaSelectorsDuplicatePolicy;
import org.mitre.mpf.wfm.util.JobPart;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

public class MediaSelectorsMapper implements
        BiFunction<UUID, String, Optional<String>>,
        Iterable<MediaSelectorsMapper.Entry> {

    private final Map<UUID, Entry> _entryMap;

    public MediaSelectorsMapper(
            JobPart jobPart,
            Collection<Track> tracks,
            String delimeter,
            MediaSelectorsDuplicatePolicy duplicatePolicy) {

        var idToSelector = Maps.uniqueIndex(
                jobPart.media().getMediaSelectors(), MediaSelector::id);
        var selectorIdAndInputToOutputs = createOutputTable(idToSelector, tracks);

        _entryMap = jobPart.media().getMediaSelectors()
                .stream()
                .filter(ms -> selectorIdAndInputToOutputs.containsRow(ms.id()))
                .map(getEntryFactory(selectorIdAndInputToOutputs, duplicatePolicy, delimeter))
                .collect(ImmutableMap.toImmutableMap(e -> e.selector().id(), Function.identity()));
    }


    @Override
    public Iterator<Entry> iterator() {
        return _entryMap.values().iterator();
    }

    @Override
    public Optional<String> apply(UUID id, String input) {
        return Optional.ofNullable(_entryMap.get(id))
                .flatMap(e -> e.apply(input));
    }

    public Entry get(UUID uuid) {
        return _entryMap.get(uuid);
    }


    private static Table<UUID, String, Set<String>> createOutputTable(
            Map<UUID, MediaSelector> idToSelector,
            Collection<Track> tracks) {
        var resultBuilders = HashBasedTable.<UUID, String, ImmutableSet.Builder<String>>create();
        for (var track : tracks) {
            var selectorId = track.getSelectorId().orElse(null);
            var selectedInput = track.getSelectedInput()
                    .filter(s -> !s.isBlank())
                    .orElse(null);
            if (selectorId == null || selectedInput == null) {
                continue;
            }
            var outputBuilder = resultBuilders.get(selectorId, selectedInput);
            if (outputBuilder == null) {
                outputBuilder = ImmutableSet.builder();
                resultBuilders.put(selectorId, selectedInput, outputBuilder);
            }
            var selector = idToSelector.get(selectorId);
            getOutputs(track, selector).forEach(outputBuilder::add);
        }

        return resultBuilders.cellSet()
            .stream()
            .collect(ImmutableTable.toImmutableTable(
                    Table.Cell::getRowKey,
                    Table.Cell::getColumnKey,
                    c -> c.getValue().build()));
    }


    private static Stream<String> getOutputs(Track track, MediaSelector selector) {
        var resultProp = selector.resultDetectionProperty();
        var trackProp = track.getTrackProperties().get(resultProp);
        if (trackProp != null) {
            return Stream.of(trackProp);
        }

        var exemplarProp = Optional.ofNullable(track.getExemplar())
                .map(d -> d.getDetectionProperties().get(resultProp));
        if (exemplarProp.isPresent()) {
            return exemplarProp.stream();
        }

        return track.getDetections().stream()
            .map(d -> d.getDetectionProperties().get(resultProp))
            .filter(Objects::nonNull);
    }


    public static class Entry implements Function<String, Optional<String>> {
        private final MediaSelector _selector;

        private final Map<String, Set<String>> _outputMap;

        private final MediaSelectorsDuplicatePolicy _duplicatePolicy;


        private Entry(
                MediaSelector selector,
                Map<String, Set<String>> outputMap,
                MediaSelectorsDuplicatePolicy duplicatePolicy) {
            _selector = selector;
            _outputMap = outputMap;
            _duplicatePolicy = duplicatePolicy;
        }

        @Override
        public Optional<String> apply(String input) {
            return Optional.ofNullable(_outputMap.get(input))
                .flatMap(_duplicatePolicy);
        }

        public MediaSelector selector() {
            return _selector;
        }
    }


    private static class EntryWithDelimeter extends Entry {

        private final String _delimeter;

        public EntryWithDelimeter(
                MediaSelector selector,
                Map<String, Set<String>> outputMap,
                MediaSelectorsDuplicatePolicy duplicatePolicy,
                String delimeter) {
            super(selector, outputMap, duplicatePolicy);
            _delimeter = delimeter;
        }

        @Override
        public Optional<String> apply(String input) {
            return super.apply(input)
                .map(out -> "%s %s %s".formatted(input, _delimeter, out));
        }
    }


    private static Function<MediaSelector, Entry> getEntryFactory(
            Table<UUID, String, Set<String>> selectorIdAndInputToOutputs,
            MediaSelectorsDuplicatePolicy duplicatePolicy,
            String delimeter) {
        if (delimeter == null || delimeter.isEmpty()) {
            return ms -> new Entry(ms, selectorIdAndInputToOutputs.row(ms.id()), duplicatePolicy);
        }
        else {
            return ms -> new EntryWithDelimeter(
                    ms, selectorIdAndInputToOutputs.row(ms.id()), duplicatePolicy, delimeter);
        }
    }
}
