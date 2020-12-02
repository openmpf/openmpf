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


package org.mitre.mpf.component.executor.detection;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TestMPFDetectionMain {

    @Test
    public void restrictMediaTypeCanHandleMissingEnvVar() {
        assertFalse(MPFDetectionMain.getMediaTypeSelector(Map.of()).isPresent());
        assertFalse(MPFDetectionMain.getMediaTypeSelector(restrictMediaTypeEnv("")).isPresent());
        assertFalse(MPFDetectionMain.getMediaTypeSelector(restrictMediaTypeEnv(",")).isPresent());
    }

    @Test
    public void testValidRestrictMediaTypes() {
        assertMediaTypeSelector("MediaType in ('VIDEO')", "VIDEO");
        assertMediaTypeSelector("MediaType in ('VIDEO', 'IMAGE')", "VIDEO, IMAGE");
        assertMediaTypeSelector("MediaType in ('VIDEO', 'IMAGE')", "VIDEO,,IMAGE");
        assertMediaTypeSelector("MediaType in ('VIDEO', 'IMAGE', 'AUDIO')", "VIDEO,  IMaGe ,  audio,");
    }

    private static void assertMediaTypeSelector(String expectedSelector, String envVarValue) {
        var envMap = restrictMediaTypeEnv(envVarValue);
        var actualSelector = MPFDetectionMain.getMediaTypeSelector(envMap).get();
        assertEquals(expectedSelector, actualSelector);
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsWhenInvalidRestrictMediaType() {
        MPFDetectionMain.getMediaTypeSelector(restrictMediaTypeEnv("HELLO"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsWhenInvalidMediaTypeMixedWithActualMediaType() {
        MPFDetectionMain.getMediaTypeSelector(restrictMediaTypeEnv("VIDEO,HELLO"));
    }

    private static Map<String, String> restrictMediaTypeEnv(String envValue) {
        return Map.of(MPFDetectionMain.RESTRICT_MEDIA_TYPES_ENV_NAME, envValue);
    }


    @Test
    public void testEnvironmentJobProperties() {
        var environment = Map.of(
                "MPF_PROP_PROP1", "VALUE1",
                "MPF_PROP_PROP2", "VALUE2",
                "NOT A PROPERTY", "ASDF",
                "MPF_PROP BAD_PROP", "ASDF",
                "MPF_PROP_", "ASDF"
        );
        var expected = Map.of(
                "PROP1", "VALUE1",
                "PROP2", "VALUE2");
        assertEquals(expected, MPFDetectionMessenger.getEnvironmentJobProperties(environment));
    }
}
