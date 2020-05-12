/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.mitre.mpf.frameextractor.FrameExtractor;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.util.ThreadUtil;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class StreamableFrameExtractor {

    private StreamableFrameExtractor() {
    }

    @FunctionalInterface
    public static interface StoreStream {
        public URI store(InputStream inputStream) throws IOException, StorageException;
    }

    public static Table<Integer, Integer, URI> run(ArtifactExtractionRequest request, StoreStream storeStreamFn)
            throws IOException, StorageException {
        Path tempDirectory = Files.createTempDirectory(
                "artifacts_" + request.getJobId() + '_' + request.getMediaId() + '_' + request.getActionIndex() + '_')
                .toAbsolutePath();
        Path pipePath = tempDirectory.resolve("pipe.png");
        createNamedPipe(pipePath.toString());

        try {
            BlockingQueue<MutablePair<Integer, Integer>> queue = new SynchronousQueue<>();
            FrameExtractor frameExtractor = new FrameExtractor(
                    Paths.get(request.getPath()).toUri(),
                    tempDirectory.toUri(),
                    filenameGenerator(pipePath.toString(), queue),
                    request.getCroppingFlag());

            frameExtractor.getExtractionsMap().putAll(request.getExtractionsMap());

            ThreadUtil.runAsync(() -> {
                try {
                    frameExtractor.execute();
                } finally {
                    queue.put(MutablePair.of(null, null));
                }
            });

            Pair<Integer, Integer> trackAndFrame;
            Table<Integer, Integer, URI> trackAndFrameToUri = HashBasedTable.create();
            while (!(trackAndFrame = queue.take()).equals(MutablePair.of(null, null))) {
                try (InputStream inputStream = Files.newInputStream(pipePath)) {
                    URI location = storeStreamFn.store(inputStream);
                    trackAndFrameToUri.put(trackAndFrame.getLeft(), trackAndFrame.getRight(), location);
                }
            }
            return trackAndFrameToUri;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            Files.delete(pipePath);
            Files.delete(tempDirectory);
        }
    }

    private static FrameExtractor.FileNameGenerator filenameGenerator(String pipePath,
                                                                      BlockingQueue<MutablePair<Integer, Integer>> queue) {
        return (path, track, frame, prefix) -> {
            try {
                queue.put(new MutablePair<>(track, frame));
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
}
