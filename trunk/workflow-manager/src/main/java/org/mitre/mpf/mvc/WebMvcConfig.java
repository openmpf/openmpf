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

package org.mitre.mpf.mvc;

import java.util.List;

import javax.inject.Inject;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.smile.MappingJackson2SmileHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

@EnableWebMvc
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ProbingResourceMessageConverter _probingResourceConverter;

    private final ObjectMapper _objectMapper;


    @Inject
    public WebMvcConfig(
            ProbingResourceMessageConverter probingResourceMessageConverter,
            ObjectMapper objectMapper) {
        _probingResourceConverter = probingResourceMessageConverter;
        _objectMapper = objectMapper;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        var iter = converters.listIterator();
        var addedJacksonConverter = false;
        var addedResourceConverter = false;
        while (iter.hasNext()) {
            var next = iter.next();
            if (next instanceof MappingJackson2SmileHttpMessageConverter) {
                // Prevent HTTP responses from using the Smile format.
                iter.remove();
            }
            else if (next instanceof MappingJackson2HttpMessageConverter) {
                iter.set(createJacksonConverter());
                addedJacksonConverter = true;
            }
            else if (next instanceof ResourceHttpMessageConverter) {
                iter.set(_probingResourceConverter);
                addedResourceConverter = true;
            }
        }

        if (!addedJacksonConverter) {
            converters.add(createJacksonConverter());
        }
        if (!addedResourceConverter) {
            converters.add(_probingResourceConverter);
        }
    }


    private MappingJackson2HttpMessageConverter createJacksonConverter() {
        // Spring automatically configures a MappingJackson2HttpMessageConverter, but it does not
        // let you specify which ObjectMapper to use.
        return new MappingJackson2HttpMessageConverter(_objectMapper);
    }
}
