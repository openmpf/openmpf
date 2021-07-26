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

package org.mitre.mpf.wfm.camelOps;

import org.apache.camel.Exchange;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionProcessor;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaMetadataValidator;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.enums.*;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JniLoader;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mitre.mpf.test.TestUtil.nonBlank;
import static org.mitre.mpf.test.TestUtil.nonEmptyMap;
import static org.mockito.Mockito.*;

public class TestMediaInspectionProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(TestMediaInspectionProcessor.class);
    private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final InProgressBatchJobsService _mockInProgressJobs
            = mock(InProgressBatchJobsService.class);

    private final MediaMetadataValidator _mockMediaMetadataValidator
            = mock(MediaMetadataValidator.class);

    private final MediaInspectionProcessor _mediaInspectionProcessor
            = new MediaInspectionProcessor(_mockPropertiesUtil, _mockInProgressJobs, new IoUtils(),
                                           _mockMediaMetadataValidator);

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();


    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static int next() {
        return SEQUENCE.incrementAndGet();
    }


    @BeforeClass
    public static void initClass() {
        assertTrue(JniLoader.isLoaded());
        ThreadUtil.start();
    }

    @Before
    public void init() {
        when(_mockMediaMetadataValidator.skipInspection(anyLong(), any()))
                .thenReturn(false);
    }


    @Test(timeout = 5 * MINUTES)
    public void testImageInspection() {
        LOG.info("Starting image media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/meds1.jpg", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "c067e7eed23a0fe022140c30dbfa993ae720309d6567a803d111ecec739a6713";// `sha256sum meds1.jpg`

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.IMAGE), eq("image/jpeg"),
                                        eq(1), notNull());
        verifyNoJobOrMediaError();

        LOG.info("Image media inspection test passed.");
    }


    @Test(timeout = 5 * MINUTES)
    public void testVideoInspection() {
        LOG.info("Starting video media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/video_01.mp4", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea"; // `sha256sum video_01.mp4`
        int frameCount = 90; // `ffprobe -show_packets video_01.mp4 | grep video | wc -l`

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO), eq("video/mp4"),
                        eq(frameCount), metadataCaptor.capture());
        verifyNoJobOrMediaError();

        var mediaMetadata = metadataCaptor.getValue();
        assertTrue(Boolean.parseBoolean(mediaMetadata.get("HAS_CONSTANT_FRAME_RATE")));

        LOG.info("Video media inspection test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testAudioInspection() {
        LOG.info("Starting audio media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/green.wav", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "237739f8d6ff3459d747f79d272d148d156a696bad93f3ddecc2350c4ee5d9e0"; // `sha256sum green.wav`

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.AUDIO),
                        eq("audio/vnd.wave"), eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

        LOG.info("Audio media inspection test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testVideoToAudioFallback() {
        LOG.info("Starting media inspection test with video to audio fallback.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/video_02_audio_only.mp4", Collections.emptyMap());

        verify(_mockInProgressJobs, atLeastOnce())
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MISSING_VIDEO_STREAM), nonBlank());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "5891ecaf9423b58526e5a11f0409c329ceec95551357f424ba8a19a3578327ba"; // `sha256sum video_02_audio_only`

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.AUDIO), eq("video/mp4"),
                        eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

        LOG.info("Media inspection test with video to audio fallback passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testVideoToUnknownFallback() {
        LOG.info("Starting media inspection test with video to unknown fallback.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/video_01_invalid.mp4", Collections.emptyMap());

        verify(_mockInProgressJobs, atLeastOnce())
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MISSING_VIDEO_STREAM), nonBlank());
        verify(_mockInProgressJobs, atLeastOnce())
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MISSING_AUDIO_STREAM), nonBlank());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "239dbbbe6faf66af7eb471ad54b993526221043ced333723a4fd450d107f272c"; // `sha256sum video_01_invalid.mp4`

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.UNKNOWN), eq("video/mp4"),
                        eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

        LOG.info("Media inspection test with video to unknown fallback passed..");
    }

    @Test(timeout = 5 * MINUTES)
    public void testInaccessibleFileInspection()  {
        LOG.info("Starting inaccessible file media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, URI.create("file:/asdfasfdasdf124124sadfasdfasdf.bin"),
                Collections.emptyMap());

        assertTrue(media.isFailed());
        verifyMediaError(jobId, mediaId);

        LOG.info("Inaccessible file media inspection test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testAdtsFile() {
        LOG.info("Starting adts file test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/green.adts", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "b587735773acb39e7d092305dc47db14c568c446dee63786c58cd4e7711b6739"; // `sha256sum green.adts`

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.AUDIO),
                        eq("audio/x-hx-aac-adts"), eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

        LOG.info("adts file test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testTsFile() {
        LOG.info("Starting ts file test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/bbb24p_00_short.ts", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "06091f89bfa66d0f882f1a71f68858a8ec1ffaa96919b9f87b30a14795f0189f"; // `sha256sum bbb24p_00_short.ts`

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO),
                        eq("video/vnd.dlna.mpeg-tts"), eq(10), metadataCaptor.capture());

        verify(_mockInProgressJobs)
                .addWarning(jobId, mediaId, IssueCodes.FRAME_COUNT,
                            "OpenCV reported the frame count to be 27, but FFmpeg reported it to be 10. 10 will be used.");

        verifyNoJobOrMediaError();
        var mediaMetadata = metadataCaptor.getValue();
        assertFalse(Boolean.parseBoolean(mediaMetadata.get("HAS_CONSTANT_FRAME_RATE")));

        LOG.info("ts file test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testCrushedPngInspection() {
        when(_mockPropertiesUtil.getTemporaryMediaDirectory())
                .thenReturn(_tempFolder.getRoot());

        long jobId = next();
        long mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/pngdefry/lenna-crushed.png",
                                       Map.of());

        assertFalse(String.format("The response entity must not fail. Message: %s.",
                                  media.getErrorMessage()),
                    media.isFailed());

        verifyNoJobOrMediaError();

        // `sha256sum lenna-crushed.png`
        String mediaHash = "cfcf04d5abe24dd8747b2b859e567864cca883d7dc391171dd682d635509bc89";
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.IMAGE),
                                        eq("image/png"), eq(1),
                                        metadataCaptor.capture());
        assertEquals("512", metadataCaptor.getValue().get("FRAME_WIDTH"));
        assertEquals("512", metadataCaptor.getValue().get("FRAME_HEIGHT"));

        var pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(_mockInProgressJobs)
                .addConvertedMediaPath(eq(jobId), eq(mediaId), pathCaptor.capture());
        var defriedPath = pathCaptor.getValue();
        assertTrue(defriedPath.startsWith(_tempFolder.getRoot().toPath()));
        assertTrue(defriedPath.getFileName().toString().startsWith("lenna-crushed_defried"));
        assertTrue(defriedPath.getFileName().toString().endsWith(".png"));
        assertTrue(Files.exists(defriedPath));
    }

    @Test(timeout = 5 * MINUTES)
    public void testHeicInspection() {
        when(_mockPropertiesUtil.getTemporaryMediaDirectory())
                .thenReturn(_tempFolder.getRoot());

        long jobId = next();
        long mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/IMG_5355.HEIC",
                                       Map.of());

        assertFalse(String.format("The response entity must not fail. Message: %s.",
                                  media.getErrorMessage()),
                    media.isFailed());

        verifyNoJobOrMediaError();

        // `sha256sum IMG_5355.HEIC`
        String mediaHash = "a671c241b4943919236865df4fa9997f99d80ce4dba276256436f6310914aff2";
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.IMAGE),
                                        eq("image/heic"), eq(1),
                                        metadataCaptor.capture());
        assertEquals("3024", metadataCaptor.getValue().get("FRAME_WIDTH"));
        assertEquals("4032", metadataCaptor.getValue().get("FRAME_HEIGHT"));

        var pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(_mockInProgressJobs)
                .addConvertedMediaPath(eq(jobId), eq(mediaId), pathCaptor.capture());
        var heicPath = pathCaptor.getValue();
        assertTrue(heicPath.startsWith(_tempFolder.getRoot().toPath()));
        assertTrue(heicPath.getFileName().toString().endsWith(".png"));
        assertTrue(Files.exists(heicPath));
    }

    @Test(timeout = 5 * MINUTES)
    public void canHandleInvalidExifDimensions() {
        long jobId = next();
        long mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/lp-bmw.jpg",
                                       Map.of());

        assertFalse(String.format("The response entity must not fail. Message: %s.",
                                  media.getErrorMessage()),
                    media.isFailed());
        verifyNoJobOrMediaError();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), notNull(), eq(MediaType.IMAGE),
                                        eq("image/jpeg"), eq(1),
                                        metadataCaptor.capture());
        assertEquals("809", metadataCaptor.getValue().get("FRAME_WIDTH"));
        assertEquals("606", metadataCaptor.getValue().get("FRAME_HEIGHT"));
        assertEquals("1", metadataCaptor.getValue().get("EXIF_ORIENTATION"));
    }

    @Test
    // No need to explicitly test when skipInspection is false because all other
    // tests in this class have it set to false.
    public void canSkipMediaInspection() {
        long jobId = next();
        long mediaId = next();

        when(_mockMediaMetadataValidator
                     .skipInspection(eq(jobId), argThat(m -> m.getId() == mediaId)))
                .thenReturn(true);

        inspectMedia(jobId, mediaId, URI.create("file:///fake"), Map.of());
        verify(_mockInProgressJobs, never())
                .addMediaInspectionInfo(anyLong(), anyLong(), any(), any(), any(), anyInt(), anyMap());
        verifyNoJobOrMediaError();
    }


    private void verifyNoJobOrMediaError() {
        verify(_mockInProgressJobs, never())
                .addError(anyLong(), anyLong(), any(), any());
        verify(_mockInProgressJobs, never())
                .setJobStatus(anyLong(), eq(BatchJobStatusType.ERROR));
    }

    private void verifyMediaError(long jobId, long mediaId) {
        verify(_mockInProgressJobs, atLeastOnce())
                .addError(eq(jobId), eq(mediaId), eq(IssueCodes.MEDIA_INSPECTION), nonBlank());
    }

    private Exchange setupExchange(long jobId, MediaImpl media) {
        return MediaTestUtil.setupExchange(jobId, media, _mockInProgressJobs);
    }

    private MediaImpl inspectMedia(long jobId, long mediaId, String filePath, Map<String, String> mediaMetadata) {
        URI mediaUri = TestUtil.findFile(filePath);
        return inspectMedia(jobId, mediaId, mediaUri, mediaMetadata);
    }

    private MediaImpl inspectMedia(long jobId, long mediaId, URI mediaUri, Map<String, String> mediaMetadata) {
        MediaImpl media = new MediaImpl(
                mediaId, mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri), Collections.emptyMap(),
                mediaMetadata, null);
        Exchange exchange = setupExchange(jobId, media);
        _mediaInspectionProcessor.process(exchange);

        assertEquals("Media ID headers must be set.", mediaId, exchange.getOut().getHeader(MpfHeaders.MEDIA_ID));
        assertEquals("Job ID headers must be set.", jobId, exchange.getOut().getHeader(MpfHeaders.JOB_ID));

        return media;
    }
}
