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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.mitre.mpf.frameextractor.FrameExtractor;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestOutputStream;
import java.security.MessageDigest;


@Component
public class LocalStorageBackend implements StorageBackend {

    private final PropertiesUtil _propertiesUtil;

    private final ObjectMapper _objectMapper;

    private final InProgressBatchJobsService _inProgressJobs;


    @Inject
    LocalStorageBackend(PropertiesUtil propertiesUtil,
                        ObjectMapper objectMapper,
                        InProgressBatchJobsService inProgressBatchJobsService) {
        _propertiesUtil = propertiesUtil;
        _objectMapper = objectMapper;
        _inProgressJobs = inProgressBatchJobsService;
    }


    @Override
    public boolean canStore(JsonOutputObject outputObject) {
        return true;
    }

    @Override
    public URI store(JsonOutputObject outputObject, Mutable<String> outputSha) throws IOException {
        long internalJobId = _propertiesUtil.getJobIdFromExportedId(outputObject.getJobId());
        Path outputPath = _propertiesUtil.createDetectionOutputObjectFile(internalJobId);
        if (outputSha.getValue() == null) {
            MessageDigest digest = DigestUtils.getSha256Digest();
            try (var outStream = new DigestOutputStream(Files.newOutputStream(outputPath), digest)) {
                _objectMapper.writeValue(outStream, outputObject);
            }
            outputSha.setValue(Hex.encodeHexString(digest.digest()));
        }
        else {
            _objectMapper.writeValue(outputPath.toFile(), outputObject);
        }
        return outputPath.toUri();
    }


    @Override
    public boolean canStore(MarkupResult markupResult) {
        return true;
    }

    @Override
    public void store(MarkupResult markupResult) {
        // No-op: markup is stored locally by the markup component.
    }


    @Override
    public boolean canStore(ArtifactExtractionRequest request) {
        return true;
    }


    @Override
    public Table<Integer, Integer, URI> storeArtifacts(ArtifactExtractionRequest request) throws IOException {
        URI artifactsDirectory = _propertiesUtil.createArtifactDirectory(
                request.getJobId(), request.getMediaId(), request.getTaskIndex(),
                request.getActionIndex()).toURI();

        FrameExtractor frameExtractor = new FrameExtractor(
                Paths.get(request.getMediaPath()).toUri(), request.getMediaMetadata(), artifactsDirectory,
                request.getCroppingFlag(), request.getRotationFillIsBlack());
        frameExtractor.getExtractionsMap().putAll(request.getExtractionsMap());

        Table<Integer, Integer, String> extractionResults = frameExtractor.execute();
        return Tables.transformValues(extractionResults, v -> Paths.get(v).toUri());
    }


    @Override
    public boolean canStoreDerivativeMedia(BatchJob job, long parentMediaId) {
        return true;
    }

    @Override
    public void storeDerivativeMedia(BatchJob job, Media media) throws IOException {
        var storagePath = _propertiesUtil.createDerivativeMediaPath(job.getId(), media);
        Files.move(media.getLocalPath(), storagePath);
        _inProgressJobs.addStorageUri(job.getId(), media.getId(), storagePath.toUri().toString());
    }
}
