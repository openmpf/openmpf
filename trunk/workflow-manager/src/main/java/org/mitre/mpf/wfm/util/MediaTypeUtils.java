/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;

@Component(MediaTypeUtils.REF)
public class MediaTypeUtils {

    private static final Logger log = LoggerFactory.getLogger(MediaTypeUtils.class);
    public static final String REF = "mediaTypeUtils";

    @Autowired
    private PropertiesUtil propertiesUtil;

    private static FileSystemResource mediaTypesFile;

    private static PropertiesConfiguration propertiesConfig;

    @PostConstruct
    private void init() {
        // get the media types properties file from the PropertiesUtil;
        // the PropertiesUtil will ensure that it is copied from the template, if necessary
        mediaTypesFile = propertiesUtil.getMediaTypesFile();

        URL url;
        try {
            url = mediaTypesFile.getURL();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot get URL from " + mediaTypesFile + ".", e);
        }

        FileBasedConfigurationBuilder<PropertiesConfiguration> fileBasedConfigBuilder =
                new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class);

        Parameters configBuilderParameters = new Parameters();
        fileBasedConfigBuilder.configure(configBuilderParameters.fileBased().setURL(url)
                .setListDelimiterHandler(new DefaultListDelimiterHandler(',')));

        try {
            propertiesConfig = fileBasedConfigBuilder.getConfiguration();
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Cannot create configuration from " + mediaTypesFile + ".", e);
        }
    }

    /**
     * Uses the media mimeType and any whitelisted properties to determine how to process
     * a piece of media.
     *
     * @param mimeType  The mime-type of the media.
     * @return          The MediaType to treat the media as.
     */
    public static MediaType parse(String mimeType) {
        String trimmedMimeType = TextUtils.trim(mimeType);

        if (propertiesConfig == null) {
            log.warn("Media type properties could not be loaded from " + mediaTypesFile + ".");
        } else {
            String typeFromWhitelist = propertiesConfig.getString("whitelist." + mimeType);
            if (typeFromWhitelist != null) {
                log.debug("Media type found in whitelist: " + mimeType + " is " + typeFromWhitelist);
                MediaType type = MediaType.valueOf(typeFromWhitelist);
                if (type != null) {
                    return type;
                }
            }
        }

        if(StringUtils.startsWithIgnoreCase(trimmedMimeType, "AUDIO")) {
            return MediaType.AUDIO;
        } else if(StringUtils.startsWithIgnoreCase(trimmedMimeType, "IMAGE")) {
            return MediaType.IMAGE;
        } else if(StringUtils.startsWithIgnoreCase(trimmedMimeType, "VIDEO")) {
            return MediaType.VIDEO;
        } else {
            return MediaType.UNKNOWN;
        }
    }
}
