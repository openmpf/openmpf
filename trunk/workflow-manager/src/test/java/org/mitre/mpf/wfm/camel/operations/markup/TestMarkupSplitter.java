/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel.operations.markup;

import com.google.common.collect.ImmutableSortedMap;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.mpf.wfm.data.entities.transients.Detection;
import org.mitre.mpf.wfm.data.entities.transients.Track;

import java.util.List;
import java.util.Map;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestMarkupSplitter {

    private static String TEXT_PROP_NAME = "TEXT_PROP";
    private static String NUMERIC_PROP_NAME = "NUMERIC_PROP";

    private static Detection createDetection(Map<String, String> detectionProperties) {
        return new Detection(15,      // x
                             15,      // y
                             100,     // width
                             100,     // height
                             8.8888f, // confidence
                             7,       // offsetFrame
                             700,     // offsetTime
                             detectionProperties);
    }

    private static Track createTrack(Map<String, String> trackProperties, Map<String, String> detectionProperties) {
        return new Track(777,     // jobId
                         888,     // mediaId
                         1,       // taskIndex
                         0,       // actionIndex
                         5,       // startOffsetFrame
                         10,      // endOffsetFrame
                         5000,    // startOffsetTime
                         10000,   // endOffsetTime
                         "type",  // type
                         7.7777f, // confidence
                         List.of(createDetection(detectionProperties)),
                         trackProperties,
                         "");
    }

    @Test
    public void getLabelFromTrack() {
        Track track = createTrack(
                ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class",
                                      NUMERIC_PROP_NAME, "9.999"),
                ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                      NUMERIC_PROP_NAME, "1.11111"));
        Assert.assertEquals(
                "prefix: some reall 9.999",
                MarkupSplitter.getLabel(track, "prefix: ", TEXT_PROP_NAME, 10,
                                        NUMERIC_PROP_NAME).get());
    }

    @Test
    public void getLabelFromDetection() {
        Detection detection = createDetection(
                ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                      NUMERIC_PROP_NAME, "1.11111"));
        Assert.assertEquals(
                "prefix: abc 1.111",
                MarkupSplitter.getLabel(detection, "prefix: ", TEXT_PROP_NAME, 10,
                                        NUMERIC_PROP_NAME).get());
    }

    @Test
    public void getLabelFromExemplar() {
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertEquals(
                    "abc 1.111",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class"),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertEquals(
                    "some reall 1.111",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(NUMERIC_PROP_NAME, "9.999"),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertEquals(
                    "abc 9.999",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
    }

    @Test
    public void getLabelWithRounding() {
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class",
                                          NUMERIC_PROP_NAME, "9.99999"),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "7000.5555"));
            Assert.assertEquals(
                    "some reall 10.000",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
        {
            Detection detection = createDetection(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "7000.5555"));
            Assert.assertEquals(
                    "abc 7000.556",
                    MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
        {
            Detection detection = createDetection(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abcdefgh   cutoff",
                                          NUMERIC_PROP_NAME, "7000.5"));
            Assert.assertEquals(
                    "abcdefgh 7000.500",
                    MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
    }


    @Test
    public void testLabelLength() {
        Detection detection = createDetection(
                ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class",
                                      NUMERIC_PROP_NAME, "7000.5555"));

        Assert.assertEquals(
                "7000.556",
                MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 0,
                                        NUMERIC_PROP_NAME).get());

        Assert.assertEquals(
                "s 7000.556",
                MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 1,
                                        NUMERIC_PROP_NAME).get());
        Assert.assertEquals(
                "some really 7000.556",
                MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 11,
                                        NUMERIC_PROP_NAME).get());

        Assert.assertEquals(
                "some really really long class 7000.556",
                MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 29,
                                        NUMERIC_PROP_NAME).get());

        Assert.assertEquals(
                "some really really long class 7000.556",
                MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 100,
                                        NUMERIC_PROP_NAME).get());
    }


    @Test
    public void getLabelWithMissingPart() {
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(),
                    ImmutableSortedMap.of());
            Assert.assertTrue(MarkupSplitter.getLabel(
                    track, "", TEXT_PROP_NAME, 10, NUMERIC_PROP_NAME).isEmpty());
        }
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class"),
                    ImmutableSortedMap.of());
            Assert.assertEquals(
                    "some reall",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(NUMERIC_PROP_NAME, "9.999"),
                    ImmutableSortedMap.of());
            Assert.assertEquals(
                    "9.999",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc"));
            Assert.assertEquals(
                    "abc",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(),
                    ImmutableSortedMap.of(NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertEquals(
                    "1.111",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
    }

    @Test
    public void getLabelWithConfidence() {
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class",
                                          NUMERIC_PROP_NAME, "9.999"),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertEquals(
                    "some reall 7.778",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            "CONFIDENCE").get());
        }
        {
            Detection detection = createDetection(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertEquals(
                    "abc 8.889",
                    MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 10,
                                            "CONFIDENCE").get());
        }
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class",
                                          "CONFIDENCE", "9.999"),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          "CONFIDENCE", "1.11111"));
            Assert.assertEquals(
                    "some reall 9.999",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            "CONFIDENCE").get());
        }
        {
            Detection detection = createDetection(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          "CONFIDENCE", "1.11111"));
            Assert.assertEquals(
                    "abc 1.111",
                    MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 10,
                                            "CONFIDENCE").get());
        }
    }

    @Test
    public void omitLabelPart() {
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class",
                                          NUMERIC_PROP_NAME, "9.999"),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertEquals(
                    "9.999",
                    MarkupSplitter.getLabel(track, "", "", 10,
                                            NUMERIC_PROP_NAME).get());
        }
        {
            Detection detection = createDetection(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertEquals(
                    "1.111",
                    MarkupSplitter.getLabel(detection, "", "", 10,
                                            NUMERIC_PROP_NAME).get());
        }
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class",
                                          NUMERIC_PROP_NAME, "9.999"),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertEquals(
                    "some reall",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10, "").get());
        }
        {
            Detection detection = createDetection(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertEquals(
                    "abc",
                    MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 10, "").get());
        }
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class",
                                          NUMERIC_PROP_NAME, "9.999"),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertTrue(MarkupSplitter.getLabel(track, "", "", 10, "").isEmpty());
        }
        {
            Detection detection = createDetection(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));
            Assert.assertTrue(MarkupSplitter.getLabel(detection, "", "", 10, "").isEmpty());
        }
    }

    @Test
    public void invalidNumericProperty() {
        {
            Track track = createTrack(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "some really really long class",
                                          NUMERIC_PROP_NAME, "not a number"),
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "1.11111"));

            Assert.assertEquals(
                    "some reall",
                    MarkupSplitter.getLabel(track, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
        {
            Detection detection = createDetection(
                    ImmutableSortedMap.of(TEXT_PROP_NAME, "abc",
                                          NUMERIC_PROP_NAME, "not a number"));
            Assert.assertEquals(
                    "abc",
                    MarkupSplitter.getLabel(detection, "", TEXT_PROP_NAME, 10,
                                            NUMERIC_PROP_NAME).get());
        }
    }
}
