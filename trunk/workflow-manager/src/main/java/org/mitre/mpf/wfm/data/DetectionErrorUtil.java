/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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


package org.mitre.mpf.wfm.data;

import com.google.common.collect.*;
import org.mitre.mpf.interop.JsonIssueDetails;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.DetectionProcessingError;
import org.mitre.mpf.wfm.data.entities.persistent.Media;
import org.mitre.mpf.wfm.enums.MediaType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;

import static java.util.stream.Collectors.*;

public class DetectionErrorUtil {

    private DetectionErrorUtil() { }


    /**
     *
     * @param job The job whose DetectionProcessingErrors will be merged.
     * @return A multimap with media id keys and the media's errors as the values.
     */
    public static Multimap<Long, JsonIssueDetails> getMergedDetectionErrors(BatchJob job) {
        // Index by media id so we can process the message for each piece of media separately.
        Multimap<Long, DetectionProcessingError> errorsIndexedByMedia
                = Multimaps.index(job.getDetectionProcessingErrors(), DetectionProcessingError::getMediaId);

        SetMultimap<Long, JsonIssueDetails> results = MultimapBuilder.hashKeys().hashSetValues().build();

        for (var entry : errorsIndexedByMedia.asMap().entrySet()) {
            Media media = job.getMedia(entry.getKey());
            Collection<DetectionProcessingError> mediaErrors = entry.getValue();

            if (media.getMediaType() == MediaType.VIDEO) {
                results.putAll(media.getId(), mergeErrors(job, mediaErrors));
            }
            else {
                List<JsonIssueDetails> issues = mediaErrors
                        .stream()
                        .map(d -> convertDetectionErrorToIssue(job, d))
                        .collect(toList());
                results.putAll(media.getId(), issues);
            }
        }

        return results;
    }


    private static List<JsonIssueDetails> mergeErrors(BatchJob job, Collection<DetectionProcessingError> errors) {
        // Using JsonIssueDetails as the key ensures that issues with the same source, code, and message strings get
        // grouped together.
        Map<JsonIssueDetails, Optional<String>> issueRangeStrings = errors.stream()
                .collect(groupingBy(d -> convertDetectionErrorToIssue(job, d),
                                    toFrameRangesString(DetectionErrorUtil::toRange)));

        var mergedIssues = new ArrayList<JsonIssueDetails>();
        for (var entry : issueRangeStrings.entrySet()) {
            var initialIssue = entry.getKey();
            var msgWithFrames = entry.getValue()
                    .map(frames -> initialIssue.getMessage() + ' ' + frames)
                    .orElseGet(initialIssue::getMessage);
            mergedIssues.add(new JsonIssueDetails(initialIssue.getSource(), initialIssue.getCode(), msgWithFrames));
        }
        return mergedIssues;
    }



    private static JsonIssueDetails convertDetectionErrorToIssue(BatchJob job, DetectionProcessingError error) {
        var source = job.getPipelineElements()
                .getAlgorithm(error.getTaskIndex(), error.getActionIndex())
                .getName();
        return new JsonIssueDetails(source, error.getErrorCode(), error.getErrorMessage());
    }


    private static Range<Integer> toRange(DetectionProcessingError error) {
        return toInclusiveRange(error.getStartFrame(), error.getStopFrame());
    }

    private static Range<Integer> toInclusiveRange(int lowInclusive, int highInclusive) {
        return Range.closed(lowInclusive, highInclusive).canonical(DiscreteDomain.integers());
    }


    private static <T> Collector<T, ?, Optional<String>> toFrameRangesString(Function<T, Range<Integer>> toRangeFn) {
        return Collector.of(
                TreeRangeSet::create,
                (rs, el) -> rs.add(toRangeFn.apply(el)),
                (rs1, rs2) -> { rs1.addAll(rs2); return rs1; },
                DetectionErrorUtil::createFrameRangeString
        );
    }


    public static Collector<Integer, ?, Optional<String>> toFrameRangesString() {
        return toFrameRangesString(i -> toInclusiveRange(i, i));
    }


    private static Optional<String> createFrameRangeString(RangeSet<Integer> frameRangeSet) {
        if (frameRangeSet.isEmpty()) {
            return Optional.empty();
        }

        // Create a string like "(Frames: 0 - 19)" or "(Frames: 0 - 19, 50 - 59)".
        // The RangeSet class takes care of merging adjacent ranges.
        var frameRangesString = frameRangeSet.asRanges()
                .stream()
                .map(DetectionErrorUtil::toString)
                .collect(joining(", ", "(Frames: ", ")"));
        return Optional.of(frameRangesString);
    }


    private static String toString(Range<Integer> range) {
        int start = range.lowerBoundType() == BoundType.CLOSED
                ? range.lowerEndpoint()
                : range.lowerEndpoint() + 1;

        int end = range.upperBoundType() == BoundType.CLOSED
                ? range.upperEndpoint()
                : range.upperEndpoint() - 1;

        if (start == end) {
            return String.valueOf(start);
        }

        if (start + 1 == end) {
            return String.format("%s, %s", start, end);
        }

        return String.format("%s - %s", start, end);
    }
}
