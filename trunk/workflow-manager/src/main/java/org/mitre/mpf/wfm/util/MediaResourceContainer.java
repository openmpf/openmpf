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

package org.mitre.mpf.wfm.util;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Paths;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mitre.mpf.wfm.enums.ListFilterType;
import org.mitre.mpf.wfm.enums.UriScheme;

/**
 * Container for information about a media resource.
 */

public class MediaResourceContainer {

    private String resourceUri = null;
    public String getUri() { return resourceUri; }

    private UriScheme resourceUriScheme = null;
    public UriScheme getUriScheme() { return resourceUriScheme; }

    private File resourceFile = null;
    /** Get the File associated with the URI used to construct the media resource info, if applicable.
     * @return File associated with the URI used to construct the media resource info or null if this media resource is not defining a file.
     */
    public File getResourceFile ()  { return resourceFile; }

    private String resourceErrorMessage = null;
    private boolean isSupportedProtocol = false;

    /** Constructor to collect media resource info for the specified URI.
     * This constructor is protected because it is only intended to be instantiated by the MediaResource class.
     * @param uri URI to evaluate
     * @param listFilterType enumeration specifies if the specified uriSchemeFilterList is an inclusion filter or an exclusion filter
     * @param uriSchemeFilterList list of URI schemes that OpenMPF supports (if inclusion filter is passed) or
     * the list of URI schemes that OpenMPF doesn't support (if exclusion filter is passed).
     */
    @JsonCreator
    protected MediaResourceContainer(@JsonProperty("uri") String uri, @JsonProperty("listFilterType") ListFilterType listFilterType,
                             @JsonProperty("uriSchemeFilterList") List<UriScheme> uriSchemeFilterList) {
        resourceUri = uri;
        try {
            // collect basic information about the media.
            URI uriInstance = new URI(uri);
            resourceUriScheme = UriScheme.parse(uriInstance.getScheme());

            // additional handling is required for file media.  Get the file path, handling any possible errors which may occur.
            try {
                if (resourceUriScheme == UriScheme.FILE) {
                    resourceFile = Paths.get(uriInstance).toAbsolutePath().toFile();
                }
            } catch (IllegalArgumentException iae) {
                // an exception occurred while getting the file path, store the error and clear resourceUriScheme
                resourceErrorMessage = iae.getMessage();
                resourceUriScheme = UriScheme.UNDEFINED;
            } catch (FileSystemNotFoundException fsnfe) {
                // an exception occurred while getting the file path, store the error and clear resourceUriScheme
                resourceErrorMessage = fsnfe.getMessage();
                resourceUriScheme = UriScheme.UNDEFINED;
            } catch (SecurityException se) {
                // an exception occurred while getting the file path, store the error and clear resourceUriScheme
                resourceErrorMessage = se.getMessage();
                resourceUriScheme = UriScheme.UNDEFINED;
            }

            // use the filter parameters to determine whether or not OpenMPF supports this media,
            // provided that the resources URI scheme is still considered to be valid.
            if ( resourceUriScheme != null ) {
                if (listFilterType == ListFilterType.INCLUSION_LIST) {
                    // check the resourceUriScheme to see if it is in the list of supported uriSchemes, if so that the uriScheme is supported by OpenMPF
                    isSupportedProtocol =
                        isResourceOfDefinedUriScheme() && uriSchemeFilterList.stream().anyMatch(
                            supportedUriScheme -> resourceUriScheme == supportedUriScheme);
                } else {
                    // check the resourceUriScheme to see if it is in the list of unsupported uriSchemes, if so that the uriScheme is NOT supported by OpenMPF
                    isSupportedProtocol =
                        isResourceOfDefinedUriScheme() && uriSchemeFilterList.stream().noneMatch(
                            supportedUriScheme -> resourceUriScheme == supportedUriScheme);
                }
            }
        } catch (URISyntaxException use) {
            resourceErrorMessage = use.getMessage();
        }
    }

    public boolean isResourceOfDefinedUriScheme() { return resourceUriScheme != null && resourceUriScheme != UriScheme.UNDEFINED; }
    public boolean isResourceOfSupportedUriScheme() { return isSupportedProtocol; };
    public boolean isFileResource() { return resourceUriScheme == UriScheme.FILE && resourceFile != null; };
    public boolean isFileResourceExisting() { return isFileResource() && resourceFile.exists(); }
    public boolean isFileResourceReadable() { return isFileResourceExisting() && resourceFile.canRead(); }

    /** Get the error message associated with construction of this resource.
     * @return the error message associated with construction of this resource.  Will be null if no error occurred during construction.
     */
    public String getResourceErrorMessage() { return resourceErrorMessage; }

    /** Will return true if there was an error found during construction of this resource, false otherwise.
     * If the media resource was constructed with error, use method getResourceErrorMessage to find out what the error is.
     * @return true if there was an error found during construction of this resource, false otherwise.
     */
    public boolean isMediaResourceInError() { return resourceErrorMessage != null; }

 }
