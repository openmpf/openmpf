/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
import org.mitre.mpf.frameextractor.FrameExtractor;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import static java.util.stream.Collectors.toMap;


@Component
public class LocalStorageBackend implements StorageBackend {

    private final PropertiesUtil _propertiesUtil;

    private final ObjectMapper _objectMapper;


    @Inject
    LocalStorageBackend(PropertiesUtil propertiesUtil, ObjectMapper objectMapper) {
        _propertiesUtil = propertiesUtil;
        _objectMapper = objectMapper;
    }


    @Override
    public boolean canStore(JsonOutputObject outputObject) {
        return true;
    }

    @Override
    public URI store(JsonOutputObject outputObject) throws IOException {
        File outputFile = _propertiesUtil.createDetectionOutputObjectFile(outputObject.getJobId());
        _objectMapper.writeValue(outputFile, outputObject);
        return outputFile.getAbsoluteFile().toURI();
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
    public URI storeImageArtifact(ArtifactExtractionRequest request) throws IOException {
        Path inputMediaPath = Paths.get(request.getPath());
        Path artifactFile = _propertiesUtil.createArtifactFile(request.getJobId(),
                                                               request.getMediaId(),
                                                               request.getStageIndex(),
                                                               inputMediaPath.getFileName().toString()).toPath();
        Files.copy(inputMediaPath, artifactFile, StandardCopyOption.REPLACE_EXISTING);
        return artifactFile.toAbsolutePath().toUri();
    }


    @Override
    public Map<Integer, URI> storeVideoArtifacts(ArtifactExtractionRequest request) throws IOException {
        URI artifactsDirectory = _propertiesUtil.createArtifactDirectory(request.getJobId(),
                                                                         request.getMediaId(),
                                                                         request.getStageIndex()).toURI();
        FrameExtractor frameExtractor = new FrameExtractor(Paths.get(request.getPath()).toUri(),
                                                           artifactsDirectory);
        frameExtractor.getFrames().addAll(request.getFrameNumbers());
        Map<Integer, String> extractionResults = frameExtractor.execute();

        return extractionResults.entrySet()
                .stream()
                .collect(toMap(Map.Entry::getKey,
                               e -> Paths.get(e.getValue()).toUri()));
    }
}
