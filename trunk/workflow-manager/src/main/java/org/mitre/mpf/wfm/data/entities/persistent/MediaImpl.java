/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.FrameTimeInfo;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.MediaRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class MediaImpl implements Media {

    /** The unique identifier for this file. */
    private final long _id;
    @Override
    public long getId() { return _id; }

    private final long _parentId;
    @Override
    public long getParentId() { return _parentId; }

    private final int _creationTaskIndex;
    @Override
    @JsonIgnore
    public int getCreationTask() { return _creationTaskIndex; }


    @Override
    @JsonIgnore
    public boolean isDerivative() { return Boolean.parseBoolean(_metadata.get(MpfConstants.IS_DERIVATIVE_MEDIA)); }

    private final String _uri;
    @Override
    public String getUri() { return _uri; }


    /** The URI scheme (protocol) associated with the input URI, as obtained from the media resource. */
    private final UriScheme _uriScheme;
    @Override
    public UriScheme getUriScheme() { return _uriScheme; }


    /** The path to the media that components should use. */
    @Override
    @JsonIgnore
    public Path getProcessingPath() {
        return getConvertedMediaPath().orElse(_localPath);
    }

    /** The local file path of the file once it has been retrieved. May be null if the media is not a file, or the file path has not been externally set. */
    private final Path _localPath;
    @Override
    public Path getLocalPath() { return _localPath; }


    /** The path to the media that the JSON output object should use. */
    @Override
    @JsonIgnore
    public String getPersistentUri() {
        return getStorageUri().orElse(_uri);
    }


    /** If the media needed to be converted to another format, this will contain the path to converted media. */
    private Path _convertedMediaPath;
    @Override
    public Optional<Path> getConvertedMediaPath() {
        return Optional.ofNullable(_convertedMediaPath);
    }
    public void setConvertedMediaPath(Path path) {
        _convertedMediaPath = path;
    }


    /** For derivative media, this will contain the URI to the media once placed in storage at the end of a job. */
    private String _storageUri;
    @Override
    public Optional<String> getStorageUri() {
        return Optional.ofNullable(_storageUri);
    }
    public void setStorageUri(String storageUri) {
        _storageUri = storageUri;
    }


    /** A flag indicating if the medium has encountered an error during processing. Will be false if no error occurred. */
    private boolean _failed;
    @Override
    public boolean isFailed() { return _failed; }
    public void setFailed(boolean failed) { _failed = failed; }


    /** A message indicating what error(s) a medium has encountered during processing. Will be null if no error occurred. */
    private String _errorMessage;
    @Override
    public String getErrorMessage() { return _errorMessage; }

    /** The data type of the medium. For example, VIDEO. */
    private MediaType _type;
    @Override
    public Optional<MediaType> getType() {
        return Optional.ofNullable(_type);
    }
    public void setType(MediaType type) { _type = type; }

    @JsonIgnore
    @Override
    public boolean matchesType(MediaType... mediaTypes) {
        return Stream.of(mediaTypes).anyMatch(mt -> mt == _type);
    }

    /** The MIME type of the medium. */
    private String _mimeType;
    @Override
    public Optional<String> getMimeType() {
        if (_mimeType != null) {
            return Optional.of(_mimeType);
        }
        return Optional.ofNullable(getProvidedMetadata().get("MIME_TYPE"))
                .filter(h -> !h.isBlank());
    }
    public void setMimeType(String mimeType) { _mimeType = mimeType; }


    /** The Metadata for the medium. */
    private final Map<String, String> _metadata = new HashMap<>();
    @Override
    public Map<String, String> getMetadata() { return Collections.unmodifiableMap(_metadata); }
    @Override
    public String getMetadata(String key) { return _metadata.get(key); }
    public void addMetadata(Map<String, String> metadata) {
        _metadata.putAll(metadata);
    }
    public void addMetadata(String key, String value) {
        _metadata.put(key, value);
    }


    /** The Algorithm properties to override for the medium. */
    private final ImmutableMap<String, String> _mediaSpecificProperties;
    @Override
    public ImmutableMap<String,String> getMediaSpecificProperties() { return _mediaSpecificProperties; }
    @Override
    public String getMediaSpecificProperty(String key) { return _mediaSpecificProperties.get(key); }

    /** The provided Metadata properties to override for the medium. */
    private final ImmutableMap<String, String> _providedMetadata;

    @Override
    public ImmutableMap<String, String> getProvidedMetadata() { return _providedMetadata; }

    /** The _length of the medium in frames (for images and videos) or milliseconds (for audio). */
    private OptionalInt _length = OptionalInt.empty();
    @Override
    public OptionalInt getLength() { return _length; }
    public void setLength(int length) { _length = OptionalInt.of(length); }

    /** The SHA 256 hash of the local file (assuming it could be retrieved). */
    private String _sha256;
    @Override
    public Optional<String> getSha256() { return Optional.ofNullable(_sha256); }
    public void setSha256(String sha256) { _sha256 = sha256; }

    private FrameTimeInfo _frameTimeInfo;
    @Override
    @JsonIgnore
    public FrameTimeInfo getFrameTimeInfo() { return _frameTimeInfo; }
    public void setFrameTimeInfo(FrameTimeInfo frameTimeInfo) { _frameTimeInfo = frameTimeInfo; }

    private final ImmutableSet<MediaRange> _frameRanges;
    @Override
    public ImmutableSet<MediaRange> getFrameRanges() { return _frameRanges; }

    private final ImmutableSet<MediaRange> _timeRanges;
    @Override
    public ImmutableSet<MediaRange> getTimeRanges() { return _timeRanges; }

    private TiesDbInfo _tiesDbInfo;
    @Override
    public Optional<TiesDbInfo> getTiesDbInfo() {
        return Optional.ofNullable(_tiesDbInfo);
    }
    public void setTiesDbInfo(TiesDbInfo info) {
        _tiesDbInfo = info;
    }

    @Override
    @JsonIgnore
    public Optional<String> getLinkedHash() {
        var linkedHash = _mediaSpecificProperties.get(MpfConstants.LINKED_MEDIA_HASH);
        if (linkedHash != null && !linkedHash.isBlank()) {
            return Optional.of(linkedHash);
        }
        // If LINKED_MEDIA_HASH is missing, then the media is linked to itself.
        if (_sha256 != null) {
            return Optional.of(_sha256);
        }
        return Optional.ofNullable(getProvidedMetadata().get("MEDIA_HASH"))
            .filter(h -> !h.isBlank());
    }


    public MediaImpl(
            long id,
            long parentId,
            int creationTaskIndex,
            String uri,
            UriScheme uriScheme,
            Path localPath,
            Map<String, String> mediaSpecificProperties,
            Map<String, String> providedMetadata,
            Collection<MediaRange> frameRanges,
            Collection<MediaRange> timeRanges,
            String errorMessage) {
        _id = id;
        _parentId = parentId;
        _creationTaskIndex = creationTaskIndex;
        _uri = IoUtils.normalizeUri(uri);
        _uriScheme = uriScheme;
        _localPath = localPath;
        _mediaSpecificProperties = ImmutableMap.copyOf(mediaSpecificProperties);
        _providedMetadata = ImmutableMap.copyOf(providedMetadata);
        _frameRanges = ImmutableSet.copyOf(frameRanges);
        _timeRanges = ImmutableSet.copyOf(timeRanges);

        if (StringUtils.isNotEmpty(errorMessage)) {
            _errorMessage = createErrorMessage(id, uri, errorMessage);
            _failed = true;
        }
    }

    public MediaImpl(
            long id,
            String uri,
            UriScheme uriScheme,
            Path localPath,
            Map<String, String> mediaSpecificProperties,
            Map<String, String> providedMetadata,
            Collection<MediaRange> frameRanges,
            Collection<MediaRange> timeRanges,
            String errorMessage) {
        this(id, -1, -1, uri, uriScheme, localPath, mediaSpecificProperties, providedMetadata, frameRanges, timeRanges,
                errorMessage);
    }


    @JsonCreator
    public MediaImpl(
            @JsonProperty("id") long id,
            @JsonProperty("parentId") long parentId,
            @JsonProperty("creationTaskIndex") int creationTaskIndex,
            @JsonProperty("uri") String uri,
            @JsonProperty("uriScheme") UriScheme uriScheme,
            @JsonProperty("localPath") Path localPath,
            @JsonProperty("mediaSpecificProperties") Map<String, String> mediaSpecificProperties,
            @JsonProperty("providedMetadata") Map<String, String> providedMetadata,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("metadata") Map<String, String> metadata,
            @JsonProperty("frameRanges") Collection<MediaRange> frameRanges,
            @JsonProperty("timeRanges") Collection<MediaRange> timeRanges,
            @JsonProperty("tiesDbInfo") TiesDbInfo tiesDbInfo) {
        this(id,
             parentId,
             creationTaskIndex,
             uri,
             uriScheme,
             localPath,
             mediaSpecificProperties,
             providedMetadata,
             frameRanges,
             timeRanges,
             errorMessage);
        if (metadata != null) {
            _metadata.putAll(metadata);
        }
        _tiesDbInfo = tiesDbInfo;
    }


    public static MediaImpl toMediaImpl(Media originalMedia) {
        if (originalMedia instanceof MediaImpl) {
            return (MediaImpl) originalMedia;
        }

        MediaImpl result = new MediaImpl(
                originalMedia.getId(),
                originalMedia.getParentId(),
                originalMedia.getCreationTask(),
                originalMedia.getUri(),
                originalMedia.getUriScheme(),
                originalMedia.getLocalPath(),
                originalMedia.getMediaSpecificProperties(),
                originalMedia.getProvidedMetadata(),
                originalMedia.getFrameRanges(),
                originalMedia.getTimeRanges(),
                originalMedia.getErrorMessage());

        result.setFailed(originalMedia.isFailed());
        result.setType(originalMedia.getType().orElse(null));
        originalMedia.getLength().ifPresent(result::setLength);
        result.setSha256(originalMedia.getSha256().orElse(null));
        result.addMetadata(originalMedia.getMetadata());

        originalMedia.getConvertedMediaPath().ifPresent(result::setConvertedMediaPath);
        originalMedia.getStorageUri().ifPresent(result::setStorageUri);

        return result;
    }


    private static String createErrorMessage(long id, String uri, String genericError) {
        return String.format("An error occurred while processing media with id %s and uri %s : %s", id, uri,
                             genericError);
    }


    @Override
    public String toString() {
        return String.format("%s#<id=%d, parentId=%d, uri='%s', uriScheme='%s', localPath='%s', failed=%s, errorMessage='%s', type='%s', length=%s, sha256='%s'>",
                             getClass().getSimpleName(),
                             _id,
                             _parentId,
                             _uri,
                             _uriScheme,
                             _localPath,
                             _failed,
                             _errorMessage,
                             _type,
                             _length,
                             _sha256);
    }
}
