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
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.transients.TransientDetectionSystemProperties;
import org.mitre.mpf.wfm.enums.MarkupStatus;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.function.Function;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;


@Service
public class StorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageServiceImpl.class);

    private final PropertiesUtil _propertiesUtil;

    private final Redis _redis;

    private final ObjectMapper _objectMapper;

    private final Map<StorageBackend.Type, StorageBackend> _backends;


    @Inject
    StorageServiceImpl(
            PropertiesUtil propertiesUtil,
            Redis redis,
            ObjectMapper objectMapper,
            Collection<StorageBackend> storageBackends) {
        _propertiesUtil = propertiesUtil;
        _redis = redis;
        _objectMapper = objectMapper;

        _backends = new EnumMap<>(StorageBackend.Type.class);
        for (StorageBackend backend : storageBackends) {
            StorageBackend prevBackend = _backends.put(backend.getType(), backend);
            if (prevBackend != null) {
                throw new IllegalStateException(String.format(
                        "Both %s and %s claim to be the storage service for %s.",
                        prevBackend.getClass(), backend.getClass(), backend.getType()));
            }
        }

        EnumSet<StorageBackend.Type> missingBackends = EnumSet.allOf(StorageBackend.Type.class);
        missingBackends.remove(StorageBackend.Type.NONE);
        missingBackends.removeAll(_backends.keySet());
        if (!missingBackends.isEmpty()) {
            throw new IllegalStateException("The following storage backends are missing: " + missingBackends);
        }
    }


    private StorageBackend getStorageBackend(StorageBackend.Type backendType) {
        StorageBackend storageBackend = _backends.get(backendType);
        if (storageBackend == null && backendType != StorageBackend.Type.NONE) {
            log.warn("Unknown storage type: {}. Objects will be stored locally.", backendType);
        }
        return storageBackend;
    }


    @Override
    public String store(JsonOutputObject outputObject) throws IOException {
        TransientDetectionSystemProperties propertiesSnapshot = _redis.getPropertiesSnapshot(outputObject.getJobId());
        try {
            StorageBackend storageBackend = getStorageBackend(propertiesSnapshot.getHttpObjectStorageType());
            if (storageBackend != null) {
                return storageBackend.storeAsJson(propertiesSnapshot.getHttpStorageServiceUri(), outputObject);
            }
        }
        catch (StorageException e) {
            log.warn(String.format(
                    "Failed to remotely store output object for job id %s. It will be stored locally instead.",
                    outputObject.getJobId()), e);
            outputObject.getJobWarnings().add(
                    "This output object was stored locally because storing it remotely failed due to: " + e);
        }
        return storeLocally(outputObject);
    }


    private String storeLocally(JsonOutputObject outputObject) throws IOException {
        File outputFile = _propertiesUtil.createDetectionOutputObjectFile(outputObject.getJobId());
        _objectMapper.writeValue(outputFile, outputObject);
        return outputFile.getAbsoluteFile().toURI().toString();
    }


    @Override
    public void store(MarkupResult markupResult) {
        if (markupResult.getMarkupStatus() != MarkupStatus.COMPLETE) {
            return;
        }

        TransientDetectionSystemProperties propertiesSnapshot = _redis.getPropertiesSnapshot(markupResult.getJobId());
        try {
            StorageBackend storageBackend = getStorageBackend(propertiesSnapshot.getHttpObjectStorageType());
            if (storageBackend == null) {
                return;
            }

            Path localPath = IoUtils.toLocalPath(markupResult.getMarkupUri())
                    .orElse(null);

            URL markupUrl = localPath == null
                    ? new URL(markupResult.getMarkupUri())
                    : localPath.toUri().toURL();

            String newLocation = storageBackend.store(propertiesSnapshot.getHttpStorageServiceUri(), markupUrl);
            markupResult.setMarkupUri(newLocation);
            if (localPath != null) {
                Files.delete(localPath);
            }
        }
        catch (IOException | StorageException e) {
            log.warn(String.format(
                    "Failed to remotely store markup for job id %s. It will be stored locally instead.",
                    markupResult.getJobId()), e);
            addMarkupWarning(markupResult,
                             "Markup was stored locally because storing it remotely failed due to: " + e);
        }
    }


    private void addMarkupWarning(MarkupResult markupResult, String message) {
        String existingMessage = markupResult.getMessage();
        if (existingMessage != null && !existingMessage.isEmpty()) {
            message = existingMessage + "; " + message;
        }
        markupResult.setMessage(message);
        markupResult.setMarkupStatus(MarkupStatus.COMPLETE_WITH_WARNING);
        _redis.addJobWarning(markupResult.getJobId(), message);
    }


    @Override
    public Map<Integer, String> store(ArtifactExtractionRequest request) {
        switch (request.getMediaType()) {
            case IMAGE:
                return Collections.singletonMap(0, processImageArtifact(request));
            case VIDEO:
                return processVideoArtifact(request);
            default:
                return processUnsupportedArtifact(request);
        }
    }


    private String processImageArtifact(ArtifactExtractionRequest request) {
        Path inputMediaPath = Paths.get(request.getPath());
        TransientDetectionSystemProperties propertiesSnapshot = _redis.getPropertiesSnapshot(request.getJobId());
        try {
            StorageBackend storageBackend = getStorageBackend(propertiesSnapshot.getHttpObjectStorageType());
            if (storageBackend != null) {
                return storageBackend.store(propertiesSnapshot.getHttpStorageServiceUri(), inputMediaPath);
            }
        }
        catch (StorageException | IOException e) {
            log.warn(String.format(
                    "Failed to remotely store artifact for job id %s. It will be stored locally instead.",
                    request.getJobId()), e);
            addArtifactStoredLocallyWarning(request.getJobId(), e);
        }

        try {
            Path artifactFile = _propertiesUtil.createArtifactFile(request.getJobId(),
                                                                   request.getMediaId(),
                                                                   request.getStageIndex(),
                                                                   inputMediaPath.getFileName().toString()).toPath();
            Files.copy(inputMediaPath, artifactFile, StandardCopyOption.REPLACE_EXISTING);
            return artifactFile.toAbsolutePath().toUri().toString();
        }
        catch (IOException e) {
            log.warn("[{}|{}|ARTIFACT_EXTRACTION] Failed to copy the image Media #{} to the artifacts directory " +
                             "due to an exception. All detections (including exemplars) produced in this stage " +
                             "for this medium will NOT have an associated artifact.",
                     request.getJobId(), request.getStageIndex(), request.getMediaId(), e);
            return ArtifactExtractionProcessor.ERROR_PATH;
        }
    }

    private void addArtifactStoredLocallyWarning(long jobId, Exception ex) {
        _redis.addJobWarning(
                jobId,
                "Artifacts were stored locally because storing them remotely failed due to: " + ex);
    }


    private Map<Integer, String> processVideoArtifact(ArtifactExtractionRequest request) {
        SortedSet<Integer> frameNumbers = request.getActionIndexToMediaIndexes()
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(toCollection(TreeSet::new));

        TransientDetectionSystemProperties propertiesSnapshot = _redis.getPropertiesSnapshot(request.getJobId());
        try {
            StorageBackend storageBackend = getStorageBackend(propertiesSnapshot.getHttpObjectStorageType());
            if (storageBackend != null) {
                return storeVideoArtifactRemotely(storageBackend, propertiesSnapshot.getHttpStorageServiceUri(),
                                                  request, frameNumbers);
            }
        }
        catch (StorageException | IOException e) {
            log.warn(String.format("Failed to store artifact for job id %s. It will be stored locally instead.",
                                   request.getJobId()), e);
            addArtifactStoredLocallyWarning(request.getJobId(), e);
        }

        try {
            return storeVideoArtifactsLocally(request, frameNumbers);
        }
        catch (IOException e) {
            // In the event of an exception, all of the tracks (and associated detections) created
            // for this medium in this stage (regardless of the action which produced them) will not have artifacts
            // associated with them.
            log.warn("[Job {}|{}|ARTIFACT_EXTRACTION] Failed to extract the artifacts from Media #{} due to an " +
                             "exception. All detections (including exemplars) produced in this stage " +
                             "for this medium will NOT have an associated artifact.",
                     request.getJobId(), request.getStageIndex(), request.getMediaId(), e);

            return frameNumbers.stream()
                    .collect(toMap(Function.identity(), x -> ArtifactExtractionProcessor.ERROR_PATH));
        }
    }


    private static Map<Integer, String> storeVideoArtifactRemotely(
            StorageBackend storageBackend,
            URI serviceUri,
            ArtifactExtractionRequest request,
            Collection<Integer> frameNumbers) throws IOException, StorageException {

        Path tempDirectory = Files.createTempDirectory(
                "artifacts_" + request.getJobId() + '_' + request.getMediaId() + '_').toAbsolutePath();
        Path pipePath = tempDirectory.resolve("pipe.png");
        createNamedPipe(pipePath.toString());

        try {
            BlockingQueue<Integer> queue = new SynchronousQueue<>();
            FrameExtractor frameExtractor = new FrameExtractor(
                    Paths.get(request.getPath()).toUri(),
                    tempDirectory.toUri(),
                    filenameGenerator(pipePath.toString(), queue));

            frameExtractor.getFrames().addAll(frameNumbers);

            ThreadUtil.runAsync(() -> {
                try {
                    frameExtractor.execute();
                }
                finally {
                    queue.put(-1);
                }
            });

            Map<Integer, String> paths = new HashMap<>();
            int frameNumber;
            while ((frameNumber = queue.take()) >= 0) {
                String location = storageBackend.store(serviceUri, pipePath);
                paths.put(frameNumber, location);
            }
            addMissingKeys(frameNumbers, paths, request);
            return paths;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        finally {
            Files.delete(pipePath);
            Files.delete(tempDirectory);
        }
    }

    private static FrameExtractor.FileNameGenerator filenameGenerator(String pipePath,
                                                                      BlockingQueue<Integer> queue) {
        return (path, frame, prefix) -> {
            try {
                queue.put(frame);
                return pipePath;
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        };
    }

    private static void createNamedPipe(String path) throws IOException {
        try {
            int rc = new ProcessBuilder("mkfifo", path)
                    .inheritIO()
                    .start()
                    .waitFor();
            if (rc != 0) {
                throw new IOException("Failed to create named pipe at: " + path);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void addMissingKeys(Iterable<Integer> expectedKeys, Map<Integer, String> map,
                                       ArtifactExtractionRequest request) {
        for (int key : expectedKeys) {
            String previousValue = map.putIfAbsent(key, ArtifactExtractionProcessor.ERROR_PATH);
            if (previousValue == null) {
                log.warn("[Job {}|{}|ARTIFACT_EXTRACTION] Failed to extract artifact from Media #{} at frame {}.",
                         request.getJobId(), request.getStageIndex(), request.getMediaId(), key);
            }
        }
    }

    private Map<Integer, String> storeVideoArtifactsLocally(
            ArtifactExtractionRequest request,
            Collection<Integer> frameNumbers) throws IOException {

        URI artifactsDirectory = _propertiesUtil.createArtifactDirectory(request.getJobId(),
                                                                         request.getMediaId(),
                                                                         request.getStageIndex()).toURI();
        FrameExtractor frameExtractor = new FrameExtractor(Paths.get(request.getPath()).toUri(), artifactsDirectory);
        frameExtractor.getFrames().addAll(frameNumbers);
        Map<Integer, String> results = frameExtractor.execute();
        results.replaceAll((idx, p) -> Paths.get(p).toUri().toString());
        addMissingKeys(frameNumbers, results, request);
        return results;
    }


    private static Map<Integer, String> processUnsupportedArtifact(ArtifactExtractionRequest request) {
        log.warn("[Job {}:{}:ARTIFACT_EXTRACTION] Media #{} reports media type {} which is not supported by " +
                 "artifact extraction.",
                 request.getJobId(), request.getStageIndex(), request.getMediaId(), request.getMediaType());
        return request.getActionIndexToMediaIndexes()
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(toMap(Function.identity(), v -> ArtifactExtractionProcessor.UNSUPPORTED_PATH));
    }
}
