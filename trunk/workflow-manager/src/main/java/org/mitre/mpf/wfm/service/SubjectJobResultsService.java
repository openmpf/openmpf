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

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.mitre.mpf.rest.api.subject.Entity;
import org.mitre.mpf.rest.api.subject.Relationship;
import org.mitre.mpf.rest.api.subject.Relationship.MediaReference;
import org.mitre.mpf.rest.api.subject.SubjectJobResult;
import org.mitre.mpf.wfm.buffers.SubjectProtobuf;
import org.mitre.mpf.wfm.data.access.SubjectJobRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbCancellationState;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJobOutput;
import org.mitre.mpf.wfm.util.JmsUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SubjectJobResultsService {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectJobResultsService.class);

    private final PropertiesUtil _propertiesUtil;

    private final ObjectMapper _objectMapper;

    private final SubjectJobRepo _subjectJobRepo;

    private final JmsUtils _jmsUtils;

    @Inject
    SubjectJobResultsService(
            PropertiesUtil propertiesUtil,
            ObjectMapper objectMapper,
            SubjectJobRepo subjectJobRepo,
            JmsUtils jmsUtils) {
        _propertiesUtil = propertiesUtil;
        _objectMapper = objectMapper;
        _subjectJobRepo = subjectJobRepo;
        _jmsUtils = jmsUtils;
    }


    @Transactional
    public void completeJob(long jobId, SubjectProtobuf.SubjectTrackingResult pbResult) {
        var job = _subjectJobRepo.findById(jobId).orElseThrow();
        try {
            storeResults(job, pbResult);
        }
        finally {
            cleanup(job);
        }
    }

    @Transactional
    public void cancel(long jobId) {
        var job = _subjectJobRepo.findById(jobId).orElseThrow();
        try {
            job.setCancellationState(DbCancellationState.CANCELLED_BY_USER);
            job.setTimeCompleted(Instant.now());
            _subjectJobRepo.save(job);
            LOG.info("Successfully cancelled job.");
        }
        finally {
            cleanup(job);
        }
    }

    private void cleanup(DbSubjectJob job) {
        _jmsUtils.deleteSubjectCancelRoute(job.getId(), job.getComponentName());
    }

    private void storeResults(DbSubjectJob job, SubjectProtobuf.SubjectTrackingResult pbResult) {
        pbResult.getErrorsList().forEach(job::addError);

        var convertedResults = convertResults(pbResult);
        long jobId = job.getId();
        var resultsPath = _propertiesUtil.getSubjectTrackingResultsDir()
                .resolve(jobId + ".json");
        try {
            var json = _objectMapper.writeValueAsString(convertedResults);
            _subjectJobRepo.saveOutput(new DbSubjectJobOutput(job, json));
            Files.writeString(resultsPath, json);
            LOG.info("Wrote job results to {}", resultsPath);
        }
        catch (IOException e) {
            job.addError("Failed to create output object due to: " + e);
            throw new UncheckedIOException(e);
        }
        finally {
            job.setTimeCompleted(Instant.now());
            _subjectJobRepo.save(job);
        }
    }

    private static SubjectJobResult convertResults(
            SubjectProtobuf.SubjectTrackingResult pbResult) {
        var entityGroups = convertEntityGroups(pbResult);
        var relationshipGroups = getRelationshipGroups(pbResult);
        return new SubjectJobResult(entityGroups, relationshipGroups);
    }

    private static Map<String, List<Entity>> convertEntityGroups(
            SubjectProtobuf.SubjectTrackingResult pbResult) {
        var entityGroups = new HashMap<String, List<Entity>>();
        for (var entry : pbResult.getEntityGroupsMap().entrySet()) {
            var entityType = entry.getKey();
            var entityList = entry
                    .getValue()
                    .getEntitiesList()
                    .stream()
                    .map(e -> new Entity(
                            e.getId(), e.getScore(), convertTrackMap(e),
                            Map.copyOf(e.getPropertiesMap())))
                    .toList();
            entityGroups.put(entityType, entityList);
        }
        return entityGroups;
    }

    private static Map<String, List<String>> convertTrackMap(SubjectProtobuf.Entity pbEntity) {
        return pbEntity.getTracksMap().entrySet()
            .stream()
            .collect(toMap(
                    Map.Entry::getKey,
                    e -> List.copyOf(e.getValue().getTrackIdsList())));
    }


    private static Map<String, List<Relationship>> getRelationshipGroups(
            SubjectProtobuf.SubjectTrackingResult pbResult) {
        var relationshipGroups = new HashMap<String, List<Relationship>>();
        for (var entry : pbResult.getRelationshipGroupsMap().entrySet()) {
            var relationshipType = entry.getKey();
            var relationships = entry.getValue().getRelationshipsList()
                    .stream()
                    .map(r -> new Relationship(
                            List.copyOf(r.getEntitiesList()),
                            convertFrameMap(r),
                            Map.copyOf(r.getPropertiesMap())))
                    .toList();
            relationshipGroups.put(relationshipType, relationships);
        }
        return relationshipGroups;
    }

    private static List<MediaReference> convertFrameMap(
            SubjectProtobuf.Relationship pbRelationship) {
        return pbRelationship.getFramesMap()
                .entrySet()
                .stream()
                .map(e -> new MediaReference(
                        e.getKey(), List.copyOf(e.getValue().getFramesList())))
                .toList();
    }
}
