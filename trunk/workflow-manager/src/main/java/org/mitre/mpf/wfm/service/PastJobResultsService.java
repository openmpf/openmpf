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
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import javax.inject.Inject;

import org.apache.http.client.methods.HttpGet;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.access.JobRequestDao;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
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


    public JsonOutputObject getDetectionJobResults(long jobId) {
        try (var is = getDetectionJobResultsStream(jobId)) {
            return _objectMapper.readValue(is, JsonOutputObject.class);
        }
        catch (IOException e) {
            throw new WfmProcessingException(
                "Failed to parse results for job %s due to: %s".formatted(jobId, e), e);
        }
    }


    public InputStream getDetectionJobResultsStream(long jobId) {
        return getLocalJobResultsStream(jobId)
            .or(() -> getTiesDbJobResultsStream(jobId))
            .orElseThrow(() -> new WfmProcessingException(
                    "Job %s was not found in the database." /* or TiesDb. */.formatted(jobId)));
    }


    public InputStream getJobResultsStream(DbSubjectJob dbJob) {
        if (!dbJob.isComplete()) {
            throw new WfmProcessingException(
                    "Can not get results for job %s because it is still running."
                    .formatted(dbJob.getId()));
        }
        var outputUri = dbJob.getOutputUri().orElseThrow(
                () -> new WfmProcessingException(
                    "Job %s did not produce any output.".formatted(dbJob.getId())));
        return getLocalJobResultsStream(
                dbJob.getId(),
                outputUri,
                () -> _aggregateJobPropertiesUtil.getCombinedProperties(dbJob));
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
        var result = getLocalJobResultsStream(jobId, outputObjectUri, () -> {
            var job = _jsonUtils.deserialize(jobRequest.getJob(), BatchJob.class);
            return _aggregateJobPropertiesUtil.getCombinedProperties(job);
        });
        return Optional.of(result);
    }


    private InputStream getLocalJobResultsStream(
            long jobId,
            URI outputUri,
            Supplier<UnaryOperator<String>> propsSupplier) {
        if ("file".equalsIgnoreCase(outputUri.getScheme())) {
            try {
                return Files.newInputStream(Path.of(outputUri));
            }
            catch (IOException e) {
                throw new WfmProcessingException(
                    "Failed to open the results for job %s at \"%s\" due to: %s"
                    .formatted(jobId, outputUri, e), e);
            }
        }

        var combinedProperties = propsSupplier.get();
        try {
            if (S3StorageBackend.requiresS3ResultUpload(combinedProperties)) {
                return _s3StorageBackend.getFromS3(outputUri.toString(), combinedProperties);
            }
            else {
                var request = new HttpGet(outputUri);
                var future = _httpClient.executeRequest(
                        request, _propertiesUtil.getHttpCallbackRetryCount());
                return ThreadUtil.join(future).getEntity().getContent();
            }
        }
        catch (StorageException | IOException e) {
            throw new WfmProcessingException(
                "Failed to get the results for job %s at \"%s\" due to: %s"
                .formatted(jobId, outputUri, e), e);
        }
    }

    private Optional<InputStream> getTiesDbJobResultsStream(long jobId) {
        // TODO: Figure out how to find a supplemental by job id
        return Optional.empty();
    }
}
