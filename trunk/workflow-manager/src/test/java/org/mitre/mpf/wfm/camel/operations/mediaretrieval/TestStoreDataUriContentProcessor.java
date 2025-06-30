/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2025 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2025 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel.operations.mediaretrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.MediaUri;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class TestStoreDataUriContentProcessor extends MockitoTest.Strict {

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @InjectMocks
    private StoreDataUriContentProcessor _storeDataUriContentProcessor;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private static final long TEST_JOB_ID = 542;

    private static final long TEST_MEDIA_ID = 783;

    @Mock
    private BatchJob _mockJob;

    private Exchange _testExchange = TestUtil.createTestExchange();

    @Before
    public void init() {
        lenient().when(_mockInProgressJobs.getJob(TEST_JOB_ID))
            .thenReturn(_mockJob);
        _testExchange.getIn().setHeader(MpfHeaders.JOB_ID, TEST_JOB_ID);
    }



    @Test
    public void skipsNonDataUris() {
        var media = Stream.of(UriScheme.values())
            .filter(us -> us != UriScheme.DATA)
            .map(us -> {
                var medium = mock(Media.class);
                when(medium.getUriScheme()).thenReturn(us);
                return medium;
            })
            .toArray(Media[]::new);
        setMedia(media);

        _storeDataUriContentProcessor.process(_testExchange);

        verifyNoErrors();
        verifyMimeTypeNotSet();
    }


    @Test
    public void testInvalidUri() {
        assertThatThrownBy(() -> new MediaUri("data:text/plain;,Hello, World!"))
            .isInstanceOf(URISyntaxException.class);
    }

    @Test
    public void testBase64Error() {
        assertError("data:base64,ABC-?", "Unrecognized character: ?");
    }

    @Test
    public void testCommaMissing() {
        assertError("data:text/plain;Hello", "does not contain a comma");
    }


    private void assertError(String dataUri, String expectedErrorContent) {
        createMockMedia(dataUri);
        _storeDataUriContentProcessor.process(_testExchange);

        verify(_mockInProgressJobs)
            .addError(
                eq(TEST_JOB_ID), eq(783L), eq(IssueCodes.LOCAL_STORAGE),
                and(
                    contains("Error saving data URI content"),
                    contains(expectedErrorContent))
                );
        verifyMimeTypeNotSet();
    }


    @Test
    public void testEmpty() {
        assertStored("data:text/plain;,", "", "text/plain");
        assertStored("data:text/plain1;base64,", "", "text/plain1");
    }


    @Test
    public void testEmptyNoMimeType() {
        assertStored("data:,", "", null);
        assertStored("data:base64,", "", null);
        verifyMimeTypeNotSet();
    }


    @Test
    public void testPercentEncoding() {
        assertStored(
            "data:text/plain;,Hello,%20World!",
            "Hello, World!",
            "text/plain");

        assertStored(
                "data:text/plain,%E4%BD%A0%E5%8F%AB%E4%BB%80%E4%B9%88%E5%90%8D%E5%AD%97%EF%BC%9F",
               "你叫什么名字？",
               "text/plain");
    }

    @Test
    public void testPercentEncodingNoMime() {
        assertStored(
            "data:;,Hello,%20World!",
            "Hello, World!",
            null);

        assertStored(
                "data:,%E4%BD%A0%E5%8F%AB%E4%BB%80%E4%B9%88%E5%90%8D%E5%AD%97%EF%BC%9F",
               "你叫什么名字？",
               null);
    }


    @Test
    public void testBase64TextEnglish() {
        assertStored(
            "data:text/plain;base64,SGVsbG8sIFdvcmxkIQ==",
            "Hello, World!",
            "text/plain");

        assertStored(
            "data:text/plain1;charset=UTF-8;base64,5L2g5Y+r5LuA5LmI5ZCN5a2X77yf",
            "你叫什么名字？",
            "text/plain1");
    }


    @Test
    public void testBase64TextNoMime() {
        assertStored(
            "data:;base64,SGVsbG8sIFdvcmxkIQ==",
            "Hello, World!",
            null);
        assertStored(
            "data:base64,5L2g5Y+r5LuA5LmI5ZCN5a2X77yf",
            "你叫什么名字？",
            null);
        verifyMimeTypeNotSet();
    }


    @Test
    public void testBase64InRegularData() {
        assertStored(
            "data:text/plain;,base64,",
            "base64,",
            "text/plain");

        assertStored(
            "data:text/plain1;,base64,def",
            "base64,def",
            "text/plain1");

        assertStored(
            "data:text/plain2;,abcbase64,def",
            "abcbase64,def",
            "text/plain2");
    }


    @Test
    public void testBase64InRegularDataNoMimeType() {
        assertStored(
            "data:,base64,",
            "base64,",
            null);

        assertStored(
            "data:;,base64,def",
            "base64,def",
            null);

        assertStored(
            "data:;,abcbase64,def",
            "abcbase64,def",
            null);

        verifyMimeTypeNotSet();
    }

    @Test
    public void testBase64InContentType() {
        assertStored(
            "data:text/plain;charset=UTF-8gg;badbase64,SGVsbG8sIFdvcmxkIQ==",
            "SGVsbG8sIFdvcmxkIQ==",
            "text/plain");
    }

    @Test
    public void testBase64BinaryData() {
        testBinaryData("data:fake/mime;base64,");
        verifyMimeTypeSet("fake/mime");
    }


    @Test
    public void testBase64BinaryDataNoMime() {
        testBinaryData("data:base64,");
        verifyMimeTypeNotSet();
    }


    private void testBinaryData(String uriPrefix) {
        var base64Data
                = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4O"
                + "To7PD0+P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYGFiY2RlZmdoaWprbG1ub3Bxcn"
                + "N0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMjY6PkJGSk5SVlpeYmZqbnJ2en6ChoqOkpaanqKmqq6y"
                + "trq+wsbKztLW2t7i5uru8vb6/wMHCw8TFxsfIycrLzM3Oz9DR0tPU1dbX2Nna29zd3t/g4eLj5OXm"
                + "5+jp6uvs7e7v8PHy8/T19vf4+fr7/P3+/w==";

        var uri = uriPrefix + base64Data;
        var expectedData = new byte[256];
        IntStream.range(0, expectedData.length)
            .forEach(i -> expectedData[i] = (byte) i);

        var path = assertStored(uri);
        assertThat(path)
            .hasBinaryContent(expectedData);
    }


    @Test
    public void canHandleIoException() throws IOException {
        var media = createMockMedia("data:text/plain;,hello");
        Files.delete(media.getLocalPath().getParent());
        _storeDataUriContentProcessor.process(_testExchange);

        verify(_mockInProgressJobs)
            .addError(
                eq(TEST_JOB_ID), eq(783L), eq(IssueCodes.LOCAL_STORAGE),
                contains("Error saving data URI content"));
        verifyMimeTypeNotSet();
    }


    private void assertStored(String dataUri, String expectedContent, String expectedMimeType) {
        var path = assertStored(dataUri);
        assertThat(path)
                .usingCharset(StandardCharsets.UTF_8)
                .hasContent(expectedContent);
        if (expectedMimeType != null) {
            verifyMimeTypeSet(expectedMimeType);
        }
    }

    private Path assertStored(String dataUri) {
        var media = createMockMedia(dataUri);
        _storeDataUriContentProcessor.process(_testExchange);
        verifyNoErrors();
        return media.getLocalPath();
    }


    private Media createMockMedia(String dataUri) {
        var media = mock(Media.class);
        when(media.getUriScheme())
            .thenReturn(UriScheme.DATA);
        when(media.getUri())
            .thenReturn(MediaUri.create(dataUri));

        when(media.getId())
            .thenReturn(TEST_MEDIA_ID);
        try {
            when(media.getLocalPath())
                .thenReturn(_tempFolder.newFolder().toPath().resolve("test-media.bin"));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        setMedia(media);
        return media;
    }

    private void setMedia(Media... media) {
        when(_mockJob.getMedia())
            .thenReturn(List.of(media));
    }

    private void verifyNoErrors() {
        verify(_mockInProgressJobs, never())
            .addError(anyLong(), anyLong(), any(), any());
    }

    private void verifyMimeTypeSet(String expectedMimeType) {
        verify(_mockInProgressJobs)
            .setMimeType(TEST_JOB_ID, TEST_MEDIA_ID, expectedMimeType);
    }

    private void verifyMimeTypeNotSet() {
        verify(_mockInProgressJobs, never())
            .setMimeType(anyLong(), anyLong(), anyString());
    }
}
