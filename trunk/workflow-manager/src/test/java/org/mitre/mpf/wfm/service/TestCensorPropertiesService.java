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


package org.mitre.mpf.wfm.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class TestCensorPropertiesService {

    private AutoCloseable _closeable;

    @InjectMocks
    private CensorPropertiesService _censorPropertiesService;

    @Mock
    private PropertiesUtil _mockPropertiesUtil;

    @Before
    public void init() {
        _closeable = MockitoAnnotations.openMocks(this);
    }


    @After
    public void close() throws Exception {
        _closeable.close();
    }


    @Test
    public void canCensorProperties() {
        when(_mockPropertiesUtil.getCensoredOutputProperties())
                .thenReturn(ImmutableSet.of("CENSORED1", "CENSORED2"));

        Map<String, String> properties = ImmutableMap.of(
                "PROP1", "VALUE1",
                "CENSORED1", "CENSORED VALUE1",
                "PROP2", "VALUE2",
                "CENSORED2", "CENSORED VALUE2");

        Map<String, String> censoredProperties
                = _censorPropertiesService.copyAndCensorProperties(properties);

        Map<String, String> expectedCensoredProperties = ImmutableMap.of(
                "PROP1", "VALUE1",
                "CENSORED1", CensorPropertiesService.CENSORED_PROP_REPLACEMENT,
                "PROP2", "VALUE2",
                "CENSORED2", CensorPropertiesService.CENSORED_PROP_REPLACEMENT);

        assertEquals(expectedCensoredProperties, censoredProperties);
    }


    @Test
    public void canHandleEmptyProps() {
        when(_mockPropertiesUtil.getCensoredOutputProperties())
                .thenReturn(ImmutableSet.of("CENSORED1", "CENSORED2"));

        Map<String, String> censoredProperties
                = _censorPropertiesService.copyAndCensorProperties(new HashMap<>());
        assertTrue(censoredProperties.isEmpty());
    }


    @Test
    public void canHandleNoCensoredOutputProps() {
        when(_mockPropertiesUtil.getCensoredOutputProperties())
                .thenReturn(ImmutableSet.of());

        Map<String, String> properties = ImmutableMap.of(
                "PROP1", "VALUE1",
                "CENSORED1", "CENSORED VALUE1",
                "PROP2", "VALUE2",
                "CENSORED2", "CENSORED VALUE2");

        Map<String, String> censoredProperties
                = _censorPropertiesService.copyAndCensorProperties(properties);

        assertEquals(properties, censoredProperties);
    }


    @Test
    public void canHandleEmptyPropsAndNoCensoredOutputProps() {
        when(_mockPropertiesUtil.getCensoredOutputProperties())
                .thenReturn(ImmutableSet.of());

        Map<String, String> censoredProperties
                = _censorPropertiesService.copyAndCensorProperties(new HashMap<>());
        assertTrue(censoredProperties.isEmpty());

    }
}
