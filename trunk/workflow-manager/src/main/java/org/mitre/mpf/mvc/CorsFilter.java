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

import org.mitre.mpf.mvc.util.CloseableMdc;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableSet;

@Component("corsFilter")
public class CorsFilter implements Filter {

    private static final Set<String> CORS_ALLOWED_ORIGINS = getCorsAllowedOrigins();


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try (var mdc = CloseableMdc.all()) {
            addCorsHeadersIfAllowed((HttpServletRequest) request, (HttpServletResponse) response);
            chain.doFilter(request, response);
        }
    }


    public static boolean addCorsHeadersIfAllowed(HttpServletRequest request,
                                                  HttpServletResponse response) {
        var origin = request.getHeader("Origin");
        if (origin == null || !CORS_ALLOWED_ORIGINS.contains(origin)) {
            return false;
        }
        response.addHeader("Access-Control-Allow-Origin", origin);
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
        var acRequestHeaders = request.getHeader("Access-Control-Request-Headers");
        if (acRequestHeaders != null) {
            response.addHeader("Access-Control-Allow-Headers", acRequestHeaders);
        }
        return true;
    }


    private static Set<String> getCorsAllowedOrigins() {
        return Optional.ofNullable(System.getenv("CORS_ALLOWED_ORIGINS"))
                .stream()
                .flatMap(s -> Stream.of(s.split(",")))
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .collect(toUnmodifiableSet());
    }


    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
