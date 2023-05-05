/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

@Service
public class CensorPropertiesService {
    private static final Logger LOG = LoggerFactory.getLogger(CensorPropertiesService.class);

    public static final String CENSORED_PROP_REPLACEMENT = "<censored>";

    private final PropertiesUtil _propertiesUtil;


    @Inject
    CensorPropertiesService(PropertiesUtil propertiesUtil) {
        _propertiesUtil = propertiesUtil;
    }


    public <T extends Map<String, String>> T copyAndCensorProperties(
            Map<String, String> source,
            T destination) {
        var censorOperator = createCensorOperator();
        for (var srcEntry : source.entrySet()) {
            destination.put(srcEntry.getKey(),
                            censorOperator.apply(srcEntry.getKey(), srcEntry.getValue()));
        }
        return destination;
    }


    public Map<String, String> copyAndCensorProperties(Map<String, String> source) {
        return copyAndCensorProperties(source, new HashMap<>());
    }


    public BinaryOperator<String> createCensorOperator() {
        var censoredProperties = _propertiesUtil.getCensoredOutputProperties();
        return (k, v) -> {
            if (censoredProperties.contains(k)) {
                LOG.info("Censoring the \"{}\" property value in JSON output.", k);
                return CENSORED_PROP_REPLACEMENT;
            }
            else {
                return v;
            }
        };
    }
}
