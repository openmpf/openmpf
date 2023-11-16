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
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.TrackCache;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
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
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.UncheckedExecutionException;


@Component(RollUpProcessor.REF)
@Singleton
public class RollUpProcessor extends WfmProcessor {

    public static final String REF = "RollUpProcessor";

    private static final Logger LOG = LoggerFactory.getLogger(RollUpProcessor.class);

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
                var rollUpFile = _aggregateJobPropertiesUtil.getValue(
                        MpfConstants.ROLL_UP_FILE, jobPart);
                if (rollUpFile == null || rollUpFile.isBlank()) {
                    continue;
                }
                var tracks = trackCache.getTracks(jobPart.media().getId(), jobPart.actionIndex());
                var rolledUpTracks = getRollUpContext(rollUpFile).applyRollUp(tracks);
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

    private RollUpContext getRollUpContext(String rollUpFile) {
        try {
            return _cache.getUnchecked(rollUpFile);
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
            var rolledUpTracks = ImmutableSortedSet.<Track>naturalOrder();
            for (var track : tracks) {
                var rolledUpTrack = applyRollUp(track);
                anyTracksChanged = anyTracksChanged || rolledUpTrack != track;
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
                    track.getExemplarPolicy());
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
            var newDetectionProperties = ImmutableSortedMap.<String, String>naturalOrder();
            boolean anyPropertyChanged = false;
            for (var existingEntry : existingProperties.entrySet()) {

                for (var copyDest : _copySourceToDest.get(existingEntry.getKey())) {
                    newDetectionProperties.put(copyDest, existingEntry.getValue());
                    anyPropertyChanged = true;
                }

                var rollUpName = _propAndMemberToRollUp.get(
                        existingEntry.getKey(), existingEntry.getValue());
                if (rollUpName == null) {
                    newDetectionProperties.put(existingEntry.getKey(), existingEntry.getValue());
                }
                else {
                    newDetectionProperties.put(existingEntry.getKey(), rollUpName);
                    anyPropertyChanged = true;
                }
            }
            return anyPropertyChanged ? newDetectionProperties.build() : existingProperties;
        }
    }
}
