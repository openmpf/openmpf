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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.buffers.DetectionProtobuf;
import org.mitre.mpf.wfm.camel.operations.MediaSelectorsOutputFileProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.JobPipelineElements;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaSelector;
import org.mitre.mpf.wfm.data.entities.transients.Track;
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


    public static void validateSelectors(
            Collection<Media> media,
            JobPipelineElements pipeline) {
        media.forEach(m -> validateSelectors(m, pipeline));
    }

    private static void validateSelectors(Media media, JobPipelineElements pipeline) {
        if (media.getMediaSelectors().isEmpty()) {
            return;
        }
        var numSelectorTypes = media.getMediaSelectors().stream()
            .map(MediaSelector::type)
            .distinct()
            .limit(2)
            .count();
        if (numSelectorTypes > 1) {
            // All of the planned selectors types operate on different file types, so using
            // multiple types for the same media does not make sense. If we do add selector
            // types that can be mixed, then this restriction can be removed.
            throw new WfmProcessingException("All media selectors must have the same type.");
        }

        var outputAction = media.getMediaSelectorsOutputAction();
        if (outputAction.isPresent()) {
            var action = pipeline.getAction(outputAction.get());
            if (action == null) {
                throw new WfmProcessingException(
                    "\"mediaSelectorsOutputAction\" was set to \"" + outputAction.get()
                    + "\", but the pipeline does not contain an action with that name.");
            }
        }
        else if (pipeline.getAllActions().size() > 1) {
            throw new WfmProcessingException(
                    "The job request included media that set \"mediaSelectors\", "
                    + "but \"mediaSelectorsOutputAction\" was not set.");
        }
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
                .putAllAlgorithmProperties(selector.selectionProperties());
        pbDetectionRequest.getGenericRequestBuilder().build();
        var headers = addSelectorInfo(pbDetectionRequest, evalResult, selector.id());
        return new DetectionRequest(pbDetectionRequest.build(), headers);
    }


    public static DetectionRequest createFeedForwardDetectionRequest(
            DetectionProtobuf.DetectionRequest.Builder requestBuilder,
            Track track) {
        var headers = track.getSelectedInput()
                .map(in -> addSelectorInfo(requestBuilder, in, track.getSelectorId().orElseThrow()))
                .orElseGet(Map::of);
        return new DetectionRequest(requestBuilder.build(), Optional.of(track), headers);
    }


    private static Map<String, String> addSelectorInfo(
            DetectionProtobuf.DetectionRequest.Builder requestBuilder,
            String selectedContent,
            UUID selectorId) {
        requestBuilder.putMediaMetadata(MpfConstants.SELECTED_CONTENT, selectedContent);
        return Map.of(MpfHeaders.MEDIA_SELECTOR_ID, selectorId.toString());
    }
}
