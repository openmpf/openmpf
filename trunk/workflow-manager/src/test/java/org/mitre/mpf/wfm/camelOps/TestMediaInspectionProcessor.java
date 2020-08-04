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
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
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

		String targetType = "image";
		int targetLength = 1;
		String targetHash = "c067e7eed23a0fe022140c30dbfa993ae720309d6567a803d111ecec739a6713";//`sha256sum meds1.jpg`

		verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(targetHash), startsWith(targetType),
                                        eq(targetLength), notNull());
		verifyNoJobOrMediaError();

		log.info("Image media inspection test passed.");
	}

	/** Tests that the results from a video file are sane. */
	@Test(timeout = 5 * MINUTES)
	public void testVideoInspection() {
		log.info("Starting video media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/video_01.mp4", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String targetType = "video";
        int targetLength = 90; //`ffprobe -show_packets video_01.mp4 | grep video | wc -l`
        String targetHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea"; //`sha256sum video_01.mp4`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(targetHash), startsWith(targetType),
                                        eq(targetLength), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Video media inspection test passed.");
    }

    /** Tests that the results from a video file are sane. */
    @Test(timeout = 5 * MINUTES)
    public void testVideoInspectionInvalid() {
        log.info("Starting invalid video media inspection test.");

        long jobId = next(), mediaId = next();
        inspectMedia(jobId, mediaId, "/samples/video_01_invalid.mp4", Collections.emptyMap());

        verifyMediaError(jobId, mediaId);

        log.info("Invalid video media inspection test passed.");
    }

	/** Tests that the results from an audio file are sane. */
	@Test(timeout = 5 * MINUTES)
	public void testAudioInspection() {
		log.info("Starting audio media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/green.wav", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String targetType = "audio";
        int targetLength = -1; //`ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 green.wav` - actually produces 2.200000
        String targetHash = "237739f8d6ff3459d747f79d272d148d156a696bad93f3ddecc2350c4ee5d9e0"; //`sha256sum green.wav`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(targetHash), startsWith(targetType),
                                        eq(targetLength), nonEmptyMap());
        verifyNoJobOrMediaError();

		log.info("Audio media inspection test passed.");
	}

	/** Tests that the results from a file which is not accessible is sane. */
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

    /** Tests that media inspection is properly skipped when given valid audio metadata. */
    @Test(timeout = 5 * MINUTES)
    public void testSkipAudioInspection() {
        log.info("Starting skip audio media inspection test.");

        long jobId = next(), mediaId = next();
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", "audio/green.wav",
                "MEDIA_HASH", "237739f8d6ff3459d747f79d272d148d156a696bad93f3ddecc2350c4ee5d9e0",
                "DURATION", "10");
        inspectMedia(jobId, mediaId, "/samples/green.wav", mediaMetadata);

        verifyNoMediaWarning();

        String targetType = "audio";
        int targetLength = -1;
        String targetHash = "237739f8d6ff3459d747f79d272d148d156a696bad93f3ddecc2350c4ee5d9e0"; //`sha256sum green.wav`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(targetHash), startsWith(targetType),
                        eq(targetLength), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Skip audio media inspection test passed.");
    }

    /** Tests that media inspection is properly skipped when given valid generic metadata. */
    @Test(timeout = 5 * MINUTES)
    public void testSkipGenericInspection() {
        log.info("Starting skip generic media inspection test.");

        long jobId = next(), mediaId = next();
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", "application/green.wav",
                "MEDIA_HASH", "237739f8d6ff3459d747f79d272d148d156a696bad93f3ddecc2350c4ee5d9e0");
        inspectMedia(jobId, mediaId, "/samples/green.wav", mediaMetadata);

        verifyNoMediaWarning();

        String targetType = "application";
        int targetLength = -1;
        String targetHash = "237739f8d6ff3459d747f79d272d148d156a696bad93f3ddecc2350c4ee5d9e0"; //`sha256sum green.wav`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(targetHash), startsWith(targetType),
                        eq(targetLength), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Skip generic media inspection test passed.");
    }

    /** Tests that media inspection is properly skipped when given valid image metadata. */
    @Test(timeout = 5 * MINUTES)
    public void testSkipImageInspection() {
        log.info("Starting skip image media inspection test.");

        long jobId = next(), mediaId = next();
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", "image/meds1.jpg",
                "MEDIA_HASH", "c067e7eed23a0fe022140c30dbfa993ae720309d6567a803d111ecec739a6713",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "EXIF_ORIENTATION", "0",
                "ROTATION", "0",
                "HORIZONTAL_FLIP", "FALSE");
        inspectMedia(jobId, mediaId, "/samples/meds1.jpg", mediaMetadata);

        verifyNoMediaWarning();

        String targetType = "image";
        int targetLength = 1;
        String targetHash = "c067e7eed23a0fe022140c30dbfa993ae720309d6567a803d111ecec739a6713";//`sha256sum meds1.jpg`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(targetHash), startsWith(targetType),
                        eq(targetLength), notNull());
        verifyNoJobOrMediaError();

        log.info("Skip image media inspection test passed.");
    }

    /** Tests that media inspection is properly skipped when given valid video metadata. */
    @Test(timeout = 5 * MINUTES)
    public void testSkipVideoInspection() {
        log.info("Starting skip video media inspection test.");

        long jobId = next(), mediaId = next();
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", "video/video_01.mp4",
                "MEDIA_HASH", "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "FRAME_COUNT", "90",
                "FPS", "30",
                "DURATION", "3",
                "ROTATION", "0");
        inspectMedia(jobId, mediaId, "/samples/video_01.mp4", mediaMetadata);

        verifyNoMediaWarning();

        String targetType = "video";
        int targetLength = 90; //`ffprobe -show_packets video_01.mp4 | grep video | wc -l`
        String targetHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea"; //`sha256sum video_01.mp4`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(targetHash), startsWith(targetType),
                        eq(targetLength), nonEmptyMap());

        verifyNoJobOrMediaError();

        log.info("Skip video media inspection test passed.");
    }

    /** Tests that media inspection is not skipped when missing required metadata. */
    @Test(timeout = 5 * MINUTES)
    public void testSkipInspectionWithMissingMetadata() {
        log.info("Starting skip media inspection test with missing metadata.");

        long jobId = next(), mediaId = next();
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", "video/video_01.mp4",
                "MEDIA_HASH", "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                // missing FRAME_COUNT
                "FPS", "30",
                "DURATION", "3",
                "ROTATION", "0");
        inspectMedia(jobId, mediaId, "/samples/video_01.mp4", mediaMetadata);

        verifyMediaWarning(jobId, mediaId);

        String targetType = "video";
        int targetLength = 90; //`ffprobe -show_packets video_01.mp4 | grep video | wc -l`
        String targetHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea"; //`sha256sum video_01.mp4`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(targetHash), startsWith(targetType),
                        eq(targetLength), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Skip media inspection test with missing metadata passed.");
    }

    /** Tests that media inspection is not skipped when invalid metadata is provided. */
    @Test(timeout = 5 * MINUTES)
    public void testSkipInspectionWithInvalidMetadata() {
        log.info("Starting skip media inspection test with invalid metadata.");

        long jobId = next(), mediaId = next();
        Map<String, String> mediaMetadata = Map.of(
                "MIME_TYPE", "video/video_01.mp4",
                "MEDIA_HASH", "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "FRAME_COUNT", "90",
                "FPS", "0", // value should be non-zero
                "DURATION", "3",
                "ROTATION", "0");
        inspectMedia(jobId, mediaId, "/samples/video_01.mp4", mediaMetadata);

        verifyMediaWarning(jobId, mediaId);

        String targetType = "video";
        int targetLength = 90; //`ffprobe -show_packets video_01.mp4 | grep video | wc -l`
        String targetHash = "5eacf0a11d51413300ee0f4719b7ac7b52b47310a49320703c1d2639ebbc9fea"; //`sha256sum video_01.mp4`

        verify(mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(targetHash), startsWith(targetType),
                        eq(targetLength), nonEmptyMap());
        verifyNoJobOrMediaError();

        log.info("Skip media inspection test with invalid metadata passed.");
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
