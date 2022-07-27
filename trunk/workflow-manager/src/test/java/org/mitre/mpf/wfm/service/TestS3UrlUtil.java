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


import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.enums.MpfConstants;

import java.net.URI;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class TestS3UrlUtil {

    @Test
    public void canHandleVirtualHostWithObjectKey() throws StorageException {
        var urlUtil = getVirtualHost();
        var testUrl = "https://my.bucket.name.S3.amazonaws.com/my/object/name";

        assertEquals(URI.create("https://S3.amazonaws.com"),
                     urlUtil.getS3Endpoint(testUrl));

        assertEquals("my.bucket.name",  urlUtil.getResultsBucketName(URI.create(testUrl)));

        var bucketAndKey = urlUtil.splitBucketAndObjectKey(testUrl);
        assertEquals("my.bucket.name", bucketAndKey[0]);
        assertEquals("my/object/name", bucketAndKey[1]);

        var combinedUri = urlUtil.getFullUri(
                URI.create("https://my.bucket.name.s3.amazonaws.com"), "my/object/name");
        assertEquals(URI.create(testUrl), combinedUri);
    }


    @Test
    public void testWrongHost() throws StorageException {
        var urlUtil = S3UrlUtil.get(k -> {
            if (k.equals(MpfConstants.S3_USE_VIRTUAL_HOST)) {
                return "true";
            }
            if (k.equals(MpfConstants.S3_HOST)) {
                return "some.other.url.example.com";
            }
            return null;
        });

        var testUrl = "https://my.bucket.name.S3.amazonaws.com/my/object/name";
        TestUtil.assertThrows(StorageException.class,
                              () -> urlUtil.getResultsBucketName(URI.create(testUrl)));

        TestUtil.assertThrows(StorageException.class,
                              () -> urlUtil.splitBucketAndObjectKey(testUrl));
    }


    @Test
    public void canHandleVirtualHostNoObjectKey() throws StorageException {
        var urlUtil = getVirtualHost();
        var resultsBucket = "https://my.bucket.name.s3.amazonaws.com";
        assertEquals(URI.create("https://s3.amazonaws.com"), urlUtil.getS3Endpoint(resultsBucket));
        assertEquals("my.bucket.name", urlUtil.getResultsBucketName(URI.create(resultsBucket)));

        assertEquals(
                URI.create("https://my.bucket.name.s3.amazonaws.com/ab/cd/abcdefg"),
                urlUtil.getFullUri(URI.create(resultsBucket), "ab/cd/abcdefg"));
    }


    @Test
    public void reportsNoBucketWithVirtualHost() throws StorageException {
        var urlUtil = getVirtualHost();
        var ex = TestUtil.assertThrows(
                StorageException.class,
                () -> urlUtil.getResultsBucketName(URI.create("https://s3.amazonaws.com")));
        assertThat(ex.getMessage(),
                   containsString("only contained the configured S3 host"));
    }


    @Test
    public void reportsErrorWhenInvalidResultsBucket() throws StorageException {
        var urlUtils = getVirtualHost();
        var notAUrl = "not-a-url";
        TestUtil.assertThrows(StorageException.class,
                              () -> urlUtils.getResultsBucketName(URI.create(notAUrl)));
    }


    @Test
    public void canHandlePathWithObjectKey() throws StorageException {
        var urlUtil = S3UrlUtil.get(k -> null);
        var testUrl = "https://asdf.example.com/bucket/my/object/name";

        assertEquals(URI.create("https://asdf.example.com"),
                     urlUtil.getS3Endpoint(testUrl));

        assertEquals("bucket",  urlUtil.getResultsBucketName(URI.create(testUrl)));

        var bucketAndKey = urlUtil.splitBucketAndObjectKey(testUrl);
        assertEquals("bucket", bucketAndKey[0]);
        assertEquals("my/object/name", bucketAndKey[1]);

        var combinedUri = urlUtil.getFullUri(
                URI.create("https://asdf.example.com/bucket"), "my/object/name");
        assertEquals(URI.create(testUrl), combinedUri);
    }


    @Test
    public void canHandlePathNoObjectKey() throws StorageException {
        var urlUtil = S3UrlUtil.get(k -> "false");
        var resultsBucket = "https://asdf.example.com/bucket";
        assertEquals(URI.create("https://asdf.example.com"), urlUtil.getS3Endpoint(resultsBucket));
        assertEquals("bucket", urlUtil.getResultsBucketName(URI.create(resultsBucket)));

        assertEquals(
                URI.create("https://asdf.example.com/bucket/ab/cd/abcdefg"),
                urlUtil.getFullUri(URI.create(resultsBucket), "ab/cd/abcdefg"));
    }


    private static S3UrlUtil getVirtualHost() throws StorageException {
        return S3UrlUtil.get(k -> {
            if (k.equals(MpfConstants.S3_USE_VIRTUAL_HOST)) {
                return "true";
            }
            else if (k.equals(MpfConstants.S3_HOST)) {
                return "s3.amazonaws.com";
            }
            else {
                return "";
            }
        });
    }
}
