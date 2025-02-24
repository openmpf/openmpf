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

package org.mitre.mpf.wfm.camel.operations.detection;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.camel.Exchange;
import org.mitre.mpf.nms.util.EnvironmentVariableExpander;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JobPart;
import org.mitre.mpf.wfm.util.JobPartsIter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.UncheckedExecutionException;


@Component(RollUpProcessor.REF)
@Singleton
public class RollUpProcessor extends WfmProcessor {

    public static final String REF = "rollUpProcessor";

    private static final Logger LOG = LoggerFactory.getLogger(RollUpProcessor.class);

    // Cache roll up files because a user is likely to use the same roll up file for many jobs.
    private final LoadingCache<String, RollUpContext> _cache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build(loadRollUp());

    private final InProgressBatchJobsService _inProgressJobs;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final ObjectMapper _objectMapper;

    @Inject
    RollUpProcessor(
            InProgressBatchJobsService inProgressJobs,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            ObjectMapper objectMapper) {
        _objectMapper = objectMapper;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _inProgressJobs = inProgressJobs;
    }


    @Override
    public void wfmProcess(Exchange exchange) {
        exchange.getOut().setBody(exchange.getIn().getBody());
        var trackCache = exchange.getIn().getBody(TrackCache.class);
        var job = _inProgressJobs.getJob(trackCache.getJobId());
        for (var jobPart : JobPartsIter.task(job, trackCache.getTaskIndex())) {
            try {
                var rollUpContext = getRollUpContext(jobPart);
                if (rollUpContext.isEmpty()) {
                    // No roll up is configured for this part of the job.
                    continue;
                }

                var tracks = trackCache.getTracks(jobPart.media().getId(), jobPart.actionIndex());
                var rolledUpTracks = rollUpContext.get().applyRollUp(tracks);
                // When no tracks need to be changed, applyRollUp returns a reference to the same
                // collection that was passed in. Using .equals() below would be much slower than
                // the != operator.
                if (tracks != rolledUpTracks) {
                    trackCache.updateTracks(
                            jobPart.media().getId(), jobPart.actionIndex(), rolledUpTracks);
                }
            }
            catch (WfmProcessingException e) {
                LOG.error("Failed to apply roll up due to: " + e, e);
                _inProgressJobs.addError(
                    job.getId(), jobPart.media().getId(), IssueCodes.OTHER,
                    "Failed to apply roll up due to: " + e);
            }
        }
    }


    private Optional<RollUpContext> getRollUpContext(JobPart jobPart) {
        var unexpandedRollUpFile = _aggregateJobPropertiesUtil.getValue(
                MpfConstants.ROLL_UP_FILE, jobPart);
        if (unexpandedRollUpFile == null || unexpandedRollUpFile.isBlank()) {
            return Optional.empty();
        }
        var rollUpFile = EnvironmentVariableExpander.expand(unexpandedRollUpFile);
        try {
            return Optional.of(_cache.getUnchecked(rollUpFile));
        }
        catch (UncheckedExecutionException e) {
            Throwables.throwIfUnchecked(e.getCause());
            throw e;
        }
    }


    private CacheLoader<String, RollUpContext> loadRollUp() {
        return new CacheLoader<>() {
            public RollUpContext load(String key) {
                try {
                    LOG.info("Loading roll up file: {}", key);
                    var json = _objectMapper.readValue(
                            new File(key), new TypeReference<List<JsonRollUpConfig>>(){});
                    return new RollUpContext(json);
                }
                catch (IOException e) {
                    throw new WfmProcessingException(
                            "Could not load roll up file due to: " + e, e);
                }
            }
        };
    }

    private record JsonRollUpConfig(
        String propertyToProcess, Optional<String> originalPropertyCopy,
        List<JsonRollUpGroup> groups) {}

    private record JsonRollUpGroup(String rollUp, List<String> members) {}


    private static class RollUpContext {
        private final Table<String, String, String> _propAndMemberToRollUp
                = HashBasedTable.create();

        private final Multimap<String, String> _copySourceToDest
                = MultimapBuilder.hashKeys().hashSetValues().build();


        public RollUpContext(List<JsonRollUpConfig> rollUpConfigs) {
            for (var rollUpConfig : rollUpConfigs) {
                var propertyToProcess = rollUpConfig.propertyToProcess;
                rollUpConfig.originalPropertyCopy
                        .ifPresent(ocp -> _copySourceToDest.put(propertyToProcess, ocp));

                for (var group : rollUpConfig.groups) {
                    var rollUpName = group.rollUp;
                    for (var member : group.members) {
                        var existingRollUpName
                                = _propAndMemberToRollUp.put(propertyToProcess, member, rollUpName);
                        if (existingRollUpName != null && !rollUpName.equals(existingRollUpName)) {
                            throw new WfmProcessingException(
                                    "The roll up file is invalid because \"%s\" is mapped to both \"%s\" and \"%s\"."
                                    .formatted(member, rollUpName, existingRollUpName));
                        }
                    }
                }
            }
            validate();
        }

        private void validate() {
            var copyDestToSource = new HashMap<String, String>();
            for (var entry : _copySourceToDest.entries()) {
                var copySource = entry.getKey();
                var copyDest = entry.getValue();
                if (_propAndMemberToRollUp.containsRow(copyDest)) {
                    throw new WfmProcessingException(
                        "The roll up file is invalid because \"%s\" appears in both propertyToProcess and originalPropertyCopy."
                        .formatted(copyDest));
                }
                var prevSource = copyDestToSource.put(copyDest, copySource);
                if (prevSource != null && !prevSource.equals(copySource)) {
                    throw new WfmProcessingException(
                        "The roll up file is invalid because both \"%s\" and \"%s\" have originalPropertyCopy set to \"%s\"."
                        .formatted(copySource, prevSource, copyDest));
                }
            }
        }

        public SortedSet<Track> applyRollUp(SortedSet<Track> tracks) {
            var anyTracksChanged = false;
            var rolledUpTracks= ImmutableSortedSet.<Track>naturalOrder();
            for (var track : tracks) {
                var rolledUpTrack = applyRollUp(track);
                anyTracksChanged = anyTracksChanged || rolledUpTrack != track;
                // The track is added to rolledUpTracks whether it was changed or not because
                // another track in the collection may be changed.
                rolledUpTracks.add(rolledUpTrack);
            }
            return anyTracksChanged
                    // At least one track was changed, so return the new collection.
                    ? rolledUpTracks.build()
                    // No tracks were changed. tracks and rolledUpTracks contain the same tracks,
                    // but returning the existing collection signals that the tracks do not need to
                    // be sent to Redis.
                    : tracks;
        }

        private Track applyRollUp(Track track) {
            var rolledUpDetections = applyRollUpToDetections(track.getDetections());
            var rolledUpTrackProperties = applyRollUp(track.getTrackProperties());
            if (rolledUpDetections == track.getDetections()
                    && rolledUpTrackProperties == track.getTrackProperties()) {
                return track;
            }
            return new Track(
                    track.getJobId(),
                    track.getMediaId(),
                    track.getTaskIndex(),
                    track.getActionIndex(),
                    track.getStartOffsetFrameInclusive(),
                    track.getEndOffsetFrameInclusive(),
                    track.getStartOffsetTimeInclusive(),
                    track.getEndOffsetTimeInclusive(),
                    track.getMergedTaskIndex(),
                    track.getConfidence(),
                    rolledUpDetections,
                    rolledUpTrackProperties,
                    track.getExemplarPolicy(),
                    track.getQualitySelectionProperty(),
                    track.getSelectorId().orElse(null),
                    track.getSelectedInput().orElse(null));
        }

        private SortedSet<Detection> applyRollUpToDetections(
                SortedSet<Detection> existingDetections) {
            var newDetections = ImmutableSortedSet.<Detection>naturalOrder();
            var anyDetectionsChanged = false;
            for (var existingDetection : existingDetections) {
                var detectionWithRollUp = applyRollUp(existingDetection);
                anyDetectionsChanged
                        = anyDetectionsChanged || existingDetection != detectionWithRollUp;
                newDetections.add(detectionWithRollUp);
            }
            return anyDetectionsChanged ? newDetections.build() : existingDetections;
        }

        private Detection applyRollUp(Detection detection) {
            var rolledUpProperties = applyRollUp(detection.getDetectionProperties());
            if (rolledUpProperties == detection.getDetectionProperties()) {
                return detection;
            }
            return new Detection(
                    detection.getX(),
                    detection.getY(),
                    detection.getWidth(),
                    detection.getHeight(),
                    detection.getConfidence(),
                    detection.getMediaOffsetFrame(),
                    detection.getMediaOffsetTime(),
                    rolledUpProperties);
        }

        private Map<String, String> applyRollUp(Map<String, String> existingProperties) {
            var newProperties = ImmutableSortedMap.<String, String>naturalOrder();
            boolean anyPropertyCopied = copyProperties(existingProperties, newProperties);
            boolean anyPropertyRolledUp = rollUpProperties(existingProperties, newProperties);
            return anyPropertyCopied || anyPropertyRolledUp
                    ? newProperties.build()
                    : existingProperties;
        }

        boolean copyProperties(
                Map<String, String> properties, ImmutableMap.Builder<String, String> newProperties) {
            boolean anyPropertyChanged = false;
            for (var prop : properties.entrySet()) {
                for (var copyDest : _copySourceToDest.get(prop.getKey())) {
                    newProperties.put(copyDest, prop.getValue());
                    anyPropertyChanged = true;
                }
            }
            return anyPropertyChanged;
        }

        boolean rollUpProperties(
                    Map<String, String> properties,
                    ImmutableMap.Builder<String, String> newProperties) {
            boolean anyPropertyChanged = false;
            for (var prop : properties.entrySet()) {
                var rollUpName = _propAndMemberToRollUp.get(prop.getKey(), prop.getValue());
                if (rollUpName == null) {
                    newProperties.put(prop);
                }
                else {
                    newProperties.put(prop.getKey(), rollUpName);
                    anyPropertyChanged = true;
                }
            }
            return anyPropertyChanged;
        }
    }
}
