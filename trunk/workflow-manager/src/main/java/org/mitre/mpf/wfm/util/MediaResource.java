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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.enums.ListFilterType;
import org.mitre.mpf.wfm.enums.UriScheme;

/**
 * Contains properties used to identify a piece of media.  The media may or may not have a local file path associated with it.
 * This class also defines the protocols that OpenMPF supports for media resources
 */
public class MediaResource {

    public static final String NO_ERROR = "NO ERROR";
    public static final String NOT_DEFINED_URI_SCHEME = "URI scheme not defined";
    public static final String NOT_SUPPORTED_URI_SCHEME = "Unsupported URI scheme";
    public static final String LOCAL_FILE_DOES_NOT_EXIST = "File does not exist";
    public static final String LOCAL_FILE_NOT_READABLE = "File is not readable";

    // define the UriSchemes that are supported by OpenMPF for media resources.
    // Note that OpenMPF supports only HTTPS, HTTP and FILE protocols for media, so we use an supported (i.e. inclusive) list of protocols here.
    public static final List<UriScheme> supportedUriSchemeList = Arrays.asList(UriScheme.FILE, UriScheme.HTTP, UriScheme.HTTPS);

    private MediaResourceContainer mediaResourceContainer = null;

    // Local file path (i.e. absolute file path) being kept as a separate property, allowing for overriding file info in mediaResourceContainer.
    // Support for this is required by RemoteMediaSplitter//TransientMedia
    private String localFilePath = null;

    /** The local file path of this piece of media, if this is file media, null otherwise.
     * @return If this is file media, returns the absolute path to the local file for this piece of media, null otherwise.
     */
    public String getLocalFilePath() { return localFilePath; }

    // Note: need to support this method for use by RemoteMediaSplitter//TransientMedia.  Usage of this method will override
    // local file path as obtained from mediaResourceContainer
    public void setLocalFilePath (String localFilePath) { this.localFilePath = localFilePath; }

    private String resourceStatusMessage = null;

    /** The URI associated with this piece of media.
     * @return The URI associated with this piece of media.
     */
    public String getUri() { return mediaResourceContainer.getUri(); }

    /** The URI scheme (protocol) associated with this media resource. Note that OpenMPF supports only HTTPS, HTTP and FILE protocols for media.
     * @return The URI scheme (protocol) associated with this media resource.
     */
    public UriScheme getUriScheme() {return mediaResourceContainer.getUriScheme();}

    /** Check to see if this is file media.
     * @return true if this is file media, false otherwise.
     */
    public boolean isFileMedia() { return mediaResourceContainer.isFileResource(); }
    /** Check to see if this is file media, which exists and is readable.
     * @return true if this is existing, readable file media, false otherwise.
     */
    public boolean isExistingReadableFileMedia() { return mediaResourceContainer.isFileResourceReadable(); }

    /** Check to see if this is a correctly defined media resource.
     * @return true if the URI scheme of this media resource is correctly constructed, false otherwise.
     */
    public boolean isDefinedUriScheme() { return mediaResourceContainer.isResourceOfDefinedUriScheme(); }

     /** Get the status message associated with this media resource.
      * @return Status message associated with this media resource.
      * May indicate no error if the media resource was successfully constructed, {@link #NO_ERROR} is returned.  Otherwise, it
      * may return an error message describing the source of the construction error.
     */
    public String getResourceStatusMessage() {
        return resourceStatusMessage;
    }

    /** Construct media resource from the specified URI. If the media resource is not successfully constructed,
     * then the mediaResourceStatusMessage may be used to return the reason why the construction failed.
     * @param uri The URI of the media, defined using one of the media protocols supported by OpenMPF.
     */
    @JsonCreator
    public MediaResource(@JsonProperty("uri") String uri) {
        // construct the media resource info container, passing along the URI schemes that are supported by OpenMPF for this type of media.
        mediaResourceContainer = new MediaResourceContainer(uri, ListFilterType.INCLUSION_LIST, supportedUriSchemeList);

        if ( mediaResourceContainer.isMediaResourceInError() ) {
            resourceStatusMessage = mediaResourceContainer.getResourceErrorMessage();
        } else if ( !mediaResourceContainer.isResourceOfDefinedUriScheme() ) {
            resourceStatusMessage = NOT_DEFINED_URI_SCHEME;
        } else if ( !mediaResourceContainer.isResourceOfSupportedUriScheme() ) {
            resourceStatusMessage = NOT_SUPPORTED_URI_SCHEME;
        } else if ( mediaResourceContainer.isFileResource() && !mediaResourceContainer.isFileResourceExisting() ) {
            resourceStatusMessage = LOCAL_FILE_DOES_NOT_EXIST;
        } else if ( mediaResourceContainer.isFileResource() && !mediaResourceContainer.isFileResourceReadable() ) {
            resourceStatusMessage = LOCAL_FILE_NOT_READABLE;
        } else if ( mediaResourceContainer.isFileResource() ) {
            // localFilePath maintained separately allows for override of file path in mediaResourceContainer via method setLocalFilePath
            localFilePath = mediaResourceContainer.getResourceFile().getAbsolutePath();
            resourceStatusMessage = NO_ERROR;
        } else {
            resourceStatusMessage = NO_ERROR;
        }
    }

    /** Check to see if the URI scheme for this media is one of the protocols OpenMPF supports for media.
     * For media, OpenMPF supports the file, http, or https protocol.
     * @return true if the specified URI scheme is any defined protocol, false otherwise.
     */
    public boolean isSupportedUriScheme() {
        return isSupportedUriScheme(getUriScheme());
    }

    /** Check to see if the passed URI scheme is one of the supported protocols.
     * For media, OpenMPF supports the file, http or https protocol.
     * @param localUriScheme URI scheme to test.
     * @return true if the specified URI scheme is any defined protocol, false otherwise.
     */
    private static boolean isSupportedUriScheme(UriScheme localUriScheme) {
        // check the localUriScheme to see if it is in the list of supported uriSchemes, if so that the uriScheme is supported by OpenMPF
        return localUriScheme != null && supportedUriSchemeList.stream().anyMatch(supportedUriScheme -> localUriScheme == supportedUriScheme);
    }

    /** Check to see if the URI scheme for this media resource is one of the supported stream protocols.
     * OpenMPF supports the file, http or https protocol for media.
     * @param uri The URI of the source file which may use any of the supported protocols.
     * @return true if the URI scheme for this media resource is one of the supported protocols, false otherwise.
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

    /** Collect media resource info for the specified URI.
     * @param uri URI to be evaluated.
     * @return Information about the media resource, including UriScheme, protocol support, file status (if applicable)
     */
    public static MediaResourceContainer getMediaResourceContainer(String uri) {
        return new MediaResourceContainer(uri, ListFilterType.INCLUSION_LIST, supportedUriSchemeList);
    }

}
