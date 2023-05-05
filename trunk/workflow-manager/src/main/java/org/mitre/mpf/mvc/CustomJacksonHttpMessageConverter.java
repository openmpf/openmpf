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


package org.mitre.mpf.mvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

// Spring's built-in MappingJackson2HttpMessageConverter does not allow you to configure the ObjectMapper that it uses.
// Classes annotated with @Controller will use this class. This class ensures that those controllers,
// and classes that explicitly use ObjectMapper, all use the same ObjectMapper instance.
@Component
public class CustomJacksonHttpMessageConverter extends MappingJackson2HttpMessageConverter {

    // These classes should use the internal Spring HttpMessageConverters.
    private static final ImmutableList<Class<?>> BLACKLIST = ImmutableList.of(String.class, Resource.class);

    @Inject
    CustomJacksonHttpMessageConverter(ObjectMapper objectMapper) {
        super(objectMapper);
    }


    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return BLACKLIST.stream().noneMatch(blc -> blc.isAssignableFrom(clazz))
                && super.canWrite(clazz, mediaType);
    }
}
