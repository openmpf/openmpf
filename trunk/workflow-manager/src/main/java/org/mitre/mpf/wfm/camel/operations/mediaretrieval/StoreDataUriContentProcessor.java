/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2025 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2025 The MITRE Corporation                                       *
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.camel.Exchange;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.IssueCodes;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.enums.UriScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.io.BaseEncoding;
import com.google.common.io.CharSource;
import com.google.common.io.MoreFiles;


@Component(StoreDataUriContentProcessor.REF)
public class StoreDataUriContentProcessor extends WfmProcessor {

    public static final String REF = "storeDataUriContentProcessor";

    private static final Logger LOG = LoggerFactory.getLogger(StoreDataUriContentProcessor.class);

    private final InProgressBatchJobsService _inProgressJobs;

    StoreDataUriContentProcessor(InProgressBatchJobsService inProgressJobs) {
        _inProgressJobs = inProgressJobs;
    }


    @Override
    public void wfmProcess(Exchange exchange) throws WfmProcessingException {
        long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
        var job = _inProgressJobs.getJob(jobId);
        job.getMedia()
                .stream()
                .filter(m -> m.getUriScheme() == UriScheme.DATA)
                .forEach(m -> copyToFile(jobId, m));
    }


    private void copyToFile(long jobId, Media media) {
        try {
            LOG.info(
                    "Copying the content of the data URI in media {} to {}",
                    media.getId(), media.getLocalPath());
            saveDataUriContent(jobId, media);
        }
        catch (Exception e) {
            _inProgressJobs.addError(
                jobId,
                media.getId(),
                IssueCodes.LOCAL_STORAGE,
                "Error saving data URI content to temporary file: " + e.getMessage());
        }
    }


    // Data URI Formats:
    // data:[<content-type>];,<percent-encoded-data>
    // data:[<content-type>];base64,<b64-encoded-data>
    private void saveDataUriContent(long jobId, Media media) throws IOException {
        // Data URIs must be percent encoded. getSchemeSpecificPart() handles decoding the percent
        // encoded data.
        var uriNoScheme = media.getUri().get().getSchemeSpecificPart();
        var uriSections = uriNoScheme.split(",", 2);
        if (uriSections.length < 2) {
            throw new WfmProcessingException(
                "The data URI is invalid because it does not contain a comma.");
        }
        var metadataSection = uriSections[0];
        var dataSection = uriSections[1];

        if (metadataSection.endsWith("base64")) {
            BaseEncoding.base64()
                    .decodingSource(CharSource.wrap(dataSection))
                    .copyTo(MoreFiles.asByteSink(media.getLocalPath()));
        }
        else {
            Files.writeString(media.getLocalPath(), dataSection, StandardCharsets.UTF_8);
        }

        int semiColonPos = metadataSection.indexOf(';');
        if (semiColonPos > 0) {
            _inProgressJobs.setMimeType(
                jobId, media.getId(), metadataSection.substring(0, semiColonPos));
        }
    }
}
