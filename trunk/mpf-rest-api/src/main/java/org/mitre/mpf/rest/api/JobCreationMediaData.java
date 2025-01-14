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

package org.mitre.mpf.rest.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.validation.Valid;

import org.mitre.mpf.rest.api.util.Utils;
import org.mitre.mpf.rest.api.util.ValidName;


public record JobCreationMediaData(
        String mediaUri,
        Map<String, String> properties,
        Map<String, String> metadata,
        List<JobCreationMediaRange> frameRanges,
        List<JobCreationMediaRange> timeRanges,

        @Valid
        List<JobCreationMediaSelector> mediaSelectors,

        Optional<@ValidName(required = false) String> mediaSelectorsOutputAction) {

    public JobCreationMediaData {
        properties = Utils.toImmutableMap(properties);
        metadata = Utils.toImmutableMap(metadata);
        frameRanges = Utils.toImmutableList(frameRanges);
        timeRanges = Utils.toImmutableList(timeRanges);
        mediaSelectors = Utils.toImmutableList(mediaSelectors);
        mediaSelectorsOutputAction = Utils.trimAndUpper(mediaSelectorsOutputAction);
    }
}
