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

package org.mitre.mpf.wfm.camel.operations.mediainspection;

import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MediaType;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.FrameTimeInfo;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TestMediaMetadataValidator {

    private AutoCloseable _closeable;

    @InjectMocks
    private MediaMetadataValidator _mediaMetadataValidator;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    @Mock
    private AggregateJobPropertiesUtil _mockAggregateJobPropertiesUtil;


    @Before
    public void init() {
        _closeable = MockitoAnnotations.openMocks(this);
    }

    @After
    public void close() throws Exception {
        _closeable.close();
    }

    @Test
    public void testSkipAudioInspection() {
        assertInspectionSkipped(Map.of(
                "MIME_TYPE", "audio/vnd.wave",
                "MEDIA_HASH", "SOME_HASH",
                "DURATION", "10"),
                MediaType.AUDIO, -1, Set.of());
    }

    @Test
    public void testSkipGenericInspection() {
        assertInspectionSkipped(Map.of(
                "MIME_TYPE", "application/pdf",
                "MEDIA_HASH", "SOME_HASH"),
                MediaType.UNKNOWN, -1, Set.of());
    }

    @Test
    public void testSkipImageInspection() {
        assertInspectionSkipped(Map.of(
                "MIME_TYPE", "image/jpeg",
                "MEDIA_HASH", "SOME_HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "EXIF_ORIENTATION", "0",
                "ROTATION", "0",
                "HORIZONTAL_FLIP", "FALSE"),
                MediaType.IMAGE, 1, Set.of());
    }

    @Test
    public void testSkipVideoInspection() {
        assertInspectionSkipped(Map.of(
                "MIME_TYPE", "video/mp4",
                "MEDIA_HASH", "SOME_HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "FRAME_COUNT", "90",
                "FPS", "30",
                "DURATION", "3",
                "ROTATION", "0"),
                MediaType.VIDEO, 90, Set.of());

        var frameTimeInfoCaptor = ArgumentCaptor.forClass(FrameTimeInfo.class);
        verify(_mockInProgressJobs)
                .addFrameTimeInfo(eq(123L), eq(321L), frameTimeInfoCaptor.capture());
        var frameTimeInfo = frameTimeInfoCaptor.getValue();
        assertFalse(frameTimeInfo.hasConstantFrameRate());
        assertTrue(frameTimeInfo.requiresTimeEstimation());
        assertEquals(1_000, frameTimeInfo.getTimeMsFromFrame(30));
    }

    @Test
    public void doesNotSkipInspectionWithInvalidMetadata() {
        assertInspectionNotSkipped(Map.of(
                "MIME_TYPE", "video/mp4",
                "MEDIA_HASH", "SOME_HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "FRAME_COUNT", "90",
                "FPS", "0", // value should be non-zero
                "DURATION", "3",
                "ROTATION", "0"));
    }

    @Test
    public void doesNotSkipInspectForHeicMedia() {
        assertInspectionNotSkipped(Map.of(
                "MIME_TYPE", "image/heic",
                "MEDIA_HASH", "SOME_HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10"));
    }

    @Test
    public void doesNotSkipInspectForCrushedPng() {
        var providedMediaMetadata = ImmutableMap.of(
                "MIME_TYPE", "image/png",
                "MEDIA_HASH", "SOME_HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10");

        var mockMedia = createMockMedia(
                56, "/samples/pngdefry/lenna-crushed.png", providedMediaMetadata);
        setSkipInspectionProp(78, mockMedia, true);

        assertFalse(_mediaMetadataValidator.skipInspection(78, mockMedia));
        verifyWarningAdded(78, 56, "Cannot skip media inspection");
    }

    @Test
    public void skipsInspectForRegularPng() {
        var providedMediaMetadata = ImmutableMap.of(
                "MIME_TYPE", "image/png",
                "MEDIA_HASH", "SOME_HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10");

        var mockMedia = createMockMedia(
                34, "/samples/pngdefry/lenna-normal.png", providedMediaMetadata);
        setSkipInspectionProp(12, mockMedia, true);

        assertTrue(_mediaMetadataValidator.skipInspection(12, mockMedia));
        verifyNoWarningAdded();
    }

    @Test
    public void testSkipInspectionVideoToAudioFallback() {
        assertInspectionSkipped(Map.of(
                "MIME_TYPE", "video/mp4",
                "MEDIA_HASH", "SOME_HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "FRAME_COUNT", "90",
                // missing FPS
                "DURATION", "3",
                "ROTATION", "0"),
                MediaType.AUDIO, -1,
                Set.of("Missing required video metadata"));
    }

    @Test
    public void testSkipInspectionVideoToUnknownFallback() {
        assertInspectionSkipped(Map.of(
                "MIME_TYPE", "video/mp4",
                "MEDIA_HASH", "SOME_HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                "FRAME_COUNT", "90",
                "FPS", "30",
                // missing DURATION
                "ROTATION", "0"),
                MediaType.UNKNOWN, -1,
                Set.of("Missing required video metadata",
                       "Missing required audio metadata"));
    }


    @Test
    public void testWarningAddedWhenSkipPropertyPresentAndNoMetadataProvided() {
        var mockMedia = createMockMedia(100, Map.of());
        setSkipInspectionProp(101, mockMedia, true);
        assertFalse(_mediaMetadataValidator.skipInspection(101, mockMedia));
        verifyWarningAdded(101, 100, "Cannot skip media inspection");
        verifyInspectionInfoNotAdded();
    }


    @Test
    public void testWarningNotAddedWhenSkipPropertyFalseAndSomeMetadataProvided() {
        var mockMedia = createMockMedia(
                102,
                Map.of("MEDIA_HASH", "SOME_HASH", "MIME_TYPE", "video/mp4"));
        setSkipInspectionProp(103, mockMedia, false);
        assertFalse(_mediaMetadataValidator.skipInspection(103, mockMedia));
        verifyNoWarningAdded();
        verifyInspectionInfoNotAdded();
    }


    @Test
    public void testWarningNotAddedWhenSkipPropertyFalseAndNoMetadataProvided() {
        var mockMedia = createMockMedia(104, Map.of());
        setSkipInspectionProp(105, mockMedia, false);
        assertFalse(_mediaMetadataValidator.skipInspection(105, mockMedia));
        verifyNoWarningAdded();
        verifyInspectionInfoNotAdded();
    }


    private void assertInspectionSkipped(Map<String, String> providedMetadata, MediaType mediaType, int length,
                                         Set<String> warnings) {
        var mockMedia = createMockMedia(321, providedMetadata);
        setSkipInspectionProp(123, mockMedia, true);
        assertTrue(_mediaMetadataValidator.skipInspection(123, mockMedia));
        verify(_mockInProgressJobs)
                .addMediaInspectionInfo(eq(123L), eq(321L), eq(providedMetadata.get("MEDIA_HASH")), eq(mediaType),
                        eq(providedMetadata.get("MIME_TYPE")), eq(length), eq(providedMetadata));
        warnings.forEach(w -> verifyWarningAdded(123, 321, w));
    }

    private void assertInspectionNotSkipped(Map<String, String> providedMetadata) {
        var mockMedia = createMockMedia(321, providedMetadata);
        setSkipInspectionProp(123, mockMedia, true);
        assertFalse(_mediaMetadataValidator.skipInspection(123, mockMedia));
        verifyWarningAdded(123, 321, "Cannot skip media inspection");
        verifyInspectionInfoNotAdded();
    }


    private void verifyWarningAdded(long jobId, long mediaId, String warning) {
        verify(_mockInProgressJobs)
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MEDIA_INSPECTION),
                        contains(warning));
    }

    private void verifyNoWarningAdded() {
        verify(_mockInProgressJobs, never())
                .addWarning(anyLong(), anyLong(), any(), anyString());
    }


    private static Media createMockMedia(long mediaId, String mediaPath,
                                         Map<String, String> providedMetadata) {
        var mockMedia = mock(Media.class);
        when(mockMedia.getId())
                .thenReturn(mediaId);
        when(mockMedia.getProvidedMetadata())
                .thenReturn(ImmutableMap.copyOf(providedMetadata));

        if (mediaPath != null) {
            when(mockMedia.getLocalPath())
                    .thenReturn(TestUtil.findFilePath(mediaPath));
        }

        return mockMedia;
    }


    private static Media createMockMedia(long mediaId, Map<String, String> providedMetadata) {
        return createMockMedia(mediaId, null, providedMetadata);
    }


    private void setSkipInspectionProp(long jobId, Media media, boolean propValue) {
        var mockJob = mock(BatchJob.class);
        when(_mockInProgressJobs.getJob(jobId))
            .thenReturn(mockJob);
        when(_mockAggregateJobPropertiesUtil.getValue(
                MpfConstants.SKIP_MEDIA_INSPECTION, mockJob, media))
            .thenReturn(String.valueOf(propValue));
    }

    private void verifyInspectionInfoNotAdded() {
        verify(_mockInProgressJobs, never())
            .addMediaInspectionInfo(anyLong(), anyLong(), any(), any(), any(), anyInt(), any());
    }
}
