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

package org.mitre.mpf.frameextractor;

import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class FrameExtractor {
    private static final Logger log = LoggerFactory.getLogger(FrameExtractor.class);

    private final URI video;
    private final URI extractionDirectory;
    private final Set<Integer> frames = new TreeSet<Integer>();
    private final FileNameGenerator fileNameGenerator;
    private String prefix = "frame";


    public FrameExtractor(URI video, URI extractionDirectory) {
        this(video, extractionDirectory, FrameExtractor::defaultFileNameGenerator);
    }

    public FrameExtractor(URI video, URI extractionDirectory, FileNameGenerator fileNameGenerator) {
        this.video = video;
        this.extractionDirectory = extractionDirectory;
        this.fileNameGenerator = fileNameGenerator;
    }


    public Map<Integer, String> execute() throws IOException {
        Split split = SimonManager.getStopwatch("org.mitre.mpf.frameextractor.FrameExtractor.execute").start();
        try {
            if (video == null) {
                throw new IllegalStateException("video must not be null");
            }

            if (extractionDirectory == null) {
                throw new IllegalStateException("extractionDirectory must not be null");
            }

            Map<Integer, String> paths = new HashMap<Integer, String>();

            Split nativeSplit = SimonManager.getStopwatch("org.mitre.mpf.frameextractor.FrameExtractor.execute->native").start();
            int response = -1;
            try {
                if(frames.size() == 0) {
                    log.debug("The collection of frames to extract was empty.");
                    response = 0;
                } else {
                    response = executeNative(new File(video).getAbsolutePath(), new File(extractionDirectory).getAbsolutePath(), paths);
                }
            } finally {
                nativeSplit.stop();
            }


            if (response != 0) {
                throw new FrameExtractorJniException(String.format("Native method invocation returned the error code %d.", response), response);
            }

            return paths;
        } finally {
            split.stop();
        }
    }

    public Set<Integer> getFrames() {
        return frames;
    }

    private String makeFilename(String path, int frameNumber) {
        return fileNameGenerator.generateFileName(path, frameNumber, prefix);
    }

    private native int executeNative(String sourceVideo, String destinationVideo, Map<Integer, String> paths);

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        if(prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException("prefix must not be null or empty");
        }
        this.prefix = prefix;
    }

    @Override
    public String toString() {
        return String.format("%s#<video='%s', extractionDirectory='%s', frames=%d>",
                this.getClass().getSimpleName(),
                video,
                extractionDirectory,
                frames.size());
    }

    private static String defaultFileNameGenerator(String path, int frameNumber, String prefix) {
        try {
            File outputFile = new File(path, String.format("%s-%d.png", prefix, frameNumber));
            outputFile.getParentFile().mkdirs();
            return outputFile.getAbsolutePath();
        } catch(Exception exception) {
            log.error("Failed to create file '{}/{}' due to an exception.", path,
                      String.format("%s-%d.png", prefix, frameNumber), exception);
            return null;
        }
    }

    @FunctionalInterface
    public static interface FileNameGenerator {
        public String generateFileName(String path, int frameNumber, String prefix);
    }
}
