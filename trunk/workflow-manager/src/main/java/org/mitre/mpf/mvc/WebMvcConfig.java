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

import org.mitre.mpf.mvc.controller.ExposedMapping;
import org.mitre.mpf.mvc.security.RestAuditLoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.smile.MappingJackson2SmileHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class WebMvcConfig extends DelegatingWebMvcConfiguration {

    private final ProbingResourceMessageConverter _probingResourceConverter;

    private final ObjectMapper _objectMapper;

    private final RestAuditLoggingInterceptor _restAuditLoggingInterceptor;

    @Inject
    public WebMvcConfig(
            ProbingResourceMessageConverter probingResourceMessageConverter,
            ObjectMapper objectMapper,
            RestAuditLoggingInterceptor restAuditLoggingInterceptor) {
        _probingResourceConverter = probingResourceMessageConverter;
        _objectMapper = objectMapper;
        _restAuditLoggingInterceptor = restAuditLoggingInterceptor;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        super.extendMessageConverters(converters);
        // The documentation for the base method states that "the order of converter registration
        // is important". A ListIterator is used ensure that when the default Spring converters
        // are replaced, the customized version is put in the same position within the list.
        var iter = converters.listIterator();
        var addedResourceConverter = false;
        var addedJacksonConverter = false;
        while (iter.hasNext()) {
            var converter = iter.next();
            if (converter instanceof ResourceHttpMessageConverter) {
                iter.set(_probingResourceConverter);
                addedResourceConverter = true;
            }
            else if (converter instanceof MappingJackson2HttpMessageConverter) {
                iter.set(createJacksonConverter());
                addedJacksonConverter = true;
            }
            else if (converter instanceof MappingJackson2SmileHttpMessageConverter) {
                // Prevent HTTP responses from using the Smile format.
                iter.remove();
            }
        }

        if (!addedResourceConverter) {
            converters.add(_probingResourceConverter);
        }
        if (!addedJacksonConverter) {
            converters.add(createJacksonConverter());
        }
    }

    private MappingJackson2HttpMessageConverter createJacksonConverter() {
        // Spring automatically configures a MappingJackson2HttpMessageConverter, but it does not
        // let you specify which ObjectMapper to use.
        return new MappingJackson2HttpMessageConverter(_objectMapper);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        super.addInterceptors(registry);
        registry.addInterceptor(_restAuditLoggingInterceptor)
                .addPathPatterns("/rest/**");
    }

    @Override
    protected RequestMappingHandlerMapping createRequestMappingHandlerMapping() {
        return new ExposedMapping.RequestMappingHandlerMappingImpl();
    }
}
