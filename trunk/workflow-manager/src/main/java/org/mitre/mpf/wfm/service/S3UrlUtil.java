/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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


package org.mitre.mpf.wfm.service;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.mitre.mpf.wfm.enums.MpfConstants;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;

public interface S3UrlUtil {
    public static S3UrlUtil get(Function<String, String> properties) {
        if (Boolean.parseBoolean(properties.apply(MpfConstants.S3_USE_VIRTUAL_HOST))) {
            return VIRTUAL_HOST_STYLE;
        }
        else {
            return PATH_STYLE;
        }
    }

    public URI getS3Endpoint(String uri) throws StorageException;

    public String getResultsBucketName(URI bucketUri) throws StorageException;

    public URI getFullUri(URI bucketUri, String objectKey) throws StorageException;

    public String[] splitBucketAndObjectKey(String uriStr) throws StorageException;



    static final S3UrlUtil PATH_STYLE = new S3UrlUtil() {
        @Override
        public String getResultsBucketName(URI bucketUri) throws StorageException{
            String path = bucketUri.getPath();
            if (path.length() < 2 || path.charAt(0) != '/') {
                throw new StorageException(
                        "Could not determine bucket name from URI: " + bucketUri);
            }
            int slash2Pos = path.indexOf('/', 1);
            return slash2Pos < 0
                    ? path.substring(1)
                    : path.substring(1, slash2Pos);
        }

        @Override
        public URI getS3Endpoint(String uri) throws StorageException {
            return getS3EndpointShared(uri);
        }

        @Override
        public URI getFullUri(URI bucketUri, String objectKey) throws StorageException {
            try {
                return new URIBuilder(bucketUri)
                        .setPath(bucketUri.getPath() + '/' + objectKey)
                        .build();
            }
            catch (URISyntaxException e) {
                throw new StorageException("Couldn't build uri: " + e, e);
            }
        }

        @Override
        public String[] splitBucketAndObjectKey(String uriStr) throws StorageException {
            URI uri = URI.create(uriStr);
            String uriPath = uri.getPath();
            if (uriPath.startsWith("/")) {
                uriPath = uriPath.substring(1);
            }
            String[] parts = uriPath.split("/", 2);
            if (parts.length != 2 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])) {
                throw new StorageException(
                        "Unable to determine bucket name and object key from uri: " + uriStr);
            }
            return parts;
        }
    };


    static final S3UrlUtil VIRTUAL_HOST_STYLE = new S3UrlUtil() {
        @Override
        public URI getS3Endpoint(String uri) throws StorageException {
            return getS3EndpointShared(uri);
        }


        @Override
        public String getResultsBucketName(URI bucketUri) throws StorageException {
            var host = bucketUri.getHost().toLowerCase();
            var s3Index = host.indexOf(".s3.");
            if (s3Index < 0) {
                throw new StorageException("host name did not contain .s3.");
            }
            return host.substring(0, s3Index);
        }


        @Override
        public URI getFullUri(URI bucketUri, String objectKey) throws StorageException {
            try {
                return new URIBuilder(bucketUri)
                        .setPath(objectKey)
                        .build();
            }
            catch (URISyntaxException e) {
                throw new StorageException("Couldn't build uri: " + e, e);
            }
        }

        @Override
        public String[] splitBucketAndObjectKey(String uriStr) throws StorageException {
            var uri = URI.create(uriStr);
            var bucket = getResultsBucketName(uri);
            var path = uri.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            return new String[] { bucket, path };
        }
    };



    private static URI getS3EndpointShared(String uri) throws StorageException {
        try {
            URI serviceUri = new URIBuilder(uri)
                    .setPath("")
                    .setFragment(null)
                    .removeQuery()
                    .build();
            if (serviceUri.getHost() == null) {
                throw new StorageException(String.format(
                        "Could not determine S3 host from \"%s\".", uri));
            }
            return serviceUri;
        }
        catch (URISyntaxException e) {
            throw new StorageException(
                    "An error occurred while trying to determine the S3 endpoint: "
                            + e.getMessage(), e);
        }
    }
}
