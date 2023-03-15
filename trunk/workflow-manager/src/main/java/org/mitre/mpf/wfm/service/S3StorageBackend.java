/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

import com.google.common.collect.Table;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.MpfConstants;

import java.io.IOException;
import java.net.URI;
import java.util.function.UnaryOperator;

public interface S3StorageBackend extends StorageBackend {

    @Override
    public boolean canStore(JsonOutputObject outputObject) throws StorageException;

    @Override
    public URI store(JsonOutputObject outputObject, Mutable<String> outputSha) throws StorageException, IOException;

    @Override
    public boolean canStore(ArtifactExtractionRequest request) throws StorageException;

    @Override
    public Table<Integer, Integer, URI> storeArtifacts(ArtifactExtractionRequest request) throws IOException;

    @Override
    public boolean canStore(MarkupResult markupResult) throws StorageException;

    @Override
    public void store(MarkupResult markupResult) throws StorageException, IOException;

    @Override
    public boolean canStoreDerivativeMedia(BatchJob job, long parentMediaId) throws StorageException;

    @Override
    public void storeDerivativeMedia(BatchJob job, Media media) throws StorageException, IOException;


    public void downloadFromS3(Media media, UnaryOperator<String> combinedProperties) throws StorageException;

    public ResponseInputStream<GetObjectResponse> getFromS3(
        String uri, UnaryOperator<String> properties) throws StorageException;


    /**
     * Ensures that the S3-related properties are valid.
     * @param properties Properties to validate
     * @throws StorageException when an invalid combination of S3 properties are provided.
     */
    public static void validateS3Properties(UnaryOperator<String> properties) throws StorageException {
        // Both will throw if properties are invalid.
        requiresS3MediaDownload(properties);
        requiresS3ResultUpload(properties);
    }


    public static boolean requiresS3MediaDownload(UnaryOperator<String> properties) throws StorageException {
        boolean uploadOnly = Boolean.parseBoolean(properties.apply(MpfConstants.S3_UPLOAD_ONLY));
        if (uploadOnly) {
            return false;
        }

        boolean hasAccessKey = StringUtils.isNotBlank(properties.apply(MpfConstants.S3_ACCESS_KEY));
        boolean hasSecretKey = StringUtils.isNotBlank(properties.apply(MpfConstants.S3_SECRET_KEY));
        if (hasAccessKey && hasSecretKey) {
            return true;
        }
        if (!hasAccessKey && !hasSecretKey) {
            return false;
        }

        String presentProperty;
        String missingProperty;
        if (hasAccessKey) {
            presentProperty = MpfConstants.S3_ACCESS_KEY;
            missingProperty = MpfConstants.S3_SECRET_KEY;
        }
        else {
            presentProperty = MpfConstants.S3_SECRET_KEY;
            missingProperty = MpfConstants.S3_ACCESS_KEY;
        }
        throw new StorageException(String.format("The %s property was set, but the %s property was not.",
                                                 presentProperty, missingProperty));
    }


    public static boolean requiresS3ResultUpload(UnaryOperator<String> properties) throws StorageException {
        if (StringUtils.isBlank(properties.apply(MpfConstants.S3_RESULTS_BUCKET))) {
            return false;
        }
        boolean hasAccessKey = StringUtils.isNotBlank(properties.apply(MpfConstants.S3_ACCESS_KEY));
        boolean hasSecretKey = StringUtils.isNotBlank(properties.apply(MpfConstants.S3_SECRET_KEY));
        if (hasAccessKey && hasSecretKey) {
            return true;
        }

        if (!hasAccessKey && !hasSecretKey) {
            throw new StorageException(String.format(
                    "The %s property was set, but the %s and %s properties were not.",
                    MpfConstants.S3_RESULTS_BUCKET, MpfConstants.S3_ACCESS_KEY,
                    MpfConstants.S3_SECRET_KEY));
        }

        String presentProperty;
        String missingProperty;
        if (hasAccessKey) {
            presentProperty = MpfConstants.S3_ACCESS_KEY;
            missingProperty = MpfConstants.S3_SECRET_KEY;
        }
        else {
            presentProperty = MpfConstants.S3_SECRET_KEY;
            missingProperty = MpfConstants.S3_ACCESS_KEY;
        }
        throw new StorageException(String.format(
                "The %s and %s properties were set, but the %s property was not.",
                MpfConstants.S3_RESULTS_BUCKET, presentProperty, missingProperty));
    }
}