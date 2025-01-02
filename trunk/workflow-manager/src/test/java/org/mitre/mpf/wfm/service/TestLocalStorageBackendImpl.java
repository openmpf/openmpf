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
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.mvc.controller.TestSubjectJobController;
import org.mitre.mpf.rest.api.subject.SubjectJobResult;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestLocalStorageBackendImpl extends MockitoTest.Strict {

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Mock
    private InProgressBatchJobsService _mockInProgressJobs;

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    private LocalStorageBackendImpl _localStorageBackend;

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    @Before
    public void init() {
        _localStorageBackend = new LocalStorageBackendImpl(
                _mockPropertiesUtil,
                _objectMapper,
                _mockInProgressJobs);
    }


    @Test
    public void outputObjectViewUsedWhenStoringSubjectResults() throws IOException {
        var outputObjectsDir = _tempFolder.newFolder("output-objects");
        Files.createDirectories(outputObjectsDir.toPath().resolve("123"));

        when(_mockPropertiesUtil.createOutputObjectsFile(123, "subject"))
            .thenReturn(outputObjectsDir.toPath().resolve("123/subject.json"));

        var jobDetails = TestSubjectJobController.createSubjectJobResult();
        var resultsUri = _localStorageBackend.store(jobDetails);
        assertThat(resultsUri.toString()).endsWith("/123/subject.json");

        var resultsString = Files.readString(Path.of(resultsUri));
        assertThat(resultsString).doesNotContain("outputUri", "callbackStatus");

        var parsedResults = _objectMapper.readValue(resultsString, SubjectJobResult.class);
        assertThat(parsedResults)
                .usingRecursiveComparison()
                .withEqualsForType(TestUtil::equalAfterTruncate, Instant.class)
                .ignoringFields("job.callbackStatus", "job.outputUri")
                .isEqualTo(jobDetails);
        assertThat(parsedResults.job().callbackStatus()).isNull();
        assertThat(parsedResults.job().outputUri()).isEmpty();
    }
}
