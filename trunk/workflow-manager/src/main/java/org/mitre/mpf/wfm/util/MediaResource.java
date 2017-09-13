package org.mitre.mpf.wfm.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.mitre.mpf.wfm.enums.UriScheme;

/**
 * Contains properties used to identify a piece of media.  The media may or may not have a local file path associated with it.
 */
public class MediaResource {

    public static final String NO_ERROR = "NO ERROR";
    public static final String NOT_SUPPORTED_URI_SCHEME = "Unsupported URI scheme";

    private String uri;
    private String resourceStatusMessage = NO_ERROR;

    /** The URI associated with this piece of media.
     * @return The URI associated with this piece of media.
     */
    public String getUri() { return uri; }

    private UriScheme uriScheme = null;
    /** The URI scheme (protocol) associated with this media resource, may include the file, http, https, or some other protocol.
     * @return The URI scheme (protocol) associated with this media resource.
     */
    public UriScheme getUriScheme() {return this.uriScheme;}

    private String localFilePath = null;

    /** The local file path of this piece of media, if this is file media, null otherwise.
     * @return If this is file media, returns the absolute path to the local file for this piece of media, null otherwise.
     */
    public String getLocalFilePath() { return this.localFilePath; }
    public void setLocalFilePath (String localFilePath) { this.localFilePath = localFilePath; }

    /** Check to see if this is a piece of file media.
     * @return true if this is file media, false otherwise.
     */
    public boolean isFileMedia() { return uriScheme == UriScheme.FILE; }

    /** Check to see if this is a correctly defined media resource.
     * @return true if the URI scheme of this media resource is correctly constructed and is of the file, http, https, or other protocol, false otherwise.
     */
    public boolean isValidResource() { return uriScheme == UriScheme.UNDEFINED; }

     /** Get the status message associated with this media resource.
      * @return Status message associated with this media resource.
      * May indicate no error if the media resource was successfully constructed, {@link #NO_ERROR} is returned.  Otherwise, it
      * may return an error message describing the source of the construction error.
     */
    public String getResourceStatusMessage() {
        return resourceStatusMessage;
    }

    /** Construct media resource from the specified URI. If the media resource is not successfully constructed,
     * then the mediaResourceStatusMessage may be used to return the reason why the construction was not successful.
     * @param uri The URI of the source file which may use the file, http, https, or other protocol.
     */
    public MediaResource(String uri) {
        this.uri = uri;
        try {
            URI uriInstance = new URI(uri);
            uriScheme = UriScheme.parse(uriInstance.getScheme());
            if (uriScheme == UriScheme.FILE) {
                localFilePath = Paths.get(uriInstance).toAbsolutePath().toString();
            } else if ( uriScheme == UriScheme.UNDEFINED ) {
                resourceStatusMessage = NOT_SUPPORTED_URI_SCHEME;
            }
        } catch (URISyntaxException use) {
            uriScheme = UriScheme.UNDEFINED;
            resourceStatusMessage = use.getMessage();
        }
    }

    /** Check to see if the URI scheme for this media is one of the media protocols supported by OpenMPF.
     * OpenMPF supports the file, http, https, or other protocol for media
     * @return true if the URI scheme for this media is one of the supported media protocols, false otherwise.
     */
    public boolean isSupportedUriScheme() {
        if ( isSupportedUriScheme(getUriScheme()) ) {
            return true;
        } else {
            return false;
        }
    }

    /** Check to see if the URI scheme for this media resource is one of the supported protocols.
     * OpenMPF supports the file, http, https, or other protocol for media
     * @oaran localUriScheme URI scheme to test
     * @return true if the specified URI scheme is one of the supported protocols, false otherwise.
     */
    private static boolean isSupportedUriScheme(UriScheme localUriScheme) {
        return ( localUriScheme != null && localUriScheme != UriScheme.UNDEFINED );
    }

    /** Check to see if the URI scheme for this media resource is one of the supported stream protocols.
     * OpenMPF supports the file, http, https, or other protocol for media
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

}
