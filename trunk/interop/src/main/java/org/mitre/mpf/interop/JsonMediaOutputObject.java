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

package org.mitre.mpf.interop;

import com.fasterxml.jackson.annotation.*;

import java.util.*;

import static java.util.Comparator.comparingLong;
import static org.mitre.mpf.interop.util.CompareUtils.stringCompare;

@JsonTypeName("MediaOutputObject")
@JsonPropertyOrder({"mediaId", "parentMediaId", "path", "sha256", "mimeType", "mediaType", "length", "mediaMetadata",
		            "mediaProperties", "status", "detectionProcessingErrors", "markupResult", "output"})
public class JsonMediaOutputObject implements Comparable<JsonMediaOutputObject> {

	@JsonProperty("mediaId")
	@JsonPropertyDescription("An internal identifier assigned to this media file. The value of this identifier may be useful when debugging system errors.")
	private long mediaId;
	public long getMediaId() { return mediaId; }

	@JsonProperty("parentMediaId")
	@JsonPropertyDescription("If this is derivative media, the id of the source media it was derived from. Set to -1 for non-derivative media.")
	private long parentMediaId;
	public long getParentMediaId() { return parentMediaId; }

	@JsonProperty("path")
	@JsonPropertyDescription("The URI to this media file.")
	private String path;
	public String getPath() { return path; }

	@JsonProperty("mediaType")
	@JsonPropertyDescription("The type associated with this media file. For example, \"VIDEO\".")
	private String type;
	public String getType() { return type; }

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
	@JsonPropertyDescription("The mapping of detection types to a set of actions performed on the given medium.")
	private SortedMap<String, SortedSet<JsonActionOutputObject>> detectionTypes;
	public SortedMap<String, SortedSet<JsonActionOutputObject>> getDetectionTypes() { return detectionTypes; }

	@JsonProperty("detectionProcessingErrors")
	@JsonPropertyDescription("The mapping of action state keys to detection errors produced in that action for the given medium.")
	private SortedMap<String, SortedSet<JsonDetectionProcessingError>> detectionProcessingErrors;
	public SortedMap<String, SortedSet<JsonDetectionProcessingError>> getDetectionProcessingErrors() { return detectionProcessingErrors; }

	public JsonMediaOutputObject(long mediaId, long parentMediaId, String path, String mediaType, String mimeType,
								 int length, String sha256, String status) {
		this.mediaId = mediaId;
		this.parentMediaId = parentMediaId;
		this.path = path;
		this.type = mediaType;
		this.mimeType = mimeType;
		this.length = length;
		this.sha256 = sha256;
		this.status = status;
		this.detectionTypes = new TreeMap<>(new DetectionTypeComparator());
		this.detectionProcessingErrors = new TreeMap<>();
		this.mediaMetadata = new TreeMap<>();
		this.mediaProperties = new TreeMap<>();
	}

	public JsonMediaOutputObject(){}

	@JsonCreator
	public static JsonMediaOutputObject factory(@JsonProperty("mediaId") long mediaId,
												@JsonProperty("parentMediaId") long parentMediaId,
												@JsonProperty("path") String path,
												@JsonProperty("mediaType") String mediaType,
												@JsonProperty("mimeType") String mimeType,
												@JsonProperty("length") int length,
												@JsonProperty("sha256") String sha256,
												@JsonProperty("status") String status,
												@JsonProperty("mediaMetadata") SortedMap<String, String> mediaMetadata,
												@JsonProperty("mediaProperties") SortedMap<String, String> mediaProperties,
												@JsonProperty("markupResult") JsonMarkupOutputObject markupResult,
												@JsonProperty("output") SortedMap<String, SortedSet<JsonActionOutputObject>> detectionTypes,
												@JsonProperty("detectionProcessingErrors") SortedMap<String, SortedSet<JsonDetectionProcessingError>> detectionProcessingErrors) {
		JsonMediaOutputObject jsonMediaOutputObject =
				new JsonMediaOutputObject(mediaId, parentMediaId, path, mediaType, mimeType, length, sha256, status);
		jsonMediaOutputObject.markupResult = markupResult;

		if(mediaMetadata != null) {
			jsonMediaOutputObject.mediaMetadata.putAll(mediaMetadata);
		}

		if(mediaProperties != null) {
			jsonMediaOutputObject.mediaProperties.putAll(mediaProperties);
		}

		if(detectionTypes != null) {
			jsonMediaOutputObject.detectionTypes.putAll(detectionTypes);
		}

		if(detectionProcessingErrors != null) {
			jsonMediaOutputObject.detectionProcessingErrors.putAll(detectionProcessingErrors);
		}

		return jsonMediaOutputObject;
	}

	@Override
	public int hashCode() {
		return Objects.hash(mediaId, parentMediaId, path, sha256, length, status, type, mimeType);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof JsonMediaOutputObject && compareTo((JsonMediaOutputObject) obj) == 0;
	}

	private static final Comparator<JsonMediaOutputObject> DEFAULT_COMPARATOR = Comparator
			.nullsFirst(
					comparingLong(JsonMediaOutputObject::getMediaId)
					.thenComparingInt(JsonMediaOutputObject::getParentMediaId)
					.thenComparing(stringCompare(JsonMediaOutputObject::getPath))
					.thenComparing(stringCompare(JsonMediaOutputObject::getSha256))
					.thenComparingInt(JsonMediaOutputObject::getLength)
					.thenComparing(stringCompare(JsonMediaOutputObject::getStatus))
					.thenComparing(stringCompare(JsonMediaOutputObject::getType))
					.thenComparing(stringCompare(JsonMediaOutputObject::getMimeType)));

	@Override
	public int compareTo(JsonMediaOutputObject other) {
		//noinspection ObjectEquality - False positive
		if (this == other) {
			return 0;
		}
		return DEFAULT_COMPARATOR.compare(this, other);
	}


	private static class DetectionTypeComparator implements Comparator<String> {
		private static final Set<String> _special = new HashSet<>(Arrays.asList(
				JsonActionOutputObject.NO_TRACKS_TYPE,
				JsonActionOutputObject.TRACKS_MERGED_TYPE,
				JsonActionOutputObject.TRACKS_SUPPRESSED_TYPE));

		@Override
		public int compare(String left, String right) {
			boolean leftIsSpecial = _special.contains(left);
			boolean rightIsSpecial = _special.contains(right);
			if (leftIsSpecial == rightIsSpecial) {
				return stringCompare().compare(left, right);
			}
			return leftIsSpecial ? -1 : 1;
		}
	}
}
