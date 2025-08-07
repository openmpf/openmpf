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

package org.mitre.mpf.wfm.util;

import java.io.IOException;
import java.util.Optional;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class ForwardHttpResponseUtil {

    private ForwardHttpResponseUtil() {
    }


    public static ResponseEntity<InputStreamResource> createResponseEntity(
            HttpResponse respToForward) throws IOException {
        if (respToForward.getStatusLine().getStatusCode() >= 400) {
            return createErrorStatusResponse(respToForward);
        }

        var entityToForward = respToForward.getEntity();
        return setEntityHeaders(entityToForward, ResponseEntity.ok())
                .body(new InputStreamResource(entityToForward.getContent()));
    }


    public static ResponseEntity<InputStreamResource> createResponseEntity(
            ResponseInputStream<GetObjectResponse> s3Stream) {
        var s3Resp = s3Stream.response();
        var respBuilder = ResponseEntity.ok();
        Optional.ofNullable(s3Resp.contentLength())
                .filter(cl -> cl > 0)
                .ifPresent(respBuilder::contentLength);

        createMediaType(s3Resp.contentType())
                .ifPresent(respBuilder::contentType);
        return respBuilder.body(new InputStreamResource(s3Stream));
    }



    private static ResponseEntity<InputStreamResource> createErrorStatusResponse(
            HttpResponse respToForward) throws  IOException {
        var respBuilder = ResponseEntity.status(respToForward.getStatusLine().getStatusCode());
        if (respToForward.getEntity() == null) {
            return respBuilder.build();
        }
        return setEntityHeaders(respToForward.getEntity(), respBuilder)
                .body(new InputStreamResource(respToForward.getEntity().getContent()));
    }


    private static ResponseEntity.BodyBuilder setEntityHeaders(
            HttpEntity entity,
            ResponseEntity.BodyBuilder respBuilder) {
        long contentLength = entity.getContentLength();
        if (contentLength > 0) {
            respBuilder.contentLength(contentLength);
        }
        Optional.ofNullable(entity.getContentType())
                .map(Header::getValue)
                .flatMap(ct -> createMediaType(ct))
                .ifPresent(respBuilder::contentType);
        return respBuilder;
    }

    private static Optional<MediaType> createMediaType(String contentType) {
        try {
            return Optional.ofNullable(contentType)
                .map(MediaType::parseMediaType);
        }
        catch (InvalidMediaTypeException e) {
            return Optional.empty();
        }
    }
}
