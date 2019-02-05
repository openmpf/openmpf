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

package org.mitre.mpf.wfm.data.entities.transients;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.MediaTypeUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** An in-flight media instance. */
public class TransientMediaImpl implements TransientMedia {

	/** The unique identifier for this file. */
	private final long _id;
	@Override
	public long getId() { return _id; }


    private final String _uri;
	@Override
	public String getUri() { return _uri; }


	/** The URI scheme (protocol) associated with the input URI, as obtained from the media resource. */
	private final UriScheme _uriScheme;
	@Override
	public UriScheme getUriScheme() { return _uriScheme; }


	/** The local file path of the file once it has been retrieved. May be null if the media is not a file, or the file path has not been externally set. */
	private final Path _localPath;
	@Override
	public Path getLocalPath() { return _localPath; }


	/** A flag indicating if the medium has encountered an error during processing. Will be false if no error occurred. */
	private boolean _failed;
	@Override
	public boolean isFailed() { return _failed; }
	public void setFailed(boolean failed) { _failed = failed; }


	/** A message indicating what error(s) a medium has encountered during processing. Will be null if no error occurred. */
	private String _message;
	@Override
	public String getMessage() { return _message; }
	public void setMessage(String message) { _message = message; }

	/** The MIME type of the medium. */
	private String _type;
	@Override
	public String getType() { return _type; }
	public void setType(String type) { _type = type; }

	@Override
	public MediaType getMediaType() {
		return MediaTypeUtils.parse(_type);
	}


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


	/** The _length of the medium in frames (for images and videos) or milliseconds (for audio). */
	private int _length;
	@Override
	public int getLength() { return _length; }
	public void setLength(int length) { _length = length; }

	/** The SHA 256 hash of the local file (assuming it could be retrieved. */
	private String _sha256;
	@Override
	public String getSha256() { return _sha256; }
	public void setSha256(String sha256) { _sha256 = sha256; }


    public TransientMediaImpl(long id, String uri, UriScheme uriScheme, Path localPath,
                              Map<String, String> mediaSpecificProperties,
                              String errorMessage) {
    	_id = id;
    	_uri = uri;
    	_uriScheme = uriScheme;
    	_localPath = localPath;
    	_mediaSpecificProperties = ImmutableMap.copyOf(mediaSpecificProperties);
    	if (StringUtils.isNotEmpty(errorMessage)) {
		    _message = createErrorMessage(id, uri, errorMessage);
	    }
    }


    public static TransientMediaImpl toTransientMediaImpl(TransientMedia originalMedia) {
    	if (originalMedia instanceof TransientMediaImpl) {
    		return (TransientMediaImpl) originalMedia;
	    }

	    TransientMediaImpl result = new TransientMediaImpl(
			    originalMedia.getId(), originalMedia.getUri(), originalMedia.getUriScheme(),
			    originalMedia.getLocalPath(), originalMedia.getMediaSpecificProperties(),
                originalMedia.getMessage());

    	result.setFailed(originalMedia.isFailed());
    	result.setType(originalMedia.getType());
    	result.setLength(originalMedia.getLength());
    	result.setSha256(originalMedia.getSha256());
    	return result;
    }


    private static String createErrorMessage(long id, String uri, String genericError) {
		return String.format("An error occurred while processing media with id \"%s\" and uri \"%s\": %s", id, uri,
		                     genericError);
    }


	@Override
	public String toString() {
		return String.format("%s#<id=%d, uri='%s', uriScheme='%s', localPath='%s', failed=%s, message='%s', type='%s', length=%d, sha256='%s'>",
				getClass().getSimpleName(),
                _id,
                _uri,
                _uriScheme,
                _localPath,
                _failed,
                _message,
                _type,
                _length,
                _sha256);
	}

}
