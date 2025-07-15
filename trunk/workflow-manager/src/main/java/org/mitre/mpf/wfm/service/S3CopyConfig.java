/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.mitre.mpf.wfm.enums.MpfConstants;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

public class S3CopyConfig {
    public final UnaryOperator<String> props;

    public final String sourceAccessKey;
    public final String destinationAccessKey;
    public final String sourceSecretKey;
    public final String destinationSecretKey;
    public final String sourceSessionToken;
    public final String destinationSessionToken;
    public final String destinationBucketUri;
    public final String sourceRegion;
    public final String destinationRegion;
    public final boolean sourceUseVirtualHost;
    public final boolean destinationUseVirtualHost;
    public final String sourceHost;
    public final String destinationHost;
    public final String sourcePrefix;
    public final String destinationPrefix;


    public final S3UrlUtil sourceUrlUtil;
    public final S3UrlUtil destinationUrlUtil;

    public final AwsCredentialsProvider sourceCredentials;
    public final AwsCredentialsProvider destinationCredentials;

    public S3CopyConfig(UnaryOperator<String> props) throws StorageException {
        this.props = props;
        sourceAccessKey = getSrcProp(props, MpfConstants.S3_ACCESS_KEY);
        destinationAccessKey = props.apply(MpfConstants.S3_ACCESS_KEY);

        sourceSecretKey = getSrcProp(props, MpfConstants.S3_SECRET_KEY);
        destinationSecretKey = props.apply(MpfConstants.S3_SECRET_KEY);

        sourceSessionToken = getSrcProp(props, MpfConstants.S3_SESSION_TOKEN);
        destinationSessionToken = props.apply(MpfConstants.S3_SESSION_TOKEN);

        destinationBucketUri = props.apply(MpfConstants.S3_RESULTS_BUCKET);

        sourceRegion = getSrcProp(props, MpfConstants.S3_REGION);
        destinationRegion = props.apply(MpfConstants.S3_REGION);

        sourceUseVirtualHost
                = Boolean.parseBoolean(getSrcProp(props, MpfConstants.S3_USE_VIRTUAL_HOST));
        destinationUseVirtualHost
                = Boolean.parseBoolean(props.apply(MpfConstants.S3_USE_VIRTUAL_HOST));

        sourceHost = getSrcProp(props, MpfConstants.S3_HOST);
        destinationHost = props.apply(MpfConstants.S3_HOST);

        sourcePrefix = Objects.requireNonNullElse(
                getSrcProp(props, MpfConstants.S3_UPLOAD_OBJECT_KEY_PREFIX), "");
        destinationPrefix = Objects.requireNonNullElse(
                props.apply(MpfConstants.S3_UPLOAD_OBJECT_KEY_PREFIX), "");

        sourceUrlUtil = sourceUseVirtualHost
                ? S3UrlUtil.getVirtualHostStyle(sourceHost)
                : S3UrlUtil.PATH_STYLE;
        destinationUrlUtil = S3UrlUtil.get(props);

        sourceCredentials = getCredentials(sourceAccessKey, sourceSecretKey, sourceSessionToken);
        destinationCredentials = getCredentials(destinationAccessKey, destinationSecretKey, destinationSessionToken);
    }


    public boolean canUseSameClient(URI sourceUri) throws StorageException {
        return Objects.equals(sourceAccessKey, destinationAccessKey)
                && Objects.equals(sourceSecretKey, destinationSecretKey)
                && Objects.equals(sourceSessionToken, destinationSessionToken)
                && Objects.equals(sourceRegion, destinationRegion)
                && sourceUseVirtualHost == destinationUseVirtualHost
                && Objects.equals(sourceHost, destinationHost)
                && Objects.equals(
                    sourceUrlUtil.getS3Endpoint(sourceUri),
                    destinationUrlUtil.getS3Endpoint(destinationBucketUri)
                );
    }

    public String getDestinationBucket() throws StorageException {
        return destinationUrlUtil.getResultsBucketName(destinationBucketUri);
    }


    public String getDestinationKey(String sourceKey) {
        String sourceKeyNoPrefix;
        if (sourceKey.startsWith(sourcePrefix)) {
            sourceKeyNoPrefix = sourceKey.substring(sourcePrefix.length());
        }
        else if (sourceKey.startsWith(destinationPrefix)) {
            sourceKeyNoPrefix = sourceKey.substring(destinationPrefix.length());
        }
        else {
            sourceKeyNoPrefix = sourceKey;
        }
        return destinationPrefix + sourceKeyNoPrefix;
    }


    private static String getSrcProp(UnaryOperator<String> props, String propName) {
        return Optional.ofNullable(props.apply(MpfConstants.TIES_DB_COPY_SRC_ + propName))
            .filter(s -> !s.isBlank())
            .orElseGet(() -> props.apply(propName));
    }


    private static AwsCredentialsProvider getCredentials(
            String accessKey, String secretKey, String sessionToken) {
        AwsCredentials credentials;
        if (sessionToken != null && !sessionToken.isBlank()) {
            credentials = AwsSessionCredentials.create(accessKey, secretKey, sessionToken);
        }
        else {
            credentials = AwsBasicCredentials.create(accessKey, secretKey);
        }
        return StaticCredentialsProvider.create(credentials);
    }
}
