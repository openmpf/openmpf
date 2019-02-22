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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.tika.Tika;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.enums.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Optional;
import java.util.stream.Stream;

@Component(IoUtils.REF)
public class IoUtils {
    public static final String REF = "ioUtils";
    private static final Logger log = LoggerFactory.getLogger(IoUtils.class);

    @Autowired
    private MediaTypeUtils mediaTypeUtils;

    // Detect is thread safe, so only one instance is needed.
    // See: {@link http://grokbase.com/t/tika/user/114qab9908/is-the-method-detect-of-instance-org-apache-tika-tika-thread-safe}
    private final Tika tikaInstance = new Tika();

    /**
     * Gets the MIME type associated with the file located at {@code url}. This method never returns null.
     * @param url The location of the file to analyze. Must not be null.
     * @return A MIME type string value - this method never returns null.
     * @throws WfmProcessingException
     */
    public String getMimeType(URL url) throws WfmProcessingException {
        Validate.notNull(url, "The url parameter must not be null.");
        String mimeType;
        try {
            mimeType = tikaInstance.detect(url);
        } catch (IOException ioe) {
            throw new WfmProcessingException(String.format("Exception occurred when getting mime type of %s", url), ioe);
        }
        return mimeType;
    }

    /***
     * Gets the MIME type associated with the file located at {@code absolutePath}.
     * @param absolutePath
     * @return
     */
    public String getMimeType(String absolutePath) {
        Validate.notNull(absolutePath, "The absolutePath parameter must not be null.");
        return tikaInstance.detect(absolutePath);
    }

    /***
     * Gets the MIME type associated with the bytes in the array.
     * @param bytes
     * @return
     */
    public String getMimeType(byte[] bytes) {
        return tikaInstance.detect(bytes);
    }

    /**
     * Gets the MIME type associated with the file.
     * @param file
     * @return
     */
    public String getMimeType(File file) throws IOException {
        return tikaInstance.detect(file);
    }


    public String getMimeType(Path path) throws IOException {
        return tikaInstance.detect(path);
    }

    /**
     * Gets the MIME type associated with the inputstream
     * @param inputStream
     * @return
     */
    public String getMimeType(InputStream inputStream) throws IOException {
        return tikaInstance.detect(inputStream);
    }

    /**
     * Gets the MediaType associated with the given MIME type. Throws an exception if the MIME type does not map to a
     * MediaType. Never returns null.
     * @param mimeType A String MIME type (generally from {@link #getMimeType(java.net.URL)}. Must not be null.
     * @return A value from {@link org.mitre.mpf.wfm.enums.MediaType}. Never returns null
     * @throws WfmProcessingException
     */
    public MediaType getMediaTypeFromMimeType(String mimeType) throws WfmProcessingException {
        Validate.notNull(mimeType, "The mimeType parameter must not be null.");
        String type = StringUtils.lowerCase(mimeType);

        if (type.startsWith("image")) {
            return MediaType.IMAGE;
        } else if (type.startsWith("video")) {
            return MediaType.VIDEO;
        } else if (type.startsWith("audio")) {
            return MediaType.AUDIO;
        } else {
            log.warn(String.format("The MIME type '%s' does not map to a MediaType.", mimeType));
            return MediaType.UNKNOWN;
        }
    }

    /**
     * Gets the media type associated with the URL by checking if the MIME type starts with image or video. If no
     * MediaType matches the url's contents, an IOException is thrown.
     * @param url The file to check. Must not be null.
     * @return The MediaType which best fits the input. Never returns null.
     * @throws WfmProcessingException If the url's contents do not map to a MediaType.
     */
    public MediaType getMediaType(URL url) throws WfmProcessingException {
        Validate.notNull(url);
        return getMediaTypeFromMimeType(getMimeType(url));
    }

    /**
     * <p>
     * Determines if the path is local or remote.
     *
     * If given as http scheme, pings the server to determine if the resource is available.
     *
     * If local, attempts to find a file with the given path on the filesystem, and if that fails,
     * assumes that the file is a resource.
     * </p>
     *
     * @param path The file to find.
     * @return A URI which should resolve to the file.
     * @throws WfmProcessingException if path could not be converted to a URI
     */
    public URI findFile(String path) throws WfmProcessingException {
        if (StringUtils.startsWithIgnoreCase(path.toLowerCase(), "http")) {
            try {
                return new URI(path);
            } catch (URISyntaxException use) {
                throw new WfmProcessingException(use);
            }
        }
        File file = new File(path);
        if (file.exists()) {
            return file.getAbsoluteFile().toURI();
        } else {
            try {
                URL url = IoUtils.class.getResource(path);
                if (url != null) {
                    return url.toURI();
                } else {
                    throw new WfmProcessingException(String.format("Resource not found when converting %s to URI", path));
                }
            } catch (URISyntaxException use) {
                throw new WfmProcessingException
                        (String.format("Exception occurred when converting path %s to URI", path), use);
            }
        }
    }

    public File createTemporaryFile() throws IOException {
        File file = File.createTempFile("tmp", ".tmp");
        file.deleteOnExit();
        return file;
    }

    /***
     * returns a File of the file in the directory or adds a _n for duplicates
     * @param directory
     * @param filename
     * @return
     */
    public File getNewFileName(String directory, String filename) {
        File newFile = new File(directory + File.separator + filename);
        if (newFile.exists()) {
            String suffix = "";
            //remove and add on suffix if neccessary
            int suffixLocation = filename.indexOf(".");
            if (suffixLocation > -1) {
                suffix = filename.substring(suffixLocation, filename.length());
                filename = filename.substring(0, suffixLocation);
            }
            int i = 1;
            while (newFile.exists()) {
                newFile = new File(directory + File.separator + filename + "_" + i + suffix);//add a _n to the filename
                i++;
            }
        }
        return newFile;
    }

    /***
     * Returns true if the file passes mime and custom extension tests
     * @param file
     * @return
     */
    public boolean isApprovedFile(File file) {
        return isApprovedContentType(getMimeType(file.getAbsolutePath()));
    }

    /***
     * Returns true if the file passes mime and custom extension tests
     * @param url
     * @return
     * @throws WfmProcessingException
     */
    public boolean isApprovedFile(URL url) throws WfmProcessingException {
        return isApprovedContentType(getMimeType(url));
    }

    /***
     * Returns true if the content type is not equal to null
     * @param contentType
     * @return
     */
    public boolean isApprovedContentType(String contentType) {
        return mediaTypeUtils.parse(contentType) != null;
    }


    public static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        }
        catch (IOException ignored) {
        }
    }


    public static Optional<Path> toLocalPath(String pathOrUri) {
        if (pathOrUri == null) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(pathOrUri);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return Optional.of(Paths.get(pathOrUri));
            }
            if ("file".equalsIgnoreCase(scheme)) {
                return Optional.of(Paths.get(uri));
            }
            return Optional.empty();
        }
        catch (URISyntaxException ignored) {
            return Optional.of(Paths.get(pathOrUri));
        }
    }


    public static URL toUrl(String pathOrUri) {
        MalformedURLException suppressed;
        try {
            return new URL(pathOrUri);
        }
        catch (MalformedURLException e) {
            suppressed = e;
        }

        try {
            return Paths.get(pathOrUri).toUri().toURL();
        }
        catch (MalformedURLException e) {
            e.addSuppressed(suppressed);
            throw new IllegalArgumentException("pathOrUri", e);
        }
    }

    public static InputStream openStream(String pathOrUri) throws IOException {
        Optional<Path> localPath = toLocalPath(pathOrUri);
        if (localPath.isPresent()) {
            return Files.newInputStream(localPath.get());
        }
        return new URL(pathOrUri).openStream();
    }


    public static void deleteEmptyDirectoriesRecursively(Path startDir) {
        if (!Files.exists(startDir)) {
            return;
        }
        try {
            Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {

                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (isEmpty(dir)) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            log.warn("IOException while deleting " + startDir, e);
        }
    }

    private static boolean isEmpty(Path dir) throws IOException {
        try (Stream<Path> paths = Files.list(dir)) {
            return !paths.findAny().isPresent();
        }
    }


    public static void writeFileAsAttachment(Path path, HttpServletResponse response) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            writeContentAsAttachment(inputStream, response, path.getFileName().toString(),
                                     Files.probeContentType(path), Files.size(path));
        }
    }

    public static void writeContentAsAttachment(
            InputStream inputStream,
            HttpServletResponse response,
            String fileName,
            String mimeType,
            long contentLength)
                throws IOException {
        if (mimeType == null) {
            response.setContentType("application/octet-stream");
        }
        else {
            response.setContentType(mimeType);
        }
        response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", fileName));
        if (contentLength > 0 && contentLength < Integer.MAX_VALUE) {
            response.setContentLength((int) contentLength);
        }

        IOUtils.copy(inputStream, response.getOutputStream());
        response.flushBuffer();
    }


    public static String normalizeUri(String uriString) {
        if (uriString.startsWith("file:/") && !uriString.startsWith("file:///")) {
            return Paths.get(URI.create(uriString)).toUri().toString();
        }
        return uriString;
    }
}
