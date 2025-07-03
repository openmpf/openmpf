/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2025 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2025 The MITRE Corporation                                       *
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

package org.mitre.mpf.rest.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import org.mitre.mpf.rest.api.util.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Wrapper around a URI that prevents data URI content from being added to the string produced by
 * calling .toString(). This prevents the content from appearing in log and error messages.
 */
public record MediaUri(URI uri) {

    public MediaUri {
        uri = Utils.normalize(uri);
    }

    @JsonCreator // Deserialize from a JSON string instead of object.
    public MediaUri(String uriStr) throws URISyntaxException {
        this(new URI(uriStr));
    }

    public static MediaUri create(String uriStr) {
        return new MediaUri(URI.create(uriStr));
    }

    public URI get() {
        return uri;
    }

    @JsonValue // Serialize as a JSON string instead of object.
    public String fullString() {
        return uri.toString();
    }

    public String shortString() {
        var full = fullString();
        if (full.startsWith("data:")) {
            int commaPos = full.indexOf(',');
            if (commaPos > 0) {
                return full.substring(0, commaPos + 1) + "<truncated>";
            }
        }
        return full;
    }

    @Override
    public String toString() {
        return shortString();
    }
}
