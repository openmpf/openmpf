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


package org.mitre.mpf.wfm.businessrules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.mitre.mpf.interop.JsonActionOutputObject;
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.mitre.mpf.interop.JsonMediaOutputObject;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.JsonTrackOutputObject;
import org.mitre.mpf.mvc.util.CloseableMdc;
import org.mitre.mpf.rest.api.subject.SubjectJobRequest;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.camel.routes.SubjectJobRouteBuilder;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.service.PastJobResultsService;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SubjectJobRequestService {
    private static final Logger LOG = LoggerFactory.getLogger(SubjectJobRequestService.class);

    private final SubjectJobRepo _subjectJobRepo;

    private final InProgressBatchJobsService _inProgressDetectionJobsService;

    private final PastJobResultsService _pastJobResultsService;

    private final JmsUtils _jmsUtils;

    private final ProducerTemplate _producerTemplate;

    private final TransactionTemplate _transactionTemplate;


    @Inject
    SubjectJobRequestService(
                SubjectJobRepo subjectJobRepo,
                InProgressBatchJobsService inProgressDetectionJobsService,
                PastJobResultsService pastJobResultsService,
                ProducerTemplate producerTemplate,
                TransactionTemplate transactionTemplate,
                JmsUtils jmsUtils) {
        _subjectJobRepo = subjectJobRepo;
        _inProgressDetectionJobsService = inProgressDetectionJobsService;
        _pastJobResultsService = pastJobResultsService;
        _producerTemplate = producerTemplate;
        _transactionTemplate = transactionTemplate;
        _jmsUtils = jmsUtils;
    }


    public long runJob(SubjectJobRequest request) {
        long jobId = addJobToDb(request).getId();
        try (var ctx = CloseableMdc.job(jobId)) {
            var futures = request.detectionJobIds().stream()
                .map(this::getProtobufDetectionResults)
                .toList();

            ThreadUtil.allOf(futures)
                .thenRun(() -> runJob(jobId, futures))
                .exceptionally(e -> addJobError(jobId, e));
        }
        return jobId;
    }


    public void cancel(long jobId) {
        var job = _subjectJobRepo.findById(jobId).orElseThrow();
        _jmsUtils.cancelSubjectJob(jobId, job.getComponentName());
    }


    private DbSubjectJob addJobToDb(SubjectJobRequest request) {
        var job = new DbSubjectJob(
                request.componentName(),
                request.priority(),
                request.detectionJobIds(),
                request.jobProperties());
        return _subjectJobRepo.save(job);
    }

    private Void addJobError(long jobId, Throwable error) {
        var cause = ThreadUtil.unwrap(error);
        LOG.error("An error occurred: ", cause);
        _transactionTemplate.executeWithoutResult(t -> {
            var job = _subjectJobRepo.findById(jobId).orElseThrow();
            job.addError("An error occurred while creating job: " + cause);
        });
        return null;
    }


    private void runJob(
            long subjectJobId,
            Collection<CompletableFuture<ProtobufDetectionResults>> protobufJobResultFutures) {
        var jobBuilder = SubjectProtobuf.SubjectTrackingJob.newBuilder()
                .setJobId(subjectJobId)
                .setJobName("Job " + subjectJobId);

        // Need to use TransactionTemplate instead of @Transactional because this method will run
        // on a different thread.
        _transactionTemplate.executeWithoutResult(t -> {
            var job = _subjectJobRepo.findById(subjectJobId).orElseThrow();
            job.setRetrievedDetectionJobs(true);
            jobBuilder.putAllJobProperties(job.getJobProperties());
        });

        for (var future : protobufJobResultFutures) {
            var jobResults = ThreadUtil.join(future);
            jobBuilder.addAllVideoJobResults(jobResults.video)
                .addAllImageJobResults(jobResults.image);
        }
        SubjectJobRouteBuilder.submit(jobBuilder.build(), _producerTemplate);
    }


    private CompletableFuture<ProtobufDetectionResults> getProtobufDetectionResults(
            long detectionJobId) {
        return _inProgressDetectionJobsService.getJobResultsAvailableFuture(detectionJobId)
            .thenApplyAsync(optResult -> {
                if (optResult.isPresent()) {
                    return optResult.get();
                }
                return _pastJobResultsService.getJobResults(detectionJobId);
            })
            .thenApply(this::toProtobuf);
    }

    private record ProtobufDetectionResults(
            Collection<SubjectProtobuf.VideoDetectionJobResults> video,
            Collection<SubjectProtobuf.ImageDetectionJobResults> image) {

    }

    private ProtobufDetectionResults toProtobuf(JsonOutputObject outputObject) {
        var videoJobResults = new ArrayList<SubjectProtobuf.VideoDetectionJobResults>();
        var imageJobResults = new ArrayList<SubjectProtobuf.ImageDetectionJobResults>();
        for (var media : outputObject.getMedia()) {
            var mediaType = MediaType.valueOf(media.getType());
            for (var entry : media.getTrackTypes().entrySet()) {
                var trackType = entry.getKey();
                var actions = entry.getValue();
                for (var action : actions) {
                    if (mediaType == MediaType.IMAGE) {
                        imageJobResults.add(createImageResults(
                                outputObject, media, trackType, action));
                    }
                    else if (mediaType == MediaType.VIDEO) {
                        videoJobResults.add(createVideoResults(
                                outputObject, media, trackType, action));
                    }
                }
            }
        }
        return new ProtobufDetectionResults(videoJobResults, imageJobResults);
    }


    private static SubjectProtobuf.ImageDetectionJobResults createImageResults(
            JsonOutputObject outputObject,
            JsonMediaOutputObject media,
            String trackType,
            JsonActionOutputObject action) {
        var imageJobResults = SubjectProtobuf.ImageDetectionJobResults.newBuilder();
        imageJobResults.setDetectionJob(createDetectionJob(outputObject, media, trackType, action.getAlgorithm()));
        for (var track : action.getTracks()) {
            var detection = track.getDetections().first();
            imageJobResults.putResults(track.getId(), convertToImageLocation(detection));
        }
        return imageJobResults.build();
    }

    private static SubjectProtobuf.DetectionJob createDetectionJob(
            JsonOutputObject outputObject,
            JsonMediaOutputObject media,
            String trackType,
            String algorithm) {
        return SubjectProtobuf.DetectionJob.newBuilder()
            .setDataUri(media.getPath())
            .setMediaId(media.getPath())
            .setAlgorithm(algorithm)
            .setTrackType(trackType)
            .putAllJobProperties(outputObject.getJobProperties())
            .putAllMediaProperties(media.getMediaProperties())
            .build();
    }


    private static DetectionProtobuf.ImageLocation convertToImageLocation(
            JsonDetectionOutputObject detection) {
        return DetectionProtobuf.ImageLocation.newBuilder()
                .setXLeftUpper(detection.getX())
                .setYLeftUpper(detection.getY())
                .setWidth(detection.getWidth())
                .setHeight(detection.getHeight())
                .setConfidence(detection.getConfidence())
                .putAllDetectionProperties(detection.getDetectionProperties())
                .build();
    }

    private static SubjectProtobuf.VideoDetectionJobResults createVideoResults(
            JsonOutputObject outputObject,
            JsonMediaOutputObject media,
            String trackType,
            JsonActionOutputObject action) {
        var videoJobResults = SubjectProtobuf.VideoDetectionJobResults.newBuilder();
        videoJobResults.setDetectionJob(createDetectionJob(
                outputObject, media, trackType, action.getAlgorithm()));
        for (var track : action.getTracks()) {
            videoJobResults.putResults(track.getId(), convertToVideoTrack(track));
        }
        return videoJobResults.build();
    }

    private static DetectionProtobuf.VideoTrack convertToVideoTrack(JsonTrackOutputObject track) {
        var videoTrack = DetectionProtobuf.VideoTrack.newBuilder()
                .setStartFrame(track.getStartOffsetFrame())
                .setStopFrame(track.getStartOffsetFrame())
                .setConfidence(track.getConfidence())
                .putAllDetectionProperties(track.getTrackProperties());
        for (var detection : track.getDetections()) {
            videoTrack.putFrameLocations(
                    detection.getOffsetFrame(), convertToImageLocation(detection));
        }
        return videoTrack.build();
    }
}
