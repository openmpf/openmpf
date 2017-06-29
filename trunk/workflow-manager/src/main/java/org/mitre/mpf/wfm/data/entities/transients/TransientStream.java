/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.data.entities.transients;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.MediaTypeUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** transient stream data. Note that currently, only the RTSP protocol for streams is supported */
public class TransientStream {

	/** The unique identifier for this stream. */
	private long id;
	public long getId() { return id; }

	/** The URI of the source stream, currently only supporting the RTSP protocol. */
	private String uri;
	public String getUri() { return uri; }
	private void setUri(String uri) {
		this.uri = uri;
        try {
            URI uriInstance = new URI(uri);
            this.uriScheme = UriScheme.parse(uriInstance.getScheme());
            if ( uriScheme != UriScheme.RTSP ) {
                failed = true;
                message = "URI scheme "+uriScheme+" is not supported, currently only supporting RTSP.";
            }
        } catch ( URISyntaxException use ) {
            uriScheme = UriScheme.UNDEFINED;
            failed = true;
            message = use.getMessage();
        }
	}

	/** The URI scheme (protocol) associated with the input stream URI. */
	private UriScheme uriScheme = null;
	public UriScheme getUriScheme() { return uriScheme == null ? UriScheme.UNDEFINED : uriScheme; }

	/** A flag indicating if the medium has encountered an error during processing. */
	private boolean failed;
	public boolean isFailed() { return failed; }
	public void setFailed(boolean failed) { this.failed = failed; }

	/** A message indicating what error(s) a medium has encountered during processing. */
	private String message;
	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }

	/** The MIME type of the medium. */
	private String type;
	public String getType() { return type; }
	public void setType(String type) { this.type = type; }

	/** The Metadata for the stream. */
	private Map<String,String> metadata = new HashMap<>();
	public String addMetadata(String key, String value) {
		return metadata.put(key, value);
	}
	public Map<String,String> getMetadata() { return metadata; }
	public String getMetadata(String key) { return metadata.get(key); }

	/** The media properties to override for this stream. */
	private Map<String,String> mediaProperties = new HashMap<>();
	public void setMediaProperties (Map<String,String> updated_media_properties) {
		mediaProperties = updated_media_properties;
	}
	public String addMediaProperty(String key, String value) {
		return mediaProperties.put(key, value);
	}
	public Map<String,String> getMediaProperties() { return mediaProperties; }
	public String getMediaProperty(String key) { return mediaProperties.get(key); }

	private int segmentSize;
	public void setSegmentSize(int segment_size) { segmentSize = segment_size; }
	public int getSegmentSize() { return segmentSize; }


	@JsonIgnore
	public MediaType getMediaType() { return MediaTypeUtils.parse(type); }

	/** The SHA 256 hash of the local file (assuming it could be retrieved. */
	private String sha256;
	public String getSha256() { return sha256; }
	public void setSha256(String sha256) { this.sha256 = sha256; }

	public TransientStream(long id, String uri) {
		this.id = id;
		setUri(uri);
	}

	@JsonCreator
	public TransientStream(@JsonProperty("id") long id, @JsonProperty("uri") String uri, @JsonProperty("uriScheme") UriScheme uriScheme) {
		this.id = id;
		this.uri = uri;
		this.uriScheme = uriScheme;
	}

	public String toString() {
		return String.format("%s#<id=%d, uri='%s', uriScheme='%s', failed=%s, message='%s', type='%s', sha256='%s'>",
				this.getClass().getSimpleName(),
				id,
				uri,
				uriScheme,
				Boolean.toString(failed),
				message,
				type,
				sha256);
	}

}
