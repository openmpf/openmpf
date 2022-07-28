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

package org.mitre.mpf.wfm.data.entities.persistent;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.FrameTimeInfo;
import org.mitre.mpf.wfm.util.MediaRange;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

// Suppress because it's better than having to explicitly use MediaImpl during deserialization.
@SuppressWarnings("ClassReferencesSubclass")
@JsonSerialize(as = MediaImpl.class)
public interface Media {

    /** The unique identifier for this file. */
    public long getId();

    public long getParentId();

    public int getCreationTask();

    public boolean wasActionProcessed(int taskIndex, int actionIndex);

    public int getLastProcessedTaskIndex();

    public boolean isDerivative();

    public String getUri();

    /** The URI scheme (protocol) associated with the input URI, as obtained from the media resource. */
    public UriScheme getUriScheme();

    /** The path to the media that components should use. */
    public Path getProcessingPath();

    /** The local file path of the file once it has been retrieved. May be null if the media is not a file, or the file path has not been externally set. */
    public Path getLocalPath();

    /** The path to the media that the JSON output object should use. */
    public String getPersistentUri();

    /** If the media needed to be converted to another format, this will contain the path to converted media. */
    public Optional<Path> getConvertedMediaPath();

    /** For derivative media, this will contain the URI to the media once placed in storage at the end of a job. */
    public Optional<String> getStorageUri();

    /** A flag indicating if the medium has encountered an error during processing. Will be false if no error occurred. */
    public boolean isFailed();

    /** A message indicating what error(s) a medium has encountered during processing. Will be null if no error occurred. */
    public String getErrorMessage();

    /** The data type of the medium. For example, VIDEO. */
    public MediaType getType();

    /** The MIME type of the medium. */
    public String getMimeType();

    /** The Metadata for the medium. */
    public Map<String,String> getMetadata();
    public String getMetadata(String key);

    /** The Algorithm properties to override for the medium. */
    public ImmutableMap<String, String> getMediaSpecificProperties();
    public String getMediaSpecificProperty(String key);

    /** The provided Metadata properties to override for the medium. */
    public ImmutableMap<String, String> getProvidedMetadata();

    /** The length of the medium in frames (for images and videos) or milliseconds (for audio). */
    public int getLength();

    /** The SHA 256 hash of the local file (assuming it could be retrieved. */
    public String getSha256();

    public FrameTimeInfo getFrameTimeInfo();

    public ImmutableSet<MediaRange> getFrameRanges();

    public ImmutableSet<MediaRange> getTimeRanges();
}
