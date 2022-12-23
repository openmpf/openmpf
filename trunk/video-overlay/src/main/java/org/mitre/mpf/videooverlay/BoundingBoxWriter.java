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

package org.mitre.mpf.videooverlay;

import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

public class BoundingBoxWriter {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(BoundingBoxWriter.class);

    private URI sourceMedium;

    public URI getSourceMedium() {
        return sourceMedium;
    }

    public void setSourceMedium(URI sourceMedium) {
        this.sourceMedium = sourceMedium;
    }

    private URI destinationMedium;

    public URI getDestinationMedium() {
        return destinationMedium;
    }

    public void setDestinationMedium(URI destinationMedium) {
        this.destinationMedium = destinationMedium;
    }

    private BoundingBoxMap boundingBoxMap = new BoundingBoxMap(); // Assigned a default value to prevent NPEs.

    public BoundingBoxMap getBoundingBoxMap() {
        return boundingBoxMap;
    }

    private Map<String, String> mediaMetadata = Map.of();

    public void setMediaMetadata(Map<String, String> mediaMetadata) { this.mediaMetadata = mediaMetadata; }

    private Map<String, String> requestProperties = Map.of();

    public void setRequestProperties(Map<String, String> requestProperties) { this.requestProperties = requestProperties; }

    /**
     * Sets the BoundingBoxMap associated with this writer. The parameter must not be null.
     */
    public void setBoundingBoxMap(BoundingBoxMap boundingBoxMap) {
        if (boundingBoxMap == null) {
            throw new IllegalArgumentException("boundingBoxMap must not be null");
        }

        this.boundingBoxMap = boundingBoxMap;
    }

    /**
     * Creates a new instance of this class. The {@link #getBoundingBoxMap() BoundingBoxMap} associated
     * with this instance is automatically initialized.
     */
    public BoundingBoxWriter() {
    }

    /**
     * Marks up the source video using the information contained in the bounding box map and writes the marked up video
     * to the destination video file. The {@link #setSourceMedium(URI) sourceMedium} and {@link #setDestinationMedium(URI) destinationMedium}
     * values must be assigned to non-null values prior to invoking this method.
     */
    public void markupVideo() throws IOException {
        markupMedium(Medium.VIDEO);
    }

    public void markupImage() throws IOException {
        markupMedium(Medium.IMAGE);
    }

    private enum Medium {
        IMAGE,
        VIDEO
    }

    private void markupMedium(Medium medium) throws IOException {

        if (sourceMedium == null) {
            throw new IllegalStateException("sourceMedium must not be null");
        }

        if (destinationMedium == null) {
            throw new IllegalStateException("destinationMedium must not be null");
        }
        File sourceFile = new File(sourceMedium.getPath()).getAbsoluteFile();
        File destinationFile = new File(destinationMedium.getPath()).getAbsoluteFile();

        int maxLabelLength = boundingBoxMap.values()
                .stream()
                .flatMap(Collection::stream)
                .mapToInt(b -> b.getLabel().orElse("").length())
                .max()
                .orElse(0);
        try {
            if (medium == Medium.IMAGE) {
                log.debug("markupImage: source = '{}' (exists = {}), destination = '{}' (exists = {})",
                          sourceFile.getPath(), sourceFile.exists(),
                          destinationFile.getPath(), destinationFile.exists());
                markupImageNative(sourceFile.getAbsolutePath(), mediaMetadata,
                                  destinationFile.getAbsolutePath(), requestProperties,
                                  maxLabelLength);
            }
            else {
                log.debug("markupVideo: source = '{}' (exists = {}), destination = '{}' (exists = {})",
                          sourceFile.getPath(), sourceFile.exists(),
                          destinationFile.getPath(), destinationFile.exists());
                markupVideoNative(sourceFile.getAbsolutePath(), mediaMetadata,
                                  destinationFile.getAbsolutePath(), requestProperties,
                                  maxLabelLength);
            }
        }
        catch (Exception e) {
            throw new VideoOverlayJniException(e);
        }
    }

    private native void markupVideoNative(String sourceVideo, Map<String, String> mediaMetadata,
                                          String destinationVideo,
                                          Map<String, String> requestProperties,
                                          int maxLabelLength);

	private native void markupImageNative(String sourceImage, Map<String, String> mediaMetadata,
                                          String destinationImage,
                                          Map<String, String> requestProperties,
                                          int maxLabelLength);

}
