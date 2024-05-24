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

import java.lang.ref.Cleaner;
import java.nio.file.Path;

import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class S3ClientWrapper {
    // We can't just close the S3 client after performing an operation because the client
    // is being cached. We can't close the client when it is evicted from the cache because it
    // might still be in use.
    private static final Cleaner CLEANER = Cleaner.create();

    private final S3Client _client;

    public S3ClientWrapper(S3Client client) {
        _client = client;
        CLEANER.register(this, new CleaningAction(client));
    }

    public <T> T getObject(
            GetObjectRequest getObjectRequest,
            ResponseTransformer<GetObjectResponse, T> responseTransformer) {
        return _client.getObject(getObjectRequest, responseTransformer);
    }


    public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest getObjectRequest) {
        return _client.getObject(getObjectRequest);
    }


    public boolean objectExists(
            String bucket, String key, AwsRequestOverrideConfiguration overrideConfiguration) {
        try {
            _client.headObject(b -> b
                    .bucket(bucket)
                    .key(key)
                    .overrideConfiguration(overrideConfiguration));
            return true;
        }
        catch (NoSuchKeyException e) {
            return false;
        }
    }

    public PutObjectResponse putObject(PutObjectRequest putObjectRequest, Path sourcePath) {
        return _client.putObject(putObjectRequest, sourcePath);
    }


    public PutObjectResponse putObject(PutObjectRequest putObjectRequest, RequestBody requestBody) {
        return _client.putObject(putObjectRequest, requestBody);
    }

    public CopyObjectResponse copyObject(CopyObjectRequest copyObjectRequest) {
        return _client.copyObject(copyObjectRequest);
    }


    private static class CleaningAction implements Runnable {
        private final S3Client _client;

        public CleaningAction(S3Client client) {
            _client = client;
        }

        public void run() {
            _client.close();
        }
    }
}
