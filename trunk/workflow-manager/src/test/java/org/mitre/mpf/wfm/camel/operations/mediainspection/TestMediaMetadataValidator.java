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

package org.mitre.mpf.wfm.camel.operations.mediainspection;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mitre.mpf.test.TestUtil.nonBlank;
import static org.mockito.Mockito.*;

public class TestMediaMetadataValidator {

    @InjectMocks
    private MediaMetadataValidator _mediaMetadataValidator;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSkipAudioInspection() {
        assertInspectionSkipped(Map.of(
                "MIME_TYPE", "audio/wav",
                "MEDIA_HASH", "SOME_HASH",
                "DURATION", "10"));
    }

    @Test
    public void testSkipGenericInspection() {
        assertInspectionSkipped(Map.of(
                "MIME_TYPE", "application/pdf",
                "MEDIA_HASH", "SOME_HASH"));
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
                "HORIZONTAL_FLIP", "FALSE"));
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
                "ROTATION", "0"));
    }

    @Test
    public void testSkipInspectionWithMissingMetadata() {
        assertInspectionNotSkipped(Map.of(
                "MIME_TYPE", "video/mp4",
                "MEDIA_HASH", "SOME_HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10",
                // missing FRAME_COUNT
                "FPS", "30",
                "DURATION", "3",
                "ROTATION", "0"));
    }

    @Test
    public void testSkipInspectionWithInvalidMetadata() {
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

        assertFalse(_mediaMetadataValidator.skipInspection(78, mockMedia));
        verifyWarningAdded(78, 56);
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

        assertTrue(_mediaMetadataValidator.skipInspection(12, mockMedia));
        verifyNoWarningAdded();
    }


    private void assertInspectionSkipped(Map<String, String> providedMetadata) {
        var mockMedia = createMockMedia(321, providedMetadata);
        assertTrue(_mediaMetadataValidator.skipInspection(123, mockMedia));
        verifyNoWarningAdded();
    }

    private void assertInspectionNotSkipped(Map<String, String> providedMetadata) {
        var mockMedia = createMockMedia(321, providedMetadata);
        assertFalse(_mediaMetadataValidator.skipInspection(123, mockMedia));
        verifyWarningAdded(123, 321);
    }


    private void verifyWarningAdded(long jobId, long mediaId) {
        verify(_mockInProgressJobs)
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MEDIA_INSPECTION),
                            nonBlank());
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
}
