package org.mitre.mpf.wfm.camel;

import static java.util.stream.Collectors.toCollection;

import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;

import org.mitre.mpf.interop.JsonActionOutputObject;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionType;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.transients.Track;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.segmenting.TriggerProcessor;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.springframework.stereotype.Component;


@Component
public class TrackOutputHelper {

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final InProgressBatchJobsService _inProgressBatchJobsService;

    private final TriggerProcessor _triggerProcessor;

    @Inject
    TrackOutputHelper(
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            InProgressBatchJobsService inProgressBatchJobsService,
            TriggerProcessor triggerProcessor) {
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _inProgressBatchJobsService = inProgressBatchJobsService;
        _triggerProcessor = triggerProcessor;
    }


    public record TrackInfo(
            int trackCount,
            String trackType,
            boolean isSuppressed,
            boolean isMergeSource,
            boolean isMergeTarget,
            SortedSet<Track> tracks) {
    }


    public TrackInfo getTrackInfo(BatchJob job, Media media, int taskIdx, int actionIdx) {
        boolean isSuppressed = isSuppressed(job, media, taskIdx);
        boolean isMergeTarget = isMergeTarget(job, media, taskIdx);
        boolean needToCheckTrackTriggers
                = isSuppressed && needToCheckSuppressedTriggers(job, media, taskIdx);
        boolean needToGetTracks = !isMergeTarget && (!isSuppressed || needToCheckTrackTriggers);

        SortedSet<Track> tracks;
        int trackCount;
        var trackType = JsonActionOutputObject.NO_TRACKS_TYPE;
        if (needToGetTracks) {
            tracks = _inProgressBatchJobsService.getTracks(
                    job.getId(), media.getId(), taskIdx, actionIdx);
            trackCount = tracks.size();
            if (trackCount > 0) {
                trackType = tracks.iterator().next().getType();
                if (needToCheckTrackTriggers) {
                    tracks = findUntriggered(job, media, taskIdx, tracks);
                    if (!tracks.isEmpty()) {
                        // Since there are un-triggered tracks, this task is the last task that
                        // processed those tracks.
                        isSuppressed = false;
                    }
                }
            }
        }
        else {
            tracks = null;
            trackCount = _inProgressBatchJobsService.getTrackCount(
                    job.getId(), media.getId(), taskIdx, actionIdx);
            if (trackCount > 0) {
                trackType = _inProgressBatchJobsService.getTrackType(
                        job.getId(), media.getId(), taskIdx, actionIdx)
                    .orElse(JsonActionOutputObject.NO_TRACKS_TYPE);
            }
        }

        boolean isMergeSource = isMergeSource(job, media, taskIdx, actionIdx);
        return new TrackInfo(
                trackCount, trackType, isSuppressed, isMergeSource, isMergeTarget, tracks);
    }


    private boolean isMergeSource(BatchJob job, Media media, int taskIdx, int actionIdx) {
        var action = job.getPipelineElements().getAction(taskIdx, actionIdx);
        return isMergeSource(job, media, action);
    }


    private boolean isMergeSource(BatchJob job, Media media, Action action) {
        var propValue = _aggregateJobPropertiesUtil.getValue(
            MpfConstants.OUTPUT_MERGE_WITH_PREVIOUS_TASK_PROPERTY,
            job, media, action);
        return Boolean.parseBoolean(propValue);
    }


    private boolean isMergeTarget(BatchJob job, Media media, int taskIdx) {
        int lastDetectionTaskIdx = getLastDetectionTaskIdx(job);
        var pipelineElements = job.getPipelineElements();
        for (int futureTaskIdx = taskIdx + 1;
                futureTaskIdx <= lastDetectionTaskIdx;
                futureTaskIdx++) {
            var futureTask = pipelineElements.getTask(futureTaskIdx);
            boolean taskAppliesToMedia = false;
            for (var futureAction : pipelineElements.getActionsInOrder(futureTask)) {
                if (_aggregateJobPropertiesUtil.actionAppliesToMedia(job, media, futureAction)) {
                    taskAppliesToMedia = true;
                    if (isMergeSource(job, media, futureAction)) {
                        return true;
                    }
                }
            }
            if (taskAppliesToMedia) {
                // We only want to check the next task that applies to the media. If task merging
                // is enabled for the pipeline, the merge chain would end with the next task or
                // some later task.
                return false;
            }
        }
        return false;
    }


    private boolean isSuppressed(BatchJob job, Media media, int taskIdx) {
        return _aggregateJobPropertiesUtil.isOutputLastTaskOnly(media, job)
                && taskIdx < getLastDetectionTaskIdx(job);
    }


    private boolean needToCheckSuppressedTriggers(BatchJob job, Media media, int taskIdx) {
        if (!_aggregateJobPropertiesUtil.isOutputLastTaskOnly(media, job)) {
            return false;
        }

        var pipelineElements = job.getPipelineElements();
        int lastDetectionTaskIdx = getLastDetectionTaskIdx(job);
        if (taskIdx == lastDetectionTaskIdx) {
            return false;
        }

        for (var futureTaskIdx = taskIdx + 1;
                futureTaskIdx <= lastDetectionTaskIdx;
                futureTaskIdx++) {

            var futureTask = pipelineElements.getTask(futureTaskIdx);
            for (var action : pipelineElements.getActionsInOrder(futureTask)) {
                var trigger = _aggregateJobPropertiesUtil.getValue(
                        MpfConstants.TRIGGER, job, media, action);
                if (trigger == null || trigger.isBlank()) {
                    // A task later in the pipeline does not have a trigger set. When a trigger
                    // is not set all tracks are passed as input to the task. That means that all
                    // of the tracks created in current task were input to a task.
                    return false;
                }
            }
        }
        return true;
    }


    private SortedSet<Track> findUntriggered(
            BatchJob job, Media media, int taskIdx, SortedSet<Track> tracks) {
        int lastDetectionTaskIdx = getLastDetectionTaskIdx(job);
        if (taskIdx == lastDetectionTaskIdx) {
            return tracks;
        }
        var wasTriggeredFilter = _triggerProcessor.createWasTriggeredFilter(
                job, media, taskIdx, lastDetectionTaskIdx);
        return tracks.stream()
            .filter(wasTriggeredFilter.negate())
            .collect(toCollection(TreeSet::new));
    }


    private int getLastDetectionTaskIdx(BatchJob job) {
        var pipelineElements = job.getPipelineElements();
        int taskCount = pipelineElements.getTaskCount();
        var lastTaskAlgo = pipelineElements.getAlgorithm(taskCount - 1, 0);
        if (lastTaskAlgo.getActionType() == ActionType.DETECTION) {
            return taskCount - 1;
        }
        else {
            return taskCount - 2;
        }
    }
}
