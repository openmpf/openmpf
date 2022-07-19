/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import org.mitre.mpf.mvc.controller.AtmosphereController;
import org.mitre.mpf.mvc.controller.TimeoutController;
import org.mitre.mpf.mvc.model.AtmosphereChannel;
import org.mitre.mpf.mvc.util.CloseableMdc;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component( value="timeoutFilter" )
public class TimeoutFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TimeoutFilter.class);

    // these calls do not require the user to be logged in; also, they do not count towards resetting the timeout countdown
    private static final Pattern excludeUrls =
			Pattern.compile("^.*(css|js|fonts|jpg|login|logout|timeout|bootout|rest).*$",
            Pattern.CASE_INSENSITIVE);

    // these ajax calls are on timers and should not count towards resetting the timeout countdown
    private static final Pattern ajaxUrls =
    		Pattern.compile("^.*(adminLogsMap|adminLogsUpdate|info|stats|javasimon-console).*$",
            Pattern.CASE_INSENSITIVE);

    // session timeout in minutes
    private int webSessionTimeout;

    @Autowired
    private PropertiesUtil propertiesUtil;


    @Override
    public void init(FilterConfig config) throws ServletException {
        webSessionTimeout = propertiesUtil.getWebSessionTimeout();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        try (var mdc = CloseableMdc.all()) {
            doHttpFilter((HttpServletRequest) req, (HttpServletResponse) res, chain);
        }
    }

    private void doHttpFilter(HttpServletRequest request, HttpServletResponse response,
                              FilterChain chain) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            // Timed out due to previous call to session.setMaxInactiveInterval;
            handleSessionTimeout(request, response, chain);
            return;
        }

        log.debug("request.getRequestURI()={}", request.getRequestURI());

        if (request.getRequestURI().equals("/workflow-manager/")) {
            // user has just logged in
            setLastActionTime(session, System.currentTimeMillis());
            session.setMaxInactiveInterval(webSessionTimeout * 60); // convert min to sec // DEBUG
            chain.doFilter(request, response);
        } else {

            Matcher m = excludeUrls.matcher(request.getRequestURI());
            boolean excludeUrl = m.matches();

            if (excludeUrl) {
                log.debug("URI matches exclude pattern={}, keep going", excludeUrl);
                chain.doFilter(request, response);

            } else {
                m = ajaxUrls.matcher(request.getRequestURI());
                boolean ajaxUrl = m.matches();

                // NOTE: We might be here if the same user has multiple browser tabs open.
                // If so, and the session has already timed out, the session entry was already
                // removed from the map so set lastAction to 0 to ensure the timeout behavior.
                long lastAction = getLastActionTime(session).orElse(0);
                int sessionTimeoutMillisecs = webSessionTimeout * 60 * 1000; // convert min to millisec

                long now = System.currentTimeMillis();
                log.debug("Not an excludeUrl, now={}, lastAction={}, sessionTimeout={}", now, lastAction, sessionTimeoutMillisecs);
                log.debug("now - lastAction={} ms", now - lastAction);

                long diff = now - lastAction;
                long warningPeriod = 60000;  //ToDo: P038: hardcoding 60 secs to warn user of timeout
                long grace = sessionTimeoutMillisecs - warningPeriod;
                if ( ( diff < grace ) || request.getRequestURI().startsWith("/workflow-manager/resetSession") ) {
                    if (!ajaxUrl) {
                        setLastActionTime(session, now);
                    }
                    log.debug("Keep going due to no timeout");
                    chain.doFilter(request, response);
                } else {
                    if ( diff < sessionTimeoutMillisecs ) {
                        // issue a warning to the client that the session is about to expire
                        HashMap<String,Object> data = new HashMap<String,Object>();
                        data.put( "timeLeftInSecs", (int) ( sessionTimeoutMillisecs - diff ) / 1000 );
                        data.put( "warningPeriod", (int) warningPeriod / 1000 );
                        AtmosphereController.broadcast(
                                AtmosphereChannel.SSPC_SESSION, "OnSessionAboutToTimeout", data );
                    }
                    else {
                        //the session has timed out
                        log.debug("Session has timed out");

                        HashMap<String,Object> data = new HashMap<String,Object>();
                        data.put( "timeLeftInSecs", -1 );
                        AtmosphereController.broadcast(
                                AtmosphereChannel.SSPC_SESSION, "OnSessionExpired", data );

                        session.invalidate();

                        //necessary to prevent a user from recovering a session
                        SecurityContextHolder.clearContext();

                        handleSessionTimeout(request, response, chain);
                    }
                }
            }
        }
    }

    private static void handleSessionTimeout(HttpServletRequest request,
                                             HttpServletResponse response,
                                             FilterChain chain) throws IOException, ServletException {
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.sendError(TimeoutController.CUSTOM_SESSION_TIMEOUT_ERROR_CODE);
        }
        else {
            var redirectPath = request.getContextPath() + "/login";
            if (request.getRequestURI().equals(redirectPath)) {
                chain.doFilter(request, response);
            }
            else {
                response.sendRedirect(redirectPath + "?reason=timeout");
            }
        }
    }


    private static final String LAST_ACTIVITY_TIME_KEY = "lastActivityTime";

    private static void setLastActionTime(HttpSession session, long time) {
        session.setAttribute(LAST_ACTIVITY_TIME_KEY, time);
    }

    private static OptionalLong getLastActionTime(HttpSession session) {
        Object lastActivityTime = session.getAttribute(LAST_ACTIVITY_TIME_KEY);
        return lastActivityTime == null
                ? OptionalLong.empty()
                : OptionalLong.of((long) lastActivityTime);
    }

    @Override
    public void destroy () {
    }

}
