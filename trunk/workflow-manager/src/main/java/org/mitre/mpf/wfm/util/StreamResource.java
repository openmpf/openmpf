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
import java.util.Arrays;
import java.util.List;
import org.mitre.mpf.wfm.enums.ListFilterType;
import org.mitre.mpf.wfm.enums.UriScheme;

/**
 * Contains properties used to identify a stream.
 */
public class StreamResource {

    public static final String NO_ERROR = "NO ERROR";
    public static final String NOT_DEFINED_URI_SCHEME = "URI scheme not defined";
    public static final String NOT_SUPPORTED_URI_SCHEME = "Unsupported URI scheme";

    // define the UriSchemes that are supported by OpenMPF for stream resources.
    // Note that OpenMPF supports only HTTPS, HTTP and RTSP protocols for streams, so we use an supported (i.e. inclusive) list of protocols here.
    public static final List<UriScheme> supportedUriSchemeList = Arrays.asList(UriScheme.RTSP, UriScheme.HTTP, UriScheme.HTTPS);

    private StreamResourceContainer streamResourceContainer = null;
    private String resourceStatusMessage = null;

    /** The URI associated with this stream.
     * @return The URI associated with this stream.
     */
    public String getUri() { return streamResourceContainer.getUri(); }

    /** The URI scheme (protocol) associated with this stream resource. OpenMPF only supports the HTTPS, HTTP and RTSP protocols for streams.
     * @return The URI scheme (protocol) associated with this stream resource.
     */
    public UriScheme getUriScheme() {return streamResourceContainer.getUriScheme();}

    /** Check to see if this is a correctly defined stream resource.
     * @return true if this stream resource is correctly constructed, false otherwise.
     */
    public boolean isDefinedUriScheme() { return streamResourceContainer.isResourceOfDefinedUriScheme(); }

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
     * @param uri The URI of the stream which may use the HTTPS, HTTP and RTSP protocol.
     */
    @JsonCreator
    public StreamResource(@JsonProperty("uri") String uri) {

        streamResourceContainer = new StreamResourceContainer(uri, ListFilterType.INCLUSION_LIST, supportedUriSchemeList);
        if ( streamResourceContainer.isStreamResourceInError() ) {
            resourceStatusMessage = streamResourceContainer.getResourceErrorMessage();
        } else if ( !streamResourceContainer.isResourceOfDefinedUriScheme() ) {
            resourceStatusMessage = NOT_DEFINED_URI_SCHEME;
        } else if ( !streamResourceContainer.isResourceOfSupportedUriScheme() ) {
            resourceStatusMessage = NOT_SUPPORTED_URI_SCHEME;
        } else {
            resourceStatusMessage = NO_ERROR;
        }
     }

    /** Check to see if the URI scheme for this stream resource is one of the supported stream protocols.
     * OpenMPF currently only supports the HTTPS, HTTP and RTSP protocols for streams.
     * @return true if the URI scheme for this stream resource is one of the supported stream protocols, false otherwise.
     */
    public boolean isSupportedUriScheme() {
        return streamResourceContainer.isResourceOfSupportedUriScheme();
    }

    /** Check to see if the URI scheme for this stream resource is one of the supported stream protocols.
     * OpenMPF currently only supports the HTTPS, HTTP and RTSP protocols for streams.
     * @param localUriScheme URI scheme to test.
     * @return true if the specified URI scheme is one of the supported stream protocols, false otherwise.
     */
    private static boolean isSupportedUriScheme(UriScheme localUriScheme) {
        return localUriScheme != null && supportedUriSchemeList.stream().anyMatch(supportedUriScheme -> localUriScheme == supportedUriScheme);
    }

    /** Check to see if the URI scheme for this stream resource is one of the supported stream protocols.
     * OpenMPF currently only supports the HTTPS, HTTP and RTSP protocols for streams.
     * @param uri The URI of the stream which may use the HTTPS, HTTP and RTSP.
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
