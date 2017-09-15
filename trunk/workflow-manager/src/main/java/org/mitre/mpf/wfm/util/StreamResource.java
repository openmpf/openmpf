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

package org.mitre.mpf.wfm.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.URISyntaxException;
import org.mitre.mpf.wfm.enums.UriScheme;

/**
 * Contains properties used to identify a stream.
 */
public class StreamResource {

    public static final String NO_ERROR = "NO ERROR";
    public static final String NOT_DEFINED_URI_SCHEME = "URI scheme not defined";
    public static final String NOT_SUPPORTED_URI_SCHEME = "Unsupported URI scheme";

    private String uri;
    private String resourceStatusMessage = null;

    /** The URI associated with this stream.
     * @return The URI associated with this stream.
     */
    public String getUri() { return uri; }

    private UriScheme uriScheme = null;
    /** The URI scheme (protocol) associated with this stream resource, may include the file, http, https, or some other protocol.
     * @return The URI scheme (protocol) associated with this stream resource.
     */
    public UriScheme getUriScheme() {return this.uriScheme;}

    /** Check to see if this is a correctly defined stream resource.
     * @return true if this stream resource is correctly constructed, false otherwise.
     */
    public boolean isDefinedUriScheme() { return uriScheme == UriScheme.UNDEFINED; }

     /** Get the status message associated with this stream resource.
      * @return Status message associated with this stream resource.
      * May indicate no error if the stream resource was successfully constructed, {@link #NO_ERROR} is returned.  Otherwise, it
      * may return an error message describing the source of the construction error.
     */
    public String getResourceStatusMessage() {
        return resourceStatusMessage;
    }

    /** Construct stream resource from the specified URI. If the stream resource is not successfully constructed,
     * then the streamResourceStatusMessage may be used to return the reason why the construction was not successful.
     * @param uri The URI of the source file which may use the rtsp, http, or other protocol.
     */
    @JsonCreator
    public StreamResource(@JsonProperty("uri") String uri) {
        this.uri = uri;
        try {
            URI uriInstance = new URI(uri);
            uriScheme = UriScheme.parse(uriInstance.getScheme());
            if ( !isDefinedUriScheme() ) {
                resourceStatusMessage = NOT_DEFINED_URI_SCHEME;
            } else if ( !isSupportedUriScheme() ) {
                resourceStatusMessage = NOT_SUPPORTED_URI_SCHEME;
            } else {
                resourceStatusMessage = NO_ERROR;
            }
        } catch (URISyntaxException use) {
            uriScheme = UriScheme.UNDEFINED;
            resourceStatusMessage = use.getMessage();
        }
    }

    /** Check to see if the URI scheme for this stream resource is one of the supported stream protocols.
     * OpenMPF currently only supports the RTSP and HTTP protocols for streams.
     * @return true if the URI scheme for this stream resource is one of the supported stream protocols, false otherwise.
     */
    public boolean isSupportedUriScheme() {
        return isSupportedUriScheme(uriScheme);
    }

    /** Check to see if the URI scheme for this stream resource is one of the supported stream protocols.
     * OpenMPF currently only supports the RTSP and HTTP protocols for streams.
     * @param localUriScheme URI scheme to test.
     * @return true if the specified URI scheme is one of the supported stream protocols, false otherwise.
     */
    private static boolean isSupportedUriScheme(UriScheme localUriScheme) {
        return localUriScheme != null && ( localUriScheme == UriScheme.RTSP || localUriScheme == UriScheme.HTTP );
    }

    /** Check to see if the URI scheme for this stream resource is one of the supported stream protocols.
     * OpenMPF currently only supports the RTSP and HTTP protocols for streams.
     * @param uri The URI of the source file which may use the rtsp, http, or other protocol.
     * @return true if the URI scheme for this stream resource is one of the supported stream protocols, false otherwise.
     */
    public static boolean isSupportedUriScheme(String uri) {
        try {
            URI uriInstance = new URI(uri);
            UriScheme localUriScheme = UriScheme.parse(uriInstance.getScheme());
            return isSupportedUriScheme(localUriScheme);
        } catch (URISyntaxException use) {
            return false;
        }
    }

}
