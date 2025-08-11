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

package org.mitre.mpf.mvc;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.stereotype.Component;

@Component
public class ProbingResourceMessageConverter extends ResourceHttpMessageConverter {

    @Override
    protected void addDefaultHeaders(
            HttpHeaders headers,
            Resource resource,
            MediaType suggestedContentType) throws IOException {
        var contentType = Optional.ofNullable(headers.getContentType())
                .or(() -> probeContentType(resource))
                .orElse(suggestedContentType);
        super.addDefaultHeaders(headers, resource, contentType);
    }


    private static Optional<MediaType> probeContentType(Resource resource) {
        if (!resource.isFile()) {
            return Optional.empty();
        }
        try {
            var path = resource.getFile().toPath();
            return Optional.ofNullable(Files.probeContentType(path))
                .map(MediaType::parseMediaType);
        }
        catch (IOException | InvalidMediaTypeException e) {
            return Optional.empty();
        }
    }
}
