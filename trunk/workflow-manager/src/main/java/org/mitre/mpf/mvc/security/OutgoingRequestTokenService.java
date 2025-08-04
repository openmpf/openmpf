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

package org.mitre.mpf.mvc.security;

import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.http.client.methods.HttpUriRequest;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectJob;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.JobPart;
import org.mitre.mpf.wfm.util.JobPartsIter;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.http.SdkHttpFullRequest;

@Service
public class OutgoingRequestTokenService {

    private final ITokenProvider _tokenProvider;

    private final TokenRequiredCheck _tokenRequired;


    @Inject
    OutgoingRequestTokenService(
            AggregateJobPropertiesUtil aggregateJobProps,
            Optional<ITokenProvider> tokenProvider) {
        if (tokenProvider.isPresent()) {
            _tokenProvider = tokenProvider.get();
            _tokenRequired = new TokenRequiredCheckEnabled(aggregateJobProps);
        }
        else {
            _tokenProvider = null;
            _tokenRequired = TOKENS_NOT_REQUIRED;
        }
    }


    public void addTokenToJobCompleteCallback(BatchJob job, HttpUriRequest request) {
        if (_tokenRequired.jobCompleteCallbackNeedsToken(job)) {
            _tokenProvider.addToken(request);
        }
    }


    public void addTokenToJobCompleteCallback(DbSubjectJob job, HttpUriRequest request) {
        if (_tokenRequired.jobCompleteCallbackNeedsToken(job)) {
            _tokenProvider.addToken(request);
        }
    }


    public void addTokenToTiesDbRequest(BatchJob job, Media media, HttpUriRequest request) {
        if (_tokenRequired.tiesDbRequestNeedsToken(job, media)) {
            _tokenProvider.addToken(request);
        }
    }


    public void addTokenToTiesDbRequest(UnaryOperator<String> props, HttpUriRequest request) {
        if (_tokenRequired.tiesDbRequestNeedsToken(props)) {
            _tokenProvider.addToken(request);
        }
    }


    public SdkHttpFullRequest addTokenToS3Request(
            UnaryOperator<String> props, SdkHttpFullRequest s3Request) {
        if (!_tokenRequired.s3NeedsToken(props)) {
            return s3Request;
        }
        var builder = s3Request.toBuilder();
        _tokenProvider.addToken(builder);
        return builder.build();
    }



    private static class TokenRequiredCheck {
        public boolean jobCompleteCallbackNeedsToken(BatchJob job) {
            return false;
        }

        public boolean jobCompleteCallbackNeedsToken(DbSubjectJob job) {
            return false;
        }

        public boolean tiesDbRequestNeedsToken(BatchJob job, Media media) {
            return false;
        }

        public boolean tiesDbRequestNeedsToken(UnaryOperator<String> props) {
            return false;
        }

        public boolean s3NeedsToken(UnaryOperator<String> props) {
            return false;
        }
    }

    private static TokenRequiredCheck TOKENS_NOT_REQUIRED = new TokenRequiredCheck();


    private static class TokenRequiredCheckEnabled extends TokenRequiredCheck {

        private final AggregateJobPropertiesUtil _aggregateJobProps;

        public TokenRequiredCheckEnabled(AggregateJobPropertiesUtil aggregateJobProps) {
            _aggregateJobProps = aggregateJobProps;
        }

        @Override
        public boolean jobCompleteCallbackNeedsToken(BatchJob job) {
            return needsToken(JobPartsIter.stream(job), MpfConstants.CALLBACK_ADD_TOKEN);
        }

        @Override
        public boolean jobCompleteCallbackNeedsToken(DbSubjectJob job) {
            return _aggregateJobProps.getValue(MpfConstants.CALLBACK_ADD_TOKEN, job)
                .map(Boolean::parseBoolean)
                .orElse(false);
        }

        @Override
        public boolean tiesDbRequestNeedsToken(BatchJob job, Media media) {
            return needsToken(JobPartsIter.stream(job, media), MpfConstants.TIES_DB_ADD_TOKEN);
        }

        @Override
        public boolean tiesDbRequestNeedsToken(UnaryOperator<String> props) {
            return needsToken(props, MpfConstants.TIES_DB_ADD_TOKEN);
        }

        @Override
        public boolean s3NeedsToken(UnaryOperator<String> props) {
            return needsToken(props, MpfConstants.S3_ADD_TOKEN);
        }

        private boolean needsToken(Stream<JobPart> jobParts, String propName) {
            return jobParts
                .map(jp -> _aggregateJobProps.getValue(propName, jp))
                .anyMatch(Boolean::parseBoolean);
        }

        private boolean needsToken(UnaryOperator<String> props, String propName) {
            return Boolean.parseBoolean(props.apply(propName));
        }
    }
}
