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


package org.mitre.mpf.wfm.util;

import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.enums.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Properties;

/**
 * The MediaTypeUtils class provides utilities for working with media types.
 *
 * Created by ecole on 3/22/16.
 */
@Component(MediaTypeUtils.REF)
public class MediaTypeUtils {

    private static final Logger log = LoggerFactory.getLogger(MediaTypeUtils.class);
    public static final String REF = "mediaTypeUtils";

    private static Properties mediaTypeProperties;

    @PostConstruct
    public void init(){
        // Providing a way to statically access the properties that load on the component.
        mediaTypeProperties = localProperties;
    }

    // TODO: Use Apache Commons Configuration

    @Resource(name="mediaTypeProperties")
    private Properties localProperties;

    /**
     * Uses the media mimeType and any whitelisted properties to determine how to process
     * a piece of media.
     *
     * @param mimeType  The mime-type of the media.
     * @return          The MediaType to treat the media as.
     */
    public static MediaType parse(String mimeType) {
        String trimmedMimeType = TextUtils.trim(mimeType);

        if (mediaTypeProperties==null) {
            log.warn("media type properties not loaded.");
        } else {
            String typeFromWhitelist = mediaTypeProperties.getProperty("whitelist." + mimeType);
            if (typeFromWhitelist != null) {
                log.debug("MediaType Found in whitelist:"+mimeType + "  "+ typeFromWhitelist);
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
