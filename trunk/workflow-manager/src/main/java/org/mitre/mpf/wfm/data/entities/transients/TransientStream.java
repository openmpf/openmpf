/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.UriScheme;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.joining;

/** transient stream data. Note that currently, only the RTSP and HTTP protocols for streams is currently supported */
public class TransientStream {

	private static final Set<UriScheme> SUPPORTED_URI_SCHEMES = EnumSet.of(
			UriScheme.RTSP, UriScheme.HTTP, UriScheme.HTTPS);

    /** The unique identifier for this stream. */
	private final long _id;
	public long getId() { return _id; }

	/** The stream resource used to construct this transient stream, Error may occur if the URI of the stream isn't a OpenMPF supported protocol. */
	private final String _uri;
	public String getUri() { return _uri; }

	/** The URI scheme (protocol) associated with the input stream URI. */
	private final UriScheme _uriScheme;
	public UriScheme getUriScheme() { return _uriScheme; }

	/** A flag indicating if the medium has encountered an error during processing. */
	private final boolean _failed;
	public boolean isFailed() { return _failed; }

	/** A _message indicating what error(s) a medium has encountered during processing. */
	private final String _message;
	public String getMessage() { return _message; }


	/** The Metadata for the stream. */
	private final ImmutableMap<String, String> _metadata;
	public ImmutableMap<String, String> getMetadata() { return _metadata; }
	public String getMetadata(String key) { return _metadata.get(key); }

	/** The media properties to override for this stream. */
	private final ImmutableMap<String, String> _mediaProperties;
	public ImmutableMap<String, String> getMediaProperties() { return _mediaProperties; }
	public String getMediaProperty(String key) { return _mediaProperties.get(key); }

	private final int _segmentSize;
	public int getSegmentSize() { return _segmentSize; }

	public MediaType getMediaType() { return MediaType.VIDEO; }


	public TransientStream(long id, String uri, int segmentSize, Map<String, String> mediaProperties) {
	    this(id, uri, segmentSize, mediaProperties, ImmutableMap.of());
	}


	public TransientStream(long id, String uri, int segmentSize, Map<String, String> mediaProperties,
	                       Map<String, String> mediaMetadata) {
		_id = id;
		_uri = uri;

		URI uriInstance = null;
		String uriError = null;
		try {
			uriInstance = new URI(uri);
		}
		catch (URISyntaxException e) {
			uriError = e.getMessage();
		}

		if (uriInstance != null) {
			_uriScheme = UriScheme.get(uriInstance);
			_failed = !SUPPORTED_URI_SCHEMES.contains(_uriScheme);
			if (_failed) {
				String supportedSchemesString = SUPPORTED_URI_SCHEMES.stream()
						.map(Enum::name)
						.collect(joining(", "));
				_message = String.format(
						"URI scheme \"%s\" in not valid for a streaming jobs. Only the following schemes are support for streaming video: %s",
						_uriScheme, supportedSchemesString);
			}
			else {
				_message = null;
			}
		}
		else {
			_uriScheme = UriScheme.UNDEFINED;
			_message = uriError;
			_failed = true;
		}

		_segmentSize = segmentSize;
		_mediaProperties = ImmutableMap.copyOf(mediaProperties);
		_metadata = ImmutableMap.copyOf(mediaMetadata);
	}


	@Override
	public String toString() {
		return String.format("%s#<id=%d, uri='%s', uriScheme='%s', failed=%s, message='%s'>",
				getClass().getSimpleName(), _id, getUri(), getUriScheme(), _failed, _message);
	}

	/** Check to see if the specified URI is correctly defined and is one of the supported stream protocols.
	 * @return true if the URI scheme is well defined and is one of the supported stream protocols, false otherwise.
	 */
    public static boolean isSupportedUriScheme(String uri) {
	    try {
		    return isSupportedUriScheme(UriScheme.get(new URI(uri)));
	    }
	    catch (URISyntaxException e) {
            return false;
	    }
    }

    public static boolean isSupportedUriScheme(UriScheme scheme) {
        return SUPPORTED_URI_SCHEMES.contains(scheme);
    }
}
