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

import java.io.IOException;

import javax.persistence.Entity;

import org.mitre.mpf.mvc.security.custom.sso.CustomSsoConfig;
import org.mitre.mpf.mvc.security.oidc.OidcSecurityConfig;
import org.mitre.mpf.wfm.data.entities.persistent.User;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;


public class UserEntityFilter implements TypeFilter {

    private final AnnotationTypeFilter _annotationTypeFilter
            = new AnnotationTypeFilter(Entity.class);

    private final boolean _oidcEnabled = OidcSecurityConfig.isEnabled();

    private final boolean _customSsoEnabled = CustomSsoConfig.isEnabled();

    @Override
    public boolean match(
            MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
            throws IOException {
        if (!_annotationTypeFilter.match(metadataReader, metadataReaderFactory)) {
            return false;
        }
        else if (_oidcEnabled || _customSsoEnabled) {
            // Prevent hibernate from creating user table when using SSO.
            return !metadataReader.getClassMetadata().getClassName().equals(User.class.getName());
        }
        else {
            return true;
        }
    }
}
