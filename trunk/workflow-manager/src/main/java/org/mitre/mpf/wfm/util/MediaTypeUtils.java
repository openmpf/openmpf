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

package org.mitre.mpf.wfm.util;

import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MediaTypeUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MediaTypeUtils.class);

    private final PropertiesConfiguration _mediaTypePropertiesConfig;

    @Inject
    public MediaTypeUtils(PropertiesUtil propertiesUtil) throws ConfigurationException {
        var mediaTypesFile = propertiesUtil.getMediaTypesFile();
        var configBuilderParameters = new Parameters()
                .fileBased()
                .setPath(mediaTypesFile.getPath())
                .setListDelimiterHandler(new DefaultListDelimiterHandler(','));

        var builder = new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class);
        _mediaTypePropertiesConfig = builder
                .configure(configBuilderParameters)
                .getConfiguration();
    }

    /**
     * Uses the media mimeType and media type properties file to determine how to process a piece
     * of media.
     *
     * @param mimeType  The mime-type of the media.
     * @return          The MediaType to treat the media as.
     */
    public MediaType parse(String mimeType) {
        var mediaType = _mediaTypePropertiesConfig.getString(mimeType);
        if (mediaType != null && !mediaType.isBlank()) {
            var trimmedUpper = mediaType.strip().toUpperCase();
            try {
                return MediaType.valueOf(trimmedUpper);
            }
            catch (IllegalArgumentException e) {
                LOG.error(
                        "The \"{}\" property from the media types file contained the invalid value of \"{}\".",
                        mimeType, mediaType);

            }
        }
        return Stream.of(MediaType.values())
            .filter(mt -> StringUtils.startsWithIgnoreCase(mimeType, mt.toString()))
            .findAny()
            .orElse(MediaType.UNKNOWN);
    }
}
