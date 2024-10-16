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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.operations.MediaSelectorsOutputFileProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaSelector;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.segmenting.DetectionRequest;
import org.mitre.mpf.wfm.segmenting.MediaSegmenter;
import org.springframework.stereotype.Component;

@Component
public class MediaSelectorsSegmenter  {

    private final JsonPathService _jsonPathService;

    private final MediaSelectorsOutputFileProcessor _mediaSelectorsOutputFileProcessor;

    @Inject
    MediaSelectorsSegmenter(
            JsonPathService jsonPathService,
            MediaSelectorsOutputFileProcessor mediaSelectorsOutputFileProcessor) {
        _jsonPathService = jsonPathService;
        _mediaSelectorsOutputFileProcessor = mediaSelectorsOutputFileProcessor;
    }


    public List<DetectionRequest> segmentMedia(Media media, DetectionContext context) {
        var selectorType = media.getMediaSelectors().get(0).type();
        var results = switch (selectorType) {
            case JSON_PATH -> segmentUsingJsonPathSelectors(
                    media, context, media.getMediaSelectors());
            // No default case so that compilation fails if a new enum value is added and a new
            // case is not added here.
        };
        if (results.isEmpty()) {
            var errorProp = context.getAlgorithmProperties().get(
                    MpfConstants.MEDIA_SELECTORS_NO_MATCHES_IS_ERROR);
            if (Boolean.parseBoolean(errorProp)) {
                throw new WfmProcessingException(
                        "None of the media selectors matched content in the source document.");
            }
            _mediaSelectorsOutputFileProcessor.createNoMatchOutputDocument(
                    context.getJobId(), media);
        }
        return results;
    }


    private List<DetectionRequest> segmentUsingJsonPathSelectors(
            Media media, DetectionContext context, Collection<MediaSelector> selectors) {
        var jsonPathEvaluator = _jsonPathService.load(media.getProcessingPath());
        return selectors.stream()
                .flatMap(ms -> createDetectionRequests(jsonPathEvaluator, media, context, ms))
                .toList();
    }

    private static Stream<DetectionRequest> createDetectionRequests(
            JsonPathEvaluator jsonPathEvaluator,
            Media media,
            DetectionContext context,
            MediaSelector selector) {
        return jsonPathEvaluator.evalAndExtractStrings(selector.expression())
                .distinct()
                .map(s -> createDetectionRequest(s, media, context, selector));
    }

    private static DetectionRequest createDetectionRequest(
                String evalResult,
                Media media,
                DetectionContext context,
                MediaSelector selector) {
        var pbDetectionRequest = MediaSegmenter.initializeRequest(media, context)
                .putAllAlgorithmProperties(selector.selectionProperties())
                .putMediaMetadata(MpfConstants.SELECTED_CONTENT, evalResult);
        pbDetectionRequest.getGenericRequestBuilder().build();
        return new DetectionRequest(
                pbDetectionRequest.build(),
                Map.of(MpfHeaders.MEDIA_SELECTOR_ID, selector.id().toString()));
    }
}
