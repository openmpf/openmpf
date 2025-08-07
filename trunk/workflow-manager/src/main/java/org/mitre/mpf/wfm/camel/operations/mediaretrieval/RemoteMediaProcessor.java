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

package org.mitre.mpf.wfm.camel.operations.mediaretrieval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.http.client.methods.HttpGet;
import org.mitre.mpf.mvc.security.OutgoingRequestTokenService;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.mitre.mpf.wfm.service.S3StorageBackend;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.HttpClientUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mitre.mpf.wfm.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.base.Preconditions;

/** This processor downloads a file from a remote URI to the local filesystem. */
@Component(RemoteMediaProcessor.REF)
public class RemoteMediaProcessor extends WfmProcessor {
    public static final String REF = "remoteMediaProcessor";
    private static final Logger log = LoggerFactory.getLogger(RemoteMediaProcessor.class);

    private final InProgressBatchJobsService _inProgressJobs;

    private final S3StorageBackend _s3Service;

    private final PropertiesUtil _propertiesUtil;

    private final AggregateJobPropertiesUtil _aggregateJobPropertiesUtil;

    private final HttpClientUtils _httpClient;

    private final OutgoingRequestTokenService _tokenService;

    @Inject
    public RemoteMediaProcessor(
            InProgressBatchJobsService inProgressJobs,
            S3StorageBackend s3Service,
            PropertiesUtil propertiesUtil,
            AggregateJobPropertiesUtil aggregateJobPropertiesUtil,
            HttpClientUtils httpClient,
            OutgoingRequestTokenService tokenService) {
        _inProgressJobs = inProgressJobs;
        _s3Service = s3Service;
        _propertiesUtil = propertiesUtil;
        _aggregateJobPropertiesUtil = aggregateJobPropertiesUtil;
        _httpClient = httpClient;
        _tokenService = tokenService;
    }

    private static final Set<UriScheme> SUPPORTED_URI_SCHEMES
            = EnumSet.of(UriScheme.HTTP, UriScheme.HTTPS);

    public static boolean supports(UriScheme scheme) {
        return SUPPORTED_URI_SCHEMES.contains(scheme);
    }


    @Override
    public void wfmProcess(Exchange exchange) {
        long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        long mediaId = exchange.getIn().getHeader(MpfHeaders.MEDIA_ID, Long.class);
        BatchJob job = _inProgressJobs.getJob(jobId);
        Media media = job.getMedia(mediaId);

        Preconditions.checkArgument(
            supports(media.getUriScheme()), "Unsupported URI scheme: %s");
        copyHeaders(exchange);

        var combinedProperties = _aggregateJobPropertiesUtil.getCombinedProperties(job, media);
        try {
            if (S3StorageBackend.requiresS3MediaDownload(combinedProperties)) {
                _s3Service.downloadFromS3(media, combinedProperties);
            }
            else {
                downloadMedia(job, media);
            }
        }
        catch (Exception e) {
            var message = handleMediaRetrievalException(media, media.getLocalPath(), e);
            handleMediaRetrievalFailure(jobId, media, message);
        }
    }

    private static final List<String> HEADERS_TO_COPY = List.of(
            MpfHeaders.MEDIA_ID,
            MpfHeaders.CORRELATION_ID,
            MpfHeaders.SPLIT_SIZE,
            MpfHeaders.JMS_PRIORITY
    );

    private static void copyHeaders(Exchange exchange) {
        var inHeaders = exchange.getIn().getHeaders();
        var outHeaders = exchange.getOut().getHeaders();
        for (var headerName : HEADERS_TO_COPY) {
            var inValue = inHeaders.get(headerName);
            if (inValue != null) {
                outHeaders.put(headerName, inValue);
            }
        }
    }


    private void downloadMedia(BatchJob job, Media media) throws IOException {
        var request = new HttpGet(media.getUri().uri());
        _tokenService.addTokenToRemoteMediaDownloadRequest(job, media, request);

        var response = _httpClient.downloadResponseSync(
                request, media.getLocalPath(), _propertiesUtil.getRemoteMediaDownloadRetries());
        var statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 200 && statusCode <= 299) {
            log.info(
                    "Successfully downloaded media from \"{}\" and stored it at \"{}\"",
                    media.getUri(), media.getLocalPath());
        }
        else {
            throw new WfmProcessingException(
                    "Received non-200 status code while trying to download: "
                    + media.getUri());
        }
    }


    private static void deleteOrLeakFile(Path path) {
        try {
            if (path != null) {
                Files.delete(path);
            }
        }
        catch (Exception exception) {
            log.warn("Failed to delete the local file '{}'. If it exists, it must be deleted manually.", path);
        }
    }


    private static String handleMediaRetrievalException(Media media, Path localFile, Exception e) {
        log.warn("Failed to retrieve {}.", media.getUri(), e);
        // Try to delete the local file, but do not throw an exception if this operation fails.
        deleteOrLeakFile(localFile);
        return e.toString();
    }

    private void handleMediaRetrievalFailure(long jobId, Media media,
                                             String errorMessage) {
        _inProgressJobs.addError(jobId, media.getId(), IssueCodes.REMOTE_STORAGE_DOWNLOAD,
                                 "Error retrieving media and saving it to temp file: " + errorMessage);
    }
}
