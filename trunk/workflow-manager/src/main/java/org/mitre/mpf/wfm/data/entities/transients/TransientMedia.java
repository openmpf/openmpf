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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.MediaResource;
import org.mitre.mpf.wfm.util.MediaTypeUtils;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

/** An in-flight media instance. */
public class TransientMedia {

	@Autowired
	private MediaTypeUtils mediaTypeUtils;

	/** The unique identifier for this file. */
	private long id;
	public long getId() { return id; }

	private MediaResource mediaResource = null;
	public String getUri() { return mediaResource.getUri(); }

//	/** Identify the URI of the transient media. Note that OpenMPF provides support for protocols as
//     * listed by {@link #isSupportedMediaUriScheme}.  If the protocol is not supported, the message from this
//     * transient media will be that the URI scheme is not supported.
//	 * @param uri The URI of the source file which may use the file, http, https, or other protocol
//	 */
//	private void setUri(String uri) {
//        mediaResource = new MediaResource(uri);
//        if ( !mediaResource.isValidResource() ) {
//            failed = true;
//            message = mediaResource.getResourceStatusMessage();
//        } else if ( !isSupportedMediaUriScheme() ) {
//            failed = true;
//            setMessage(MediaResource.NOT_SUPPORTED_URI_SCHEME);
//         }
//	}

	/** The URI scheme (protocol) associated with the input URI. */
	// djvp: added JsonIgnore to uriScheme to avoid tomcat error 9/14/17
	@JsonIgnore
	public UriScheme getUriScheme() { return mediaResource == null ? UriScheme.UNDEFINED : mediaResource.getUriScheme(); }

	/** The local file path of the file once it has been retrieved. May be null if the media is not a file, or the file path has not been externally set. */
	public String getLocalPath() { return mediaResource.getLocalFilePath(); }
	public void setLocalPath(String localPath) { mediaResource.setLocalFilePath(localPath); }

	/** A flag indicating if the medium has encountered an error during processing. Will be false if no error occurred. */
	private boolean failed = false;
	public boolean isFailed() { return failed; }
	public void setFailed(boolean failed) { this.failed = failed; }

	/** A message indicating what error(s) a medium has encountered during processing. Will be null if no error occurred. */
	private String message = null;
	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }

	/** The MIME type of the medium. */
	private String type;
	public String getType() { return type; }
	public void setType(String type) { this.type = type; }

	/** The Metadata for the medium. */
	private Map<String,String> metadata = new HashMap<>();
	public String addMetadata(String key, String value) {
		return metadata.put(key,value);
	}
	public Map<String,String> getMetadata() { return metadata; }
	public String getMetadata(String key) { return metadata.get(key); }

	/** The Algorithm properties to override for the medium. */
	private Map<String,String> mediaSpecificProperties = new HashMap<>();
	public String addMediaSpecificProperty(String key, String value) {
		return mediaSpecificProperties.put(key,value);
	}
	public Map<String,String> getMediaSpecificProperties() { return mediaSpecificProperties; }
	public String getMediaSpecificProperty(String key) { return mediaSpecificProperties.get(key); }

	@JsonIgnore
	public MediaType getMediaType() { return MediaTypeUtils.parse(type); }

	/** The length of the medium in frames (for images and videos) or milliseconds (for audio). */
	private int length;
	public int getLength() { return length; }
	public void setLength(int length) { this.length = length; }

	/** The number of frames per second for video files */
	private float fps;
	public float getFps() { return fps; }
	public void setFps(float fps) { this.fps = fps; }

	/** The SHA 256 hash of the local file (assuming it could be retrieved. */
	private String sha256;
	public String getSha256() { return sha256; }
	public void setSha256(String sha256) { this.sha256 = sha256; }

//
//     uriScheme no longer needs to be passed to the constructor, but when uriScheme is not provided in the @JsonCreator constructor we get an
//     Caused by: com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException: Unrecognized field "uriScheme" (class org.mitre.mpf.wfm.data.entities.transients.TransientMedia), not marked as ignorable (11 known properties: "localPath", "length", "message", "mediaSpecificProperties", "type", "id", "uri", "failed", "metadata", "sha256", "fps"])
//     at [Source: [B@461f7ae0; line: -1, column: 297] (through reference chain: org.mitre.mpf.wfm.data.entities.transients.TransientMedia["uriScheme"])
//     that seems to be triggered from org.mitre.mpf.wfm.data.RedisImpl.getJob(RedisImpl.java:450).  Adding @JsonIgnoreProperties(ignoreUnknown = true)
//     annotation to this class seems to take care of this issue.  Is this acceptable?  If not, assistance is requested in trying to find where
//     to better clean this up.  Tried flushing REDIS (i.e. redis-cli flushall) but that did not work djvp
//
//    /** JSON constructor, this legacy version of the constructor seems to be required for use by Camel.
//     * @param id media id
//     * @param uri URI of the media
//     * @param uriScheme UriScheme constructed by the URI
//     */
//	@JsonCreator
//    public TransientMedia(@JsonProperty("id") long id, @JsonProperty("uri") String uri, @JsonProperty("uriScheme") UriScheme uriScheme) {
//        this.id = id;
//        this.mediaResource = new MediaResource(uri,uriScheme);
//    }

    /** JSON constructor
     * @param id unique media id
     * @param uri URI of the media
     */
    @JsonCreator
	public TransientMedia(@JsonProperty("id") long id, @JsonProperty("uri") String uri) {
        this.id = id;
		this.mediaResource = new MediaResource(uri);
	}

	public String toString() {
		return String.format("%s#<id=%d, uri='%s', uriScheme='%s', localPath='%s', failed=%s, message='%s', type='%s', length=%d, sha256='%s'>",
				this.getClass().getSimpleName(),
				id,
				mediaResource.getUri(),
				mediaResource.getUriScheme(),
				mediaResource.getLocalFilePath(),
				Boolean.toString(failed),
				message,
				type,
				length,
				sha256);
	}

    /** Check to see if the URI scheme for this transient media is one of the media protocols supported by OpenMPF.
     * OpenMPF supports the file, http, https, or other protocol for transient media
     * @return true if the URI scheme for this transient media is one of the supported media protocols, false otherwise.
     */
	@JsonIgnore
    public boolean isSupportedMediaUriScheme() {
        return mediaResource != null && mediaResource.isSupportedUriScheme();
    }

}
