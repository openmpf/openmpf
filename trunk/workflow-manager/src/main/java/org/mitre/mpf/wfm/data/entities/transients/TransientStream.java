/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.util.StreamResource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** transient stream data. Note that currently, only the RTSP and HTTP protocols for streams is currently supported */
public class TransientStream {

    /** The unique identifier for this stream. */
	private long id;
	public long getId() { return id; }

	/** The stream resource used to construct this transient stream, Error may occur if the URI of the stream isn't a OpenMPF supported protocol. */
	private StreamResource streamResource = null;
	public String getUri() { return streamResource.getUri(); }

	/** The URI scheme (protocol) associated with the input stream URI. */
	public UriScheme getUriScheme() { return streamResource == null ? UriScheme.UNDEFINED : streamResource.getUriScheme(); }

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
	public MediaType getMediaType() { return MediaType.VIDEO; }

	/** Constructor of a transient stream.
	 * @param id unique identifier for this stream.
	 * @param uri URI for this stream.
	 */
	@JsonCreator
	public TransientStream(@JsonProperty("id") long id, @JsonProperty("uri") String uri) {
		this.id = id;
        streamResource = new StreamResource(uri);

        assert streamResource != null : "Stream resource must not be null, check construction for id="+id+" and uri="+uri;

        if ( !isSupportedUriScheme() ) {
            failed = true;
            message = "URI scheme " + streamResource.getUriScheme() + " is not valid for stream, error is "+streamResource.getResourceStatusMessage()+".  Check OpenMPF documentation for the list of supported protocols.";
        }
	}

	/** Deep level equality test
	 * @param other other stream to check against
	 * @return true if two TransientStreams are equal when all properties of the TransientStream are compared, false otherwise
	 */
	public boolean equalsAllFields(Object other) {
		if ( other instanceof TransientStream ) {
			TransientStream otherTransientStream = (TransientStream) other;
			return ( id == otherTransientStream.id && getUri().equals(otherTransientStream.getUri()) &&
					getUriScheme() == otherTransientStream.getUriScheme() &&
					failed == otherTransientStream.failed &&
					( ( message == null && otherTransientStream.message == null ) ||
							message.equals(otherTransientStream.message) ) &&
					metadata.equals(otherTransientStream.metadata) &&
					mediaProperties.equals(otherTransientStream.mediaProperties) &&
					segmentSize == otherTransientStream.segmentSize &&
					type == otherTransientStream.type );
		} else {
			return false;
		}
	}

	@Override
	/** Equality test that only checks the unique id of the TransientStreams
	 * @param other stream to check against
	 * @return true if two TransientStream ids are equal, false otherwise
	 */
	public boolean equals(Object other) {
		if ( other instanceof TransientStream ) {
			TransientStream otherTransientStream = (TransientStream) other;
			return ( id == otherTransientStream.id);
		} else {
			return false;
		}
	}

	@Override
	/** override of equals method requires override of hashCode method
	 * @return hashcode for this TransientStream
	 */
	public int hashCode() {
		return Objects.hash(id);
	}

	public String toString() {
		return String.format("%s#<id=%d, uri='%s', uriScheme='%s', failed=%s, message='%s', type='%s'>",
				this.getClass().getSimpleName(),
				id,
				getUri(),
				getUriScheme(),
				Boolean.toString(failed),
				message,
				type);
	}

	/** Check to see if the specified URI is correctly defined and is one of the supported stream protocols.
	 * OpenMPF currently only supports the RTSP and HTTP protocols for streams.
	 * @return true if the URI scheme is well defined and is one of the supported stream protocols, false otherwise.
	 */
	@JsonIgnore
	public boolean isSupportedUriScheme() {
		if ( streamResource != null && streamResource.isDefinedUriScheme() && streamResource.isSupportedUriScheme() ) {
			return true;
		} else {
			return false;
		}
	}

}
