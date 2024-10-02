/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.data.access.hibernate;

import java.net.URI;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

// Allows Hibernate entities to have URI fields that get stored as strings in the database.
@Converter(autoApply = true)
public class UriAttributeConverter implements AttributeConverter<URI, String> {

    @Override
    public String convertToDatabaseColumn(URI uri) {
        return uri == null ? null : uri.toString();
    }

    @Override
    public URI convertToEntityAttribute(String uriString) {
        return uriString == null ? null : URI.create(uriString);
    }
}
