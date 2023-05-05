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

package org.mitre.mpf.wfm.camelOps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.test.TestUtil.nonBlank;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.exception.TikaException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.camel.operations.mediainspection.FfprobeMetadataExtractor;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionHelper;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaInspectionProcessor;
import org.mitre.mpf.wfm.camel.operations.mediainspection.MediaMetadataValidator;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mitre.mpf.wfm.util.JniLoader;
import org.mitre.mpf.wfm.util.MediaTypeUtils;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.xml.sax.SAXException;


public class TestMediaInspectionProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(TestMediaInspectionProcessor.class);
    private static final int MINUTES = 1000*60; // 1000 milliseconds/second & 60 seconds/minute.

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final InProgressBatchJobsService _mockInProgressJobs
            = mock(InProgressBatchJobsService.class);

    private final MediaMetadataValidator _mockMediaMetadataValidator
            = mock(MediaMetadataValidator.class);

    private MediaInspectionHelper _mediaInspectionHelper;

    private MediaInspectionProcessor _mediaInspectionProcessor;

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, String>> _metadataCaptor
            = ArgumentCaptor.forClass(Map.class);

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
    public void init() throws TikaException, IOException, SAXException, ConfigurationException {
        var mediaTypePropertiesPath = TestUtil.findFilePath("/properties/mediaType.properties");
        when(_mockPropertiesUtil.getMediaTypesFile())
            .thenReturn(new FileSystemResource(mediaTypePropertiesPath.toFile()));
        var mediaTypeUtils = new MediaTypeUtils(_mockPropertiesUtil);

        _mediaInspectionHelper = new MediaInspectionHelper(
                _mockPropertiesUtil, _mockInProgressJobs, new IoUtils(),
                mediaTypeUtils,
                _mockMediaMetadataValidator,
                new FfprobeMetadataExtractor(ObjectMapperFactory.customObjectMapper()));

        _mediaInspectionProcessor = new MediaInspectionProcessor(
                _mockInProgressJobs, _mediaInspectionHelper);

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
                                        eq(1), _metadataCaptor.capture());
        verifyNoJobOrMediaError();

        var expectedMetadata = Map.of(
                "FRAME_WIDTH", "480",
                "FRAME_HEIGHT", "600",
                "MIME_TYPE", "image/jpeg");
        assertEquals(expectedMetadata, _metadataCaptor.getValue());

        LOG.info("Image media inspection test passed.");
    }


    @Test
    public void testGifImageInspection() {
        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/face-morphing.gif", Map.of());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "1d53c3a2344b01025baba6e11be06625cf606a4a1a22107735c2c945c04e4dfa";

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(
                        eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO),
                        eq("image/gif"), eq(29), _metadataCaptor.capture());

        verifyNoJobOrMediaError();
        var expectedMetadata = Map.of(
                "FRAME_WIDTH", "308",
                "FRAME_HEIGHT", "400",
                "MIME_TYPE", "image/gif",
                "ROTATION", "0.0",
                "FRAME_COUNT", "29",
                "DURATION", "3500");

        assertVideoMetadataMatches(expectedMetadata, 53.0 / 6);
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

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO), eq("video/mp4"),
                        eq(frameCount), _metadataCaptor.capture());
        verifyNoJobOrMediaError();

        var expectedMetadata = Map.of(
            "FRAME_COUNT", "90",
            "FRAME_WIDTH", "640",
            "FRAME_HEIGHT", "480",
            "MIME_TYPE", "video/mp4",
            "DURATION", "3042",
            "HAS_CONSTANT_FRAME_RATE", "true");
        assertVideoMetadataMatches(expectedMetadata, 30000.0 / 1001);

        LOG.info("Video media inspection test passed.");
    }


    @Test(timeout = 5 * MINUTES)
    public void testRotatedVideoInspection() {
        LOG.info("Starting rotated video media inspection test.");

        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/new_face_video-90.mp4",
                                       Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.",
                                  media.getErrorMessage()),
                    media.isFailed());

        String mediaHash = "c059a6fe6d6340bebc20eb8f2803317b6c9b57e7fcec60f4e352062a7b800be4";

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO),
                                        eq("video/mp4"), eq(511),
                                        _metadataCaptor.capture());
        verifyNoJobOrMediaError();

        var expectedMetadata = Map.of(
            "FRAME_COUNT", "511",
            "FRAME_WIDTH", "480",
            "FRAME_HEIGHT", "640",
            "MIME_TYPE", "video/mp4",
            "DURATION", "17067",
            "ROTATION", "90.0",
            "HAS_CONSTANT_FRAME_RATE", "true");
        assertVideoMetadataMatches(expectedMetadata, 30000.0 / 1001);

        LOG.info("Rotated video media inspection test passed.");
    }


    @Test
    public void testVideoInspectionWithMissingTimes() {
        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/text-test-video-detection.avi", Collections.emptyMap());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "df75164cccd48821d31527aa02c9ded530a8eb7f728d8c50731a9a5f3845d7a6";

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO), eq("video/x-msvideo"),
                        eq(3), _metadataCaptor.capture());
        verifyNoJobOrMediaError();

        var expectedMetadata = Map.of(
            "FRAME_COUNT", "3",
            "FRAME_WIDTH", "852",
            "FRAME_HEIGHT", "480",
            "MIME_TYPE", "video/x-msvideo",
            "DURATION", "2261");
        assertVideoMetadataMatches(expectedMetadata, 223.0 / 12);
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
                        eq("audio/vnd.wave"), eq(-1), _metadataCaptor.capture());
        verifyNoJobOrMediaError();

        var expectedMetadata = Map.of(
            "MIME_TYPE", "audio/vnd.wave",
            "DURATION", "2200"
        );
        assertEquals(expectedMetadata, _metadataCaptor.getValue());

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
                        eq(-1), _metadataCaptor.capture());
        verifyNoJobOrMediaError();

        var expectedMetadata = Map.of(
            "MIME_TYPE", "video/mp4",
            "DURATION", "17415"
        );
        assertEquals(expectedMetadata, _metadataCaptor.getValue());

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
                        eq(-1), _metadataCaptor.capture());
        verifyNoJobOrMediaError();

        assertEquals(Map.of("MIME_TYPE", "video/mp4"), _metadataCaptor.getValue());

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
                        eq("audio/x-hx-aac-adts"), eq(-1), _metadataCaptor.capture());
        verifyNoJobOrMediaError();

        var expectedMetadata = Map.of(
            "MIME_TYPE", "audio/x-hx-aac-adts",
            "DURATION", "2304"
        );
        assertEquals(expectedMetadata, _metadataCaptor.getValue());

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

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO),
                        eq("video/vnd.dlna.mpeg-tts"), eq(10), _metadataCaptor.capture());

        verifyNoJobOrMediaError();
        var mediaMetadata = _metadataCaptor.getValue();
        assertFalse(Boolean.parseBoolean(mediaMetadata.get("HAS_CONSTANT_FRAME_RATE")));

        var expectedMetadata = Map.of(
                "FRAME_COUNT", "10",
                "FRAME_WIDTH", "1920",
                "FRAME_HEIGHT", "1080",
                "MIME_TYPE", "video/vnd.dlna.mpeg-tts",
                "DURATION", "1130");
        assertVideoMetadataMatches(expectedMetadata, 24);

        LOG.info("ts file test passed.");
    }


    @Test
    public void testVideoMissingDuration() {
        long jobId = next(), mediaId = next();
        MediaImpl media = inspectMedia(jobId, mediaId, "/samples/test4.mkv", Map.of());

        assertFalse(String.format("The response entity must not fail. Message: %s.", media.getErrorMessage()),
                media.isFailed());

        String mediaHash = "43df750a2a01a37949791b717051b41522081a266b71d113be4b713063843699";

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(
                        eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.VIDEO),
                        eq("video/x-matroska"), eq(1642), _metadataCaptor.capture());

        verifyNoJobOrMediaError();
        var expectedMetadata = Map.of(
                "FRAME_WIDTH", "1280",
                "FRAME_HEIGHT", "720",
                "MIME_TYPE", "video/x-matroska",
                "ROTATION", "0.0",
                "FRAME_COUNT", "1642",
                "DURATION", "68417");

        assertVideoMetadataMatches(expectedMetadata, 24 / 1);
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

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.IMAGE),
                                        eq("image/png"), eq(1),
                                        _metadataCaptor.capture());

        var expectedMetadata = Map.of(
                "FRAME_WIDTH", "512",
                "FRAME_HEIGHT", "512",
                "MIME_TYPE", "image/png");
        assertEquals(expectedMetadata, _metadataCaptor.getValue());

        var pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(_mockInProgressJobs)
                .addConvertedMediaPath(eq(jobId), eq(mediaId), pathCaptor.capture());
        var defriedPath = pathCaptor.getValue();
        assertTrue(defriedPath.startsWith(_tempFolder.getRoot().toPath()));
        assertTrue(defriedPath.getFileName().toString().startsWith("lenna-crushed_defried"));
        assertTrue(defriedPath.getFileName().toString().endsWith(".png"));
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

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.IMAGE),
                                        eq("image/heic"), eq(1),
                                        _metadataCaptor.capture());

        var expectedMetadata = Map.of(
                "FRAME_WIDTH", "3024",
                "FRAME_HEIGHT", "4032",
                "MIME_TYPE", "image/heic");
        assertEquals(expectedMetadata, _metadataCaptor.getValue());

        var pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(_mockInProgressJobs)
                .addConvertedMediaPath(eq(jobId), eq(mediaId), pathCaptor.capture());
        var pngPath = pathCaptor.getValue();
        assertTrue(pngPath.startsWith(_tempFolder.getRoot().toPath()));
        assertTrue(pngPath.getFileName().toString().endsWith(".png"));
        assertTrue(Files.exists(pngPath));
    }


    @Test
    public void testWebpExtraBytes() throws IOException {
        when(_mockPropertiesUtil.getTemporaryMediaDirectory())
                .thenReturn(_tempFolder.getRoot());

        long jobId = next();
        long mediaId = next();
        var media = inspectMedia(
            jobId, mediaId, "/samples/Johnrogershousemay2020-extra-bytes.webp", Map.of());

        assertFalse(String.format("The response entity must not fail. Message: %s.",
                                  media.getErrorMessage()),
                    media.isFailed());

        verifyNoJobOrMediaError();

        var mediaHash = "b9ef08ce73c945d62a3bf48566377c0d9f99fe0b07810affe40c53f67d8afad2";

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), eq(mediaHash), eq(MediaType.IMAGE),
                                        eq("image/webp"), eq(1),
                                        metadataCaptor.capture());
        var metadata = metadataCaptor.getValue();
        assertEquals("1536", metadata.get("FRAME_WIDTH"));
        assertEquals("1024", metadata.get("FRAME_HEIGHT"));

        var pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(_mockInProgressJobs)
                .addConvertedMediaPath(eq(jobId), eq(mediaId), pathCaptor.capture());

        var fixedPath = pathCaptor.getValue();
        assertTrue(fixedPath.startsWith(_tempFolder.getRoot().toPath()));
        assertTrue(fixedPath.getFileName().toString().endsWith(".webp"));
        assertTrue(Files.exists(fixedPath));

        try (var is = Files.newInputStream(fixedPath)) {
            var actualFixedSha = DigestUtils.sha256Hex(is);
            var expectedFixedSha = "4cf3e271105fc5ec4c57980a652125a9436479bc5021a05c72c80b0a477d749c";
            assertEquals(expectedFixedSha, actualFixedSha);
        }
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

        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(jobId), eq(mediaId), notNull(), eq(MediaType.IMAGE),
                                        eq("image/jpeg"), eq(1),
                                        _metadataCaptor.capture());

        var expectedMetadata = Map.of(
                "ROTATION", "0",
                "EXIF_ORIENTATION", "1",
                "FRAME_WIDTH", "809",
                "FRAME_HEIGHT", "606",
                "HORIZONTAL_FLIP", "FALSE",
                "MIME_TYPE", "image/jpeg");
        assertEquals(expectedMetadata, _metadataCaptor.getValue());
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
                mediaId, mediaUri.toString(), UriScheme.get(mediaUri), Paths.get(mediaUri),
                Map.of(), mediaMetadata, List.of(), List.of(), null);
        Exchange exchange = setupExchange(jobId, media);
        _mediaInspectionProcessor.process(exchange);

        assertEquals("Media ID headers must be set.", mediaId, exchange.getOut().getHeader(MpfHeaders.MEDIA_ID));
        assertEquals("Job ID headers must be set.", jobId, exchange.getOut().getHeader(MpfHeaders.JOB_ID));

        return media;
    }

    private void assertVideoMetadataMatches(
            Map<String, String> expectedMetadata, double expectedFps) {

        var actualMetadata = _metadataCaptor.getValue();
        for (var expectedEntry : expectedMetadata.entrySet()) {
            assertEquals(expectedEntry.getValue(), actualMetadata.get(expectedEntry.getKey()));
        }

        double actualFps = Double.parseDouble(actualMetadata.get("FPS"));
        assertEquals(expectedFps, actualFps, 0.0001);
    }
}
