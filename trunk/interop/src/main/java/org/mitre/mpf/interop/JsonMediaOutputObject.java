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

package org.mitre.mpf.interop;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

@JsonTypeName("MarkupOutputObject")
@JsonPropertyOrder({"mediaId", "path", "detectionProcessingErrors"})
public class JsonMediaOutputObject implements Comparable<JsonMediaOutputObject> {

	@JsonProperty("mediaId")
	@JsonPropertyDescription("An internal identifier assigned to this media file. The value of this identifier may be useful when debugging system errors.")
	private long mediaId;
	public long getMediaId() { return mediaId; }

	@JsonProperty("path")
	@JsonPropertyDescription("The URI to this media file.")
	private String path;
	public String getPath() { return path; }

	@JsonProperty("mimeType")
	@JsonPropertyDescription("The MIME type associated with this media file.")
	private String mimeType;
	public String getMimeType() { return mimeType; }

	@JsonProperty("length")
	@JsonPropertyDescription("The length of this medium. The meaning of this value depends on the context. For image files, the length is undefined. For video files, the length is the number of frames in the video. For audio files, the length is undefined.")
	private int length;
	public int getLength() { return length; }

	@JsonProperty("status")
	@JsonPropertyDescription("A summary status indicating the system's ability to process the file. The expected value is COMPLETE.")
	private String status;
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }

	@JsonProperty("message")
	@JsonPropertyDescription("A brief message which may include additional details regarding the system's ability to process this medium.")
	private String message;
	public String getMessage() { return message; }

	@JsonProperty("sha256")
	@JsonPropertyDescription("The SHA-256 cryptographic hash value for this medium.")
	private String sha256;
	public String getSha256() { return sha256; }

	@JsonProperty("markupResult")
	@JsonPropertyDescription("The OPTIONAL markup result produced for this medium.")
	private JsonMarkupOutputObject markupResult;
	public JsonMarkupOutputObject getMarkupResult() { return markupResult; }
	public void setMarkupResult(JsonMarkupOutputObject markupResult) { this.markupResult = markupResult; }

	@JsonProperty("mediaMetadata")
	@JsonPropertyDescription("A map of properties read from the given medium.")
	private SortedMap<String, String> mediaMetadata;
	public SortedMap<String, String> getMediaMetadata() { return mediaMetadata; }

	@JsonProperty("mediaProperties")
	@JsonPropertyDescription("A map of medium-specific properties that override algorithm properties.")
	private SortedMap<String, String> mediaProperties;
	public SortedMap<String, String> getMediaProperties() { return mediaProperties; }

	@JsonProperty("output")
	@JsonPropertyDescription("The mapping of action type keys to a set of actions performed on the given medium.")
	private SortedMap<String, SortedSet<JsonActionOutputObject>> types;
	public SortedMap<String, SortedSet<JsonActionOutputObject>> getTypes() { return types; }

	@JsonProperty("detectionProcessingErrors")
	@JsonPropertyDescription("The mapping of action state keys to detection errors produced in that action for the given medium.")
	private SortedMap<String, SortedSet<JsonDetectionProcessingError>> detectionProcessingErrors;
	public SortedMap<String, SortedSet<JsonDetectionProcessingError>> getDetectionProcessingErrors() { return detectionProcessingErrors; }

	public JsonMediaOutputObject(long mediaId, String path, String mimeType, int length, String sha256, String message,
								 String status) {
		this.mediaId = mediaId;
		this.path = path;
		this.mimeType = mimeType;
		this.length = length;
		this.sha256 = sha256;
		this.message = message;
		this.status = status;
		this.types = new TreeMap<>();
		this.detectionProcessingErrors = new TreeMap<>();
		this.mediaMetadata = new TreeMap<>();
		this.mediaProperties = new TreeMap<>();
	}

	public JsonMediaOutputObject(){}

	@JsonCreator
	public static JsonMediaOutputObject factory(@JsonProperty("mediaId") long mediaId,
												@JsonProperty("path") String path,
												@JsonProperty("mimeType") String mimeType,
												@JsonProperty("length") int length,
												@JsonProperty("sha256") String sha256,
												@JsonProperty("message") String message,
												@JsonProperty("status") String status,
												@JsonProperty("mediaMetadata") SortedMap<String, String> mediaMetadata,
												@JsonProperty("mediaProperties") SortedMap<String, String> mediaProperties,
												@JsonProperty("markupResult") JsonMarkupOutputObject markupResult,
												@JsonProperty("output") SortedMap<String, SortedSet<JsonActionOutputObject>> types,
												@JsonProperty("detectionProcessingErrors") SortedMap<String, SortedSet<JsonDetectionProcessingError>> detectionProcessingErrors) {
		JsonMediaOutputObject jsonMediaOutputObject = new JsonMediaOutputObject(mediaId, path, mimeType, length, sha256, message, status);
		jsonMediaOutputObject.markupResult = markupResult;

		if(mediaMetadata != null) {
			jsonMediaOutputObject.mediaMetadata.putAll(mediaMetadata);
		}

		if(mediaProperties != null) {
			jsonMediaOutputObject.mediaProperties.putAll(mediaProperties);
		}

		if(types != null) {
			jsonMediaOutputObject.types.putAll(types);
		}

		if(detectionProcessingErrors != null) {
			jsonMediaOutputObject.detectionProcessingErrors.putAll(detectionProcessingErrors);
		}

		return jsonMediaOutputObject;
	}

	public int hashCode() {
		return (int)(mediaId * 37);
	}

	public boolean equals(Object other) {
		if(other == null || !(other instanceof JsonMediaOutputObject)) {
			return false;
		} else {
			JsonMediaOutputObject casted = (JsonMediaOutputObject)other;
			return mediaId == casted.mediaId &&
					StringUtils.equals(path, casted.path) &&
					length == casted.length &&
					StringUtils.equals(sha256, casted.sha256) &&
					StringUtils.equals(message, casted.message) &&
					StringUtils.equals(status, casted.status) &&
					StringUtils.equals(mimeType, casted.mimeType);
		}
	}

	@Override
	public int compareTo(JsonMediaOutputObject other) {
		int result = 0;
		if(other == null) {
			return 1;
		} else if ((result = ObjectUtils.compare(path, other.path)) != 0
				|| (result = ObjectUtils.compare(mediaId, other.mediaId)) != 0
				|| (result = ObjectUtils.compare(sha256, other.sha256)) != 0
				|| (result = ObjectUtils.compare(length, other.length)) != 0
				|| (result = ObjectUtils.compare(message, other.message)) != 0
				|| (result = ObjectUtils.compare(status, other.status)) != 0
				|| (result = ObjectUtils.compare(mimeType, other.mimeType)) != 0) {
			return result;
		} else {
			return 0;
		}
	}
}