/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

package org.mitre.mpf.test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Predicate;
import org.apache.commons.configuration2.ImmutableConfiguration;
import org.hamcrest.Description;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.entities.transients.TransientAction;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.data.entities.transients.TransientPipeline;
import org.mitre.mpf.wfm.data.entities.transients.TransientStage;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.util.IoUtils;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

public class TestUtil {

    private TestUtil() {

    }

    public static <T> T whereArg(Predicate<T> matchPredicate) {
        return CustomMatcher.of(matchPredicate);
    }


    public static String nonBlank() {
        return CustomMatcher.of("Non-blank String", s -> s != null && !s.trim().isEmpty());
    }


    public static <T> T anyNonNull() {
        return CustomMatcher.of("Any not null", Objects::nonNull);
    }

    public static String eqIgnoreCase(String expected) {
        return CustomMatcher.of(expected + " (case insensitive)", s -> s.equalsIgnoreCase(expected));
    }


    public static <T, C extends Collection<T>> C collectionContaining(Predicate<T> matchPredicate) {
        return CustomMatcher.of("Collection containing", c -> c.stream().anyMatch(matchPredicate));
    }

    public static <T, C extends Collection<T>> C nonEmptyCollection() {
        return CustomMatcher.of("Non-empty Collection", c -> !c.isEmpty());
    }


    private static class CustomMatcher<T> extends ArgumentMatcher<T> {

        private final Predicate<T> _matchPred;
        private final String _description;

        private CustomMatcher(Predicate<T> matchPred, String description) {
            _matchPred = matchPred;
            _description = description;
        }

        @Override
        public boolean matches(Object o) {
            //noinspection unchecked
            return _matchPred.test((T) o);
        }

        @Override
        public void describeTo(Description description) {
            if (_description == null) {
                super.describeTo(description);
            }
            else {
                description.appendText(_description);
            }
        }

        public static <T> T of(Predicate<T> matchPred)  {
            return of(null, matchPred);
        }

        public static <T> T of(String description, Predicate<T> matchPred) {
            return Mockito.argThat(new CustomMatcher<>(matchPred, description));
        }
    }

    public static TransientJob setupJob(long jobId, ImmutableConfiguration detectionSystemPropertiesSnapshot, Redis redis, IoUtils ioUtils) throws WfmProcessingException {
        TransientPipeline dummyPipeline = new TransientPipeline("dummyPipeline", "dummyDescription");
        TransientStage dummyStageDet = new TransientStage("dummydummy", "dummyDescription", ActionType.DETECTION);
        TransientAction dummyAction = new TransientAction("dummyAction", "dummyDescription", "dummyAlgo");
        dummyAction.setProperties(new HashMap<>());
        dummyStageDet.getActions().add(dummyAction);

        dummyPipeline.getStages().add(dummyStageDet);
        TransientJob dummyJob = new TransientJob(jobId, "234234", detectionSystemPropertiesSnapshot, dummyPipeline, 0, 1, false, false);
        dummyJob.getMedia().add(new TransientMedia(234234,ioUtils.findFile("/samples/video_01.mp4").toString()));

        redis.persistJob(dummyJob);
        return dummyJob;
    }


    public static boolean almostEqual(double x, double y, double epsilon) {
        return Math.abs(x - y) < epsilon;
    }


    public static boolean almostEqual(double x, double y) {
        return almostEqual(x, y, 0.01);
    }
}


