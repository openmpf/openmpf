package org.mitre.mpf.wfm.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.inject.Inject;

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

    @Inject
    MediaSelectorsSegmenter(JsonPathService jsonPathService) {
        _jsonPathService = jsonPathService;
    }


    public List<DetectionRequest> segmentMedia(Media media, DetectionContext context) {
        var selectorType = media.getMediaSelectors().get(0).type();
        return switch (selectorType) {
            case JSON_PATH -> segmentUsingJsonPathSelectors(
                    media, context, media.getMediaSelectors());
            // No default case so that compilation fails if a new enum value is added and a new
            // case is not added here.
        };
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
