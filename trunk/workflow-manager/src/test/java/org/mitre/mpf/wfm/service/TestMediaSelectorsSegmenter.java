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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.mitre.mpf.rest.api.MediaSelectorType;
import org.mitre.mpf.test.MockitoTest;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.operations.MediaSelectorsOutputFileProcessor;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.data.entities.persistent.MediaImpl;
import org.mitre.mpf.wfm.data.entities.persistent.MediaSelector;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.segmenting.DetectionRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class TestMediaSelectorsSegmenter extends MockitoTest.Strict {

    @Mock
    private JsonPathService _mockJsonPathService;

    @Mock
    private MediaSelectorsOutputFileProcessor _mockOutputFileProcessor;

    @InjectMocks
    private MediaSelectorsSegmenter _mediaSelectorsSegmenter;


    @Test
    public void testSegmentMediaNoMatchesIsError() {
        var selector = new MediaSelector(
                "expr", MediaSelectorType.JSON_PATH,
                Map.of(),
                null,
                null);
        var media = createTestMedia(List.of(selector));
        var context = createDetectionContext(
                Map.of(MpfConstants.MEDIA_SELECTORS_NO_MATCHES_IS_ERROR, "true"));

        var mockEvaluator = mock(JsonPathEvaluator.class);
        when(_mockJsonPathService.load(media.getProcessingPath()))
                .thenReturn(mockEvaluator);

        assertThatExceptionOfType(WfmProcessingException.class)
                .isThrownBy(() -> _mediaSelectorsSegmenter.segmentMedia(media, context))
                .withMessageContaining("None of the media selectors matched content in the source document.");
        verifyNoInteractions(_mockOutputFileProcessor);
    }


    @Test
    public void testSegmentMediaNoMatchesIsNotError() {
        var selector = new MediaSelector(
                "expr", MediaSelectorType.JSON_PATH,
                Map.of(),
                null,
                null);
        var media = createTestMedia(List.of(selector));
        var context = createDetectionContext(
                Map.of(MpfConstants.MEDIA_SELECTORS_NO_MATCHES_IS_ERROR, "false"));

        var mockEvaluator = mock(JsonPathEvaluator.class);
        when(_mockJsonPathService.load(media.getProcessingPath()))
                .thenReturn(mockEvaluator);

        var results = _mediaSelectorsSegmenter.segmentMedia(media, context);
        assertThat(results).isEmpty();

        verify(_mockOutputFileProcessor)
                .createNoMatchOutputDocument(context.getJobId(), media);
    }


    @Test
    public void testSegmentMedia() {
        var selector = new MediaSelector(
                "expr", MediaSelectorType.JSON_PATH,
                Map.of("SELECTION_PROP", "SELECTION_VALUE"),
                null);

        var media = createTestMedia(List.of(selector));

        var mockEvaluator = mock(JsonPathEvaluator.class);
        when(_mockJsonPathService.load(media.getProcessingPath()))
                .thenReturn(mockEvaluator);

        when(mockEvaluator.evalAndExtractStrings("expr"))
                .thenAnswer(i -> Set.of("match1", "match2").stream());

        var context = createDetectionContext();
        var results = _mediaSelectorsSegmenter.segmentMedia(media, context);

        assertThat(results)
            .hasSize(2)
            .extracting(DetectionRequest::headers)
            .allSatisfy(h -> assertThat(h).containsExactly(
                    Map.entry(MpfHeaders.MEDIA_SELECTOR_ID, selector.id().toString())));

        assertThat(results)
            .extracting(r -> r.protobuf().getMediaMetadataMap())
            .satisfiesOnlyOnce(m -> assertThat(m).containsExactly(Map.entry(
                    MpfConstants.SELECTED_CONTENT, "match1")))
            .satisfiesOnlyOnce(m -> assertThat(m).containsExactly(Map.entry(
                    MpfConstants.SELECTED_CONTENT, "match2")));

        assertThat(results)
            .extracting(r -> r.protobuf().getAlgorithmPropertiesMap())
            .allSatisfy(p -> assertThat(p).isEqualTo(selector.selectionProperties()));
    }


    private static Media createTestMedia(Collection<MediaSelector> mediaSelectors) {
        return new MediaImpl(
            428,
            "file:///path/to/media",
            null,
            Path.of("/path/to/media"),
            Map.of("MEDIA-PROP1", "MEDIA-VALUE1"),
            Map.of(), List.of(), List.of(),
            mediaSelectors, null, null);
    }

    private static DetectionContext createDetectionContext() {
        return createDetectionContext(Map.of());
    }

    private static DetectionContext createDetectionContext(Map<String, String> properties) {
        return new DetectionContext(
                0, 0, null, 0, null, false,
                properties, null, null, null);
    }
}
