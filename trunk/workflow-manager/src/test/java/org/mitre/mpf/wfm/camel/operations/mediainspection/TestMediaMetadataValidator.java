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
    public void doesNotSkipInspectForHeicMedia() {
        var providedMediaMetadata = ImmutableMap.of(
                "MIME_TYPE", "image/heic",
                "MEDIA_HASH", "HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10");

        var mockMedia = mock(Media.class);
        when(mockMedia.getId())
                .thenReturn(12L);
        when(mockMedia.getProvidedMetadata())
                .thenReturn(providedMediaMetadata);

        assertFalse(_mediaMetadataValidator.skipInspection(34, mockMedia));
        verifyWarningAdded(34, 12);
    }


    @Test
    public void doesNotSkipInspectForCrushedPng() {
        var providedMediaMetadata = ImmutableMap.of(
                "MIME_TYPE", "image/png",
                "MEDIA_HASH", "HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10");

        var mockMedia = mock(Media.class);
        when(mockMedia.getId())
                .thenReturn(56L);
        when(mockMedia.getLocalPath())
                .thenReturn(TestUtil.findFilePath("/samples/pngdefry/lenna-crushed.png"));
        when(mockMedia.getProvidedMetadata())
                .thenReturn(providedMediaMetadata);

        assertFalse(_mediaMetadataValidator.skipInspection(78, mockMedia));
        verifyWarningAdded(78, 56);
    }


    @Test
    public void skipsInspectForRegularPng() {
        var providedMediaMetadata = ImmutableMap.of(
                "MIME_TYPE", "image/png",
                "MEDIA_HASH", "HASH",
                "FRAME_WIDTH", "10",
                "FRAME_HEIGHT", "10");

        var mockMedia = mock(Media.class);
        when(mockMedia.getId())
                .thenReturn(123L);
        when(mockMedia.getLocalPath())
                .thenReturn(TestUtil.findFilePath("/samples/pngdefry/lenna-normal.png"));
        when(mockMedia.getProvidedMetadata())
                .thenReturn(providedMediaMetadata);

        assertTrue(_mediaMetadataValidator.skipInspection(456, mockMedia));
        verify(_mockInProgressJobs, never())
                .addWarning(anyLong(), anyLong(), any(), any());
    }


    private void verifyWarningAdded(long jobId, long mediaId) {
        verify(_mockInProgressJobs)
                .addWarning(eq(jobId), eq(mediaId), eq(IssueCodes.MEDIA_INSPECTION),
                            nonBlank());
    }
}
