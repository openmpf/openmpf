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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.http.client.methods.HttpGet;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PastJobResultsService {

    private final JobRequestDao _jobRequestDao;

    private final ObjectMapper _objectMapper;

    private final JsonUtils _jsonUtils;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final S3StorageBackend _s3StorageBackend;

    private final PropertiesUtil _propertiesUtil;

    private final HttpClientUtils _httpClient;

    @Inject
    PastJobResultsService(
            JobRequestDao jobRequestDao,
            JsonUtils jsonUtils,
            ObjectMapper objectMapper,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            S3StorageBackend s3StorageBackend,
            PropertiesUtil propertiesUtil,
            HttpClientUtils httpClient) {
        _jobRequestDao = jobRequestDao;
        _jsonUtils = jsonUtils;
        _objectMapper = objectMapper;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _s3StorageBackend = s3StorageBackend;
        _propertiesUtil = propertiesUtil;
        _httpClient = httpClient;
    }


    public JsonOutputObject getJobResults(long jobId) {
        try (var is = getJobResultsStream(jobId)) {
            return _objectMapper.readValue(is, JsonOutputObject.class);
        }
        catch (IOException e) {
            throw new WfmProcessingException(
                "Failed to parse results for job %s due to: %s".formatted(jobId, e), e);
        }
    }


    public InputStream getJobResultsStream(long jobId) {
        return getLocalJobResultsStream(jobId)
            .or(() -> getTiesDbJobResultsStream(jobId))
            .orElseThrow(() -> new WfmProcessingException(
                    "Job %s was not found in the database." /* or TiesDb. */.formatted(jobId)));
    }


    private Optional<InputStream> getLocalJobResultsStream(long jobId) {
        var jobRequest = _jobRequestDao.findById(jobId);
        if (jobRequest == null) {
            return Optional.empty();
        }

        if (jobRequest.getOutputObjectPath() == null) {
            throw new WfmProcessingException(
                "Job %s did not produce any output.".formatted(jobId));
        }

        var outputObjectUri = URI.create(jobRequest.getOutputObjectPath());
        if ("file".equalsIgnoreCase(outputObjectUri.getScheme())) {
            try {
                return Optional.of(Files.newInputStream(Path.of(outputObjectUri)));
            }
            catch (IOException e) {
                throw new WfmProcessingException(
                    "Failed to open the results for job %s at \"%s\" due to: %s"
                    .formatted(jobId, outputObjectUri, e), e);
            }
        }

        var job = _jsonUtils.deserialize(jobRequest.getJob(), BatchJob.class);
        var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(job);
        try {
            if (S3StorageBackend.requiresS3ResultUpload(combinedProperties)) {
                return Optional.of(_s3StorageBackend.getFromS3(
                    jobRequest.getOutputObjectPath(), combinedProperties));
            }
            else {
                var request = new HttpGet(outputObjectUri);
                var future = _httpClient.executeRequest(
                        request, _propertiesUtil.getHttpCallbackRetryCount());

                var result = ThreadUtil.join(future).getEntity().getContent();
                return Optional.of(result);
            }
        }
        catch (StorageException | IOException e) {
            throw new WfmProcessingException(
                "Failed to get the results for job %s at \"%s\" due to: %s"
                .formatted(jobId, outputObjectUri, e), e);
        }
    }

    private Optional<InputStream> getTiesDbJobResultsStream(long jobId) {
        // TODO: Figure out how to find a supplemental by job id
        return Optional.empty();
    }
}
