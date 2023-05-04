/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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
import org.apache.tika.Tika;
import org.mitre.mpf.wfm.WfmProcessingException;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class IoUtils {
    private static final Logger LOG = LoggerFactory.getLogger(IoUtils.class);

    public static final String LINUX_MAGIC_PATH = "/usr/share/misc/magic.mgc";

    private static final String CUSTOM_MAGIC_PATH
            = IoUtils.class.getResource("/magic/custom-magic.mgc").getPath();

    @Autowired
    private PropertiesUtil _propertiesUtil;

    // Detect is thread safe, so only one instance is needed.
    // See: {@link http://grokbase.com/t/tika/user/114qab9908/is-the-method-detect-of-instance-org-apache-tika-tika-thread-safe}
    private final Tika _tikaInstance = new Tika();


    public String getMimeType(Path filePath) throws WfmProcessingException {
        try {
            String mimeType = getMimeTypeUsingTika(filePath);

            if (mimeType == null || mimeType.equals("application/octet-stream")) {
                String fileMimeType = getMimeTypeUsingFile(filePath);
                if (fileMimeType != null) {
                    mimeType = fileMimeType;
                }
            }

            return mimeType;
        } catch (Exception e) {
            throw new WfmProcessingException("Could not determine the MIME type for the media.", e);
        }
    }

    public String getMimeTypeUsingTika(Path filePath) throws IOException {
        return _tikaInstance.detect(filePath);
    }

    public String getMimeTypeUsingFile(Path filePath) throws IOException, InterruptedException {
        var process = new ProcessBuilder(
                    "file", "--magic-file", LINUX_MAGIC_PATH + ':' + CUSTOM_MAGIC_PATH,
                    "--mime-type", "--brief", filePath.toString())
                .start();

        // Need to read both stdout and stderr at the same time to prevent either of them
        // filling up their pipe buffer and causing the process to dead lock waiting for space in
        // the pipe buffer.
        var errorFuture = ThreadUtil.callAsync(
                () -> IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8).trim());

        String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).trim();
        String error = errorFuture.join();

        int exitCode = process.waitFor();
        if (exitCode != 0 || !error.isEmpty()) {
            throw new WfmProcessingException(
                    "\"file\" command returned an exit code of " + exitCode + ": " + error);
        }
        return output.isEmpty() ? null : output;
    }

    public String getPathContentType(Path filePath) {
        return getMimeType(filePath);
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

        Path filePath = Paths.get(path);
        if (Files.exists(filePath)) {
            return filePath.toUri();
        }

        // Give precedence to files in to the share path so that when performing integration tests we detect a path
        // that is accessible to all of the nodes.
        String sharePath;
        if (_propertiesUtil != null) {
            sharePath = _propertiesUtil.getSharePath();
        } else {
            sharePath = System.getenv("MPF_HOME") + "/share";
        }
        filePath = Paths.get(sharePath + path);
        if (Files.exists(filePath)) {
            return filePath.toUri();
        }

        try {
            URL url = IoUtils.class.getResource(path);
            if (url != null) {
                return Paths.get(url.toURI()).toUri(); // Path.toUri() returns proper "file:///" form of URI.
            }
        } catch (URISyntaxException use) {
            throw new WfmProcessingException
                    (String.format("Exception occurred when converting path %s to URI", path), use);
        }

        throw new WfmProcessingException(String.format("File not found at path %s", path));
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
                suffix = filename.substring(suffixLocation);
                filename = filename.substring(0, suffixLocation);
            }
            int i = 1;
            while (newFile.exists()) {
                newFile = new File(directory + File.separator + filename + "_" + i + suffix); // add a _n to the filename
                i++;
            }
        }
        return newFile;
    }

    public static boolean isSubdirectory(File child, File parent) {
        return isSubdirectory(child.toPath(), parent.toPath());
    }

    public static boolean isSubdirectory(Path child, Path parent) {
        return child.toAbsolutePath().normalize().startsWith(
                parent.toAbsolutePath().normalize());
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
            LOG.warn("IOException while deleting " + startDir, e);
        }
    }

    private static boolean isEmpty(Path dir) throws IOException {
        try (Stream<Path> paths = Files.list(dir)) {
            return !paths.findAny().isPresent();
        }
    }

    public void sendBinaryResponse(Path path, HttpServletResponse response)
            throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            sendBinaryResponse(inputStream, response, getMimeType(path), Files.size(path));
        }
    }

    public static void sendBinaryResponse(
            InputStream inputStream,
            HttpServletResponse response,
            String mimeType,
            long contentLength)
                throws IOException {
        if (mimeType == null) {
            response.setContentType("application/octet-stream");
        }
        else {
            response.setContentType(mimeType);
        }
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
