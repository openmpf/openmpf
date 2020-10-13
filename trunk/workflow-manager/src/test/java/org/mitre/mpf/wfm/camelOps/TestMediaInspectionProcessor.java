/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionProcessor;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaMetadataValidator;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.enums.*;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JniLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mitre.mpf.test.TestUtil.nonBlank;
import static org.mitre.mpf.test.TestUtil.nonEmptyMap;
import static org.mockito.Mockito.*;

public class TestMediaInspectionProcessor {
	private static final Logger log = LoggerFactory.getLogger(TestMediaInspectionProcessor.class);
	private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

    private final InProgressBatchJobsService mockInProgressJobs = mock(InProgressBatchJobsService.class);

    private final MediaMetadataValidator mediaMetadataValidator = new MediaMetadataValidator(mockInProgressJobs);

    private final MediaInspectionProcessor mediaInspectionProcessor
            = new MediaInspectionProcessor(mockInProgressJobs, new IoUtils(), mediaMetadataValidator);

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static int next() {
        return SEQUENCE.incrementAndGet();
    }

    @BeforeClass
    public static void initClass() {
        assertTrue(JniLoader.isLoaded());
    }

	@Test(timeout = 5 * MINUTES)
	public void testImageInspection() {
		log.info("Starting image media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/meds1.jpg", Collections.emptyMap());

		assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
				media.isFailed());

		String mediaHash = "c067e7eed23a0fe022140c30dbfa993ae720309d6567a803d111ecec739a6713";// `sha256sum meds1.jpg`

		verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.IMAGE), eq("image/jpeg"),
                        eq(1), notNull());
		verifyNoJobOrMediaError();

		log.info("Image media inspection test passed.");
	}

	@Test(timeout = 5 * MINUTES)
	public void testVideoInspection() {
		log.info("Starting video media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/video_01.mp4", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea"; // `sha256sum video_01.mp4`
        int frameCount = 90; // `ffprobe -show_packets video_01.mp4 | grep video | wc -l`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO), eq("video/mp4"),
                        eq(frameCount), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Video media inspection test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testInvalidVideoInspection() {
        log.info("Starting invalid video media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/video_01_invalid.mp4", Collections.emptyMap());

        verify(mockInProgressJobs, atLeastOnce())
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MISSING_VIDEO_STREAM), nonBlank());
        verify(mockInProgressJobs, atLeastOnce())
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MISSING_AUDIO_STREAM), nonBlank());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "239dbbbe6faf66af7eb471ad54b993526221043ced333723a4fd450d107f272c"; // `sha256sum video_01_invalid.mp4`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.UNKNOWN), eq("video/mp4"),
                        eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Invalid video media inspection test passed.");
    }

	@Test(timeout = 5 * MINUTES)
	public void testAudioInspection() {
		log.info("Starting audio media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/green.wav", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "237739f8d6ff3459d747f79d272d148d156a696bad93f3ddecc2350c4ee5d9e0"; // `sha256sum green.wav`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.AUDIO),
                        eq("audio/vnd.wave"), eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

		log.info("Audio media inspection test passed.");
	}

	@Test(timeout = 5 * MINUTES)
	public void testInaccessibleFileInspection()  {
		log.info("Starting inaccessible file media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, URI.create("file:/asdfasfdasdf124124sadfasdfasdf.bin"),
                Collections.emptyMap());

        assertTrue(media.isFailed());
        verifyMediaError(jobId, mediaId);

		log.info("Inaccessible file media inspection test passed.");
	}

    @Test(timeout = 5 * MINUTES)
    public void testSkipAudioInspection() {
        log.info("Starting skip audio media inspection test.");

        long jobId = next(), mediaId = next();
        String mimeType = "audio/vnd.wave";
        String mediaHash = "237739f8d6ff3459d747f79d272d148d156a696bad93f3ddecc2350c4ee5d9e0";
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", mimeType,
                "MEDIA_HASH", mediaHash,
                "DURATION", "10");
        inspectMedia(jobId, mediaId, "/samples/green.wav", mediaMetadata);

        verifyNoMediaWarning();

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.AUDIO), eq(mimeType),
                        eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Skip audio media inspection test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testSkipGenericInspection() {
        log.info("Starting skip generic media inspection test.");

        long jobId = next(), mediaId = next();
        String mimeType = "text/plain";
        String mediaHash = "10c2975d2842573ed90b8a0fe29618237ca006f49c96cde87c55741b244e2843";
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", mimeType,
                "MEDIA_HASH", mediaHash);
        inspectMedia(jobId, mediaId, "/samples/NOTICE", mediaMetadata);

        verifyNoMediaWarning();

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.UNKNOWN), eq(mimeType),
                        eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Skip generic media inspection test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testSkipImageInspection() {
        log.info("Starting skip image media inspection test.");

        long jobId = next(), mediaId = next();
        String mimeType = "image/jpeg";
        String mediaHash = "c067e7eed23a0fe022140c30dbfa993ae720309d6567a803d111ecec739a6713";
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", mimeType,
                "MEDIA_HASH", mediaHash,
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "EXIF_ORIENTATION", "0",
                "ROTATION", "0",
                "HORIZONTAL_FLIP", "FALSE");
        inspectMedia(jobId, mediaId, "/samples/meds1.jpg", mediaMetadata);

        verifyNoMediaWarning();

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.IMAGE), eq("image/jpeg"),
                        eq(1), notNull());
        verifyNoJobOrMediaError();

        log.info("Skip image media inspection test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testSkipVideoInspection() {
        log.info("Starting skip video media inspection test.");

        long jobId = next(), mediaId = next();
        String mimeType = "video/mp4";
        String mediaHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea";
        int frameCount = 90;
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", mimeType,
                "MEDIA_HASH", mediaHash,
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "FRAME_COUNT", Integer.toString(frameCount),
                "FPS", "30",
                "DURATION", "3",
                "ROTATION", "0");
        inspectMedia(jobId, mediaId, "/samples/video_01.mp4", mediaMetadata);

        verifyNoMediaWarning();

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO), eq(mimeType),
                        eq(frameCount), nonEmptyMap());

        verifyNoJobOrMediaError();

        log.info("Skip video media inspection test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testNotSkipInspectionWithMissingMetadata() {
        log.info("Starting not skip media inspection test with missing metadata.");

        long jobId = next(), mediaId = next();
        String mimeType = "video/mp4";
        String mediaHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea";
        int frameCount = 90;
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", mimeType,
                "MEDIA_HASH", mediaHash,
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "FRAME_COUNT", Integer.toString(frameCount),
                "FPS", "30",
                // missing DURATION
                "ROTATION", "0");
        inspectMedia(jobId, mediaId, "/samples/video_01.mp4", mediaMetadata);

        verify(mockInProgressJobs, atLeastOnce())
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MEDIA_INSPECTION), contains("video metadata"));
        verify(mockInProgressJobs, atLeastOnce())
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MEDIA_INSPECTION), contains("audio metadata"));

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.UNKNOWN), eq(mimeType),
                        eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Not skip media inspection test with missing metadata passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testNotSkipInspectionWithInvalidMetadata() {
        log.info("Starting not skip media inspection test with invalid metadata.");

        long jobId = next(), mediaId = next();
        String mimeType = "video/mp4";
        String mediaHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea";
        int frameCount = 90;
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", mimeType,
                "MEDIA_HASH", mediaHash,
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "FRAME_COUNT", Integer.toString(frameCount),
                "FPS", "0", // value should be non-zero
                "DURATION", "3",
                "ROTATION", "0");
        inspectMedia(jobId, mediaId, "/samples/video_01.mp4", mediaMetadata);

        verifyMediaWarning(jobId, mediaId);

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO), eq(mimeType),
                        eq(frameCount), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Not skip media inspection test with invalid metadata passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testNotSkipInspectionVideoToAudioFallback() {
        log.info("Starting not skip media inspection test with video to audio fallback.");

        long jobId = next(), mediaId = next();
        String mimeType = "video/mp4";
        String mediaHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea";
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", mimeType,
                "MEDIA_HASH", mediaHash,
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "FRAME_COUNT", "90",
                // missing FPS
                "DURATION", "3",
                "ROTATION", "0");
        inspectMedia(jobId, mediaId, "/samples/video_01.mp4", mediaMetadata);

        verify(mockInProgressJobs, atLeastOnce())
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MEDIA_INSPECTION), contains("video metadata"));

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.AUDIO), eq(mimeType),
                        eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Not skip media inspection test with video to audio fallback passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testAdtsFile() {
        log.info("Starting adts file test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/green.adts", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "b587735773acb39e7d092305dc47db14c568c446dee63786c58cd4e7711b6739"; // `sha256sum green.adts`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.AUDIO),
                        eq("audio/x-hx-aac-adts"), eq(-1), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("adts file test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testHeicFile() {
        log.info("Starting heic file test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/IMG_5355.HEIC", Collections.emptyMap());

        assertTrue(String.format("The response entity will fail until we implement heic file support. Message: %s.",
                media.getErrorMessage()), media.isFailed());

        String mediaHash = "a671c241b4943919236865df4fa9997f99d80ce4dba276256436f6310914aff2"; // `sha256sum IMG_5355.HEIC`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.IMAGE),
                        eq("image/heic"), eq(-1), nonEmptyMap());
        verifyMediaError(jobId, mediaId); // heic files are not currently supported

        log.info("heic file test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testApplePngFile() {
        log.info("Starting Apple png file test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/Lenna-crushed.png", Collections.emptyMap());

        assertTrue(String.format("The response entity will fail until we implement Apple png file support. Message: %s.",
                media.getErrorMessage()), media.isFailed());

        String mediaHash = "cfcf04d5abe24dd8747b2b859e567864cca883d7dc391171dd682d635509bc89"; // `sha256sum Lenna-crushed.png`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.IMAGE),
                        eq("image/png"), eq(-1), nonEmptyMap());
        verifyMediaError(jobId, mediaId); // Apple png files are not currently supported

        log.info("Apple png file test passed.");
    }

    @Test(timeout = 5 * MINUTES)
    public void testTsFile() {
        log.info("Starting ts file test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/bbb24p_00_short.ts", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "06091f89bfa66d0f882f1a71f68858a8ec1ffaa96919b9f87b30a14795f0189f"; // `sha256sum bbb24p_00_short.ts`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO),
                        eq("video/vnd.dlna.mpeg-tts"), eq(27), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("ts file test passed.");
    }

	private void verifyNoJobOrMediaError() {
	    verify(mockInProgressJobs, never())
                .addError(anyLong(), anyLong(), any(), any());
	    verify(mockInProgressJobs, never())
                .setJobStatus(anyLong(), eq(BatchJobStatusType.ERROR));
    }

    private void verifyMediaError(long jobId, long mediaId) {
        verify(mockInProgressJobs, atLeastOnce())
                .addError(eq(jobId), eq(mediaId), eq(IssueCodes.MEDIA_INSPECTION), nonBlank());
	    verify(mockInProgressJobs)
                .setJobStatus(jobId, BatchJobStatusType.ERROR);
    }

    private void verifyNoMediaWarning() {
        verify(mockInProgressJobs, never())
                .addWarning(anyLong(), anyLong(), any(), any());
    }

    private void verifyMediaWarning(long jobId, long mediaId) {
        verify(mockInProgressJobs, atLeastOnce())
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MEDIA_INSPECTION), nonBlank());
    }

	private Exchange setupExchange(long jobId, MediaImpl media) {
	    return MediaTestUtil.setupExchange(jobId, media, mockInProgressJobs);
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
        mediaInspectionProcessor.process(exchange);

        assertEquals("Media ID headers must be set.", mediaId, exchange.getOut().getHeader(MpfHeaders.MEDIA_ID));
        assertEquals("Job ID headers must be set.", jobId, exchange.getOut().getHeader(MpfHeaders.JOB_ID));

        return media;
    }
}
