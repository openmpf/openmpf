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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.Mock;

public class TestLocalStorageBackendImpl extends MockitoTest.Strict {

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    private LocalStorageBackendImpl _localStorageBackend;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    @Before
    public void init() {
        _localStorageBackend = new LocalStorageBackendImpl(
                _mockPropertiesUtil,
                ObjectMapperFactory.customObjectMapper(),
                _mockInProgressJobs);
    }


    @Test
    public void testStoreMediaSelectorsOutput() throws IOException {
        assertThat(_localStorageBackend.canStoreMediaSelectorsOutput(null, null))
                .isTrue();

        var mediaSelectorsOutputDir = _tempFolder.newFolder().toPath();
        when(_mockPropertiesUtil.getMediaSelectorsOutputDir())
                .thenReturn(mediaSelectorsOutputDir);

        var job = mock(BatchJob.class);
        when(job.getId())
                .thenReturn(123L);

        var media = mock(Media.class);
        when(media.getId())
                .thenReturn(456L);

        var outputUri = _localStorageBackend.storeMediaSelectorsOutput(
                job, media, MediaSelectorType.JSON_PATH,
                os -> os.write("<TEST CONTENT>".getBytes(StandardCharsets.UTF_8)));

        var expectedPath = mediaSelectorsOutputDir.resolve(
                "123/456/media-selectors-output.json");

        assertThat(outputUri).isEqualTo(expectedPath.toUri());

        var outputPath = Path.of(outputUri);
        assertThat(outputPath)
                .content(StandardCharsets.UTF_8)
                .isEqualTo("<TEST CONTENT>");
    }
}
