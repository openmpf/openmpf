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


package org.mitre.mpf.mvc;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * By default Spring returns HTML when authentication fails,
 * but since this is applied to our REST endpoints JSON is more appropriate.
 */
@Component
public class RestBasicAuthEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // This header is what makes the log in box appear when accessing the REST URLs
        // in a browser such as on the Swagger page.
        response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Workflow Manager\"");
        if (request.getMethod().equals("OPTIONS")
                && CorsFilter.addCorsHeadersIfAllowed(request, response)) {
            // Handle CORS preflight request
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
        else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            try (PrintWriter pw = response.getWriter()) {
                pw.printf("{\"message\": \"%s\"}", authException.getMessage());
            }
        }
    }
}
