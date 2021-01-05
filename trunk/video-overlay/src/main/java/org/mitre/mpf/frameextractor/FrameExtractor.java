/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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
import org.mitre.mpf.interop.JsonDetectionOutputObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;


import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class FrameExtractor {
    private static final Logger log = LoggerFactory.getLogger(FrameExtractor.class);

    private final URI media;
    private final URI extractionDirectory;
    private final FileNameGenerator fileNameGenerator;
    private final boolean croppingFlag;
    private final boolean rotationFillIsBlack;

    // Maps frame numbers to pairs of trackIndex and detection to be extracted.
    private SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> extractionsMap = new TreeMap<>();
    public SortedMap<Integer, Map<Integer, JsonDetectionOutputObject>> getExtractionsMap() {
        return extractionsMap;
    }
    // Access methods for the extractions map.
    public Set<Integer> getFrameNumbers() { return extractionsMap.keySet(); }
    public Set<Integer> getTrackIndices(Integer frameNumber) { return extractionsMap.get(frameNumber).keySet(); }
    public JsonDetectionOutputObject getDetection(Integer frameNumber, Integer trackIndex) {
        return extractionsMap.get(frameNumber).get(trackIndex);
    }

    private String prefix = "frame";


    public FrameExtractor(URI media, URI extractionDirectory, boolean croppingFlag,
                          boolean rotationFillIsBlack) {
        this(media, extractionDirectory, FrameExtractor::defaultFileNameGenerator, croppingFlag,
             rotationFillIsBlack);
    }

    public FrameExtractor(URI media, URI extractionDirectory, FileNameGenerator fileNameGenerator,
                          boolean croppingFlag, boolean rotationFillIsBlack) {
        this.media = media;
        this.extractionDirectory = extractionDirectory;
        this.fileNameGenerator = fileNameGenerator;
        this.croppingFlag = croppingFlag;
        this.rotationFillIsBlack = rotationFillIsBlack;
    }


    public Table<Integer, Integer, String> execute() throws IOException {
        Split split = SimonManager.getStopwatch("org.mitre.mpf.frameextractor.FrameExtractor.execute").start();
        try {
            if (media == null) {
                throw new IllegalStateException("media URI must not be null");
            }

            if (extractionDirectory == null) {
                throw new IllegalStateException("extractionDirectory must not be null");
            }

            Table<Integer, Integer, String> extractedPathTable = HashBasedTable.create();
            List<FrameExtractionResult> results = new ArrayList<>();

            Split nativeSplit = SimonManager.getStopwatch("org.mitre.mpf.frameextractor.FrameExtractor.execute->native").start();
            int response = -1;
            try {
                if(extractionsMap.size() == 0) {
                    log.debug("extractionsMap is empty -- nothing to extract.");
                    response = 0;
                }
                else {
                    response = executeNative(
                            new File(media).getAbsolutePath(),
                            new File(extractionDirectory).getAbsolutePath(),
                            croppingFlag,
                            rotationFillIsBlack,
                            results);
                }
            } finally {
                nativeSplit.stop();
            }

            if (response != 0) {
                throw new FrameExtractorJniException(response);
            }
            results.stream().forEach(e -> extractedPathTable.put(e.trackNumber, e.frameNumber, e.filePath));

            return extractedPathTable;
        } finally {
            split.stop();
        }
    }

    private String makeFilename(String path, int trackNumber, int frameNumber) {
        return fileNameGenerator.generateFileName(path, trackNumber, frameNumber, prefix);
    }

    private native int executeNative(String sourceMedia, String extractionDestination,
                                     boolean croppingFlag, boolean rotationFillIsBlack,
                                     List<FrameExtractionResult> results);

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
        return String.format("%s#<media='%s', extractionDirectory='%s', frames=%d>",
                             this.getClass().getSimpleName(),
                             media,
                             extractionDirectory,
                             extractionsMap.keySet().size());
    }

    private static String defaultFileNameGenerator(String path, int trackNumber, int frameNumber, String prefix) {
        try {
            File outputFile = new File(String.format("%s/%d", path, trackNumber), String.format("%s-%d.png", prefix, frameNumber));
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
        public String generateFileName(String path, int trackNumber, int frameNumber, String prefix);
    }
}
