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

package org.mitre.mpf.mvc.controller;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.mitre.mpf.mvc.model.AuthenticationModel;
import org.mitre.mpf.mvc.security.AccessDeniedWithUserMessageException;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.ModelAndView;


@Controller
@Scope("request")
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    private PropertiesUtil propertiesUtil;


    public static AuthenticationModel getAuthenticationModel(HttpServletRequest request) {
        // get security context from thread local
        SecurityContext context = SecurityContextHolder.getContext();
        boolean authenticated = false;
        boolean admin = false;
        String userPrincipalName = null;

        if(context != null) {
            Authentication authentication = context.getAuthentication();
            if ( authentication != null ) {
                authenticated = true;
                Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
                admin = authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
        }

        if(request != null && request.getUserPrincipal() != null) {
        	userPrincipalName = request.getUserPrincipal().getName();
        }

        return new AuthenticationModel(authenticated, admin, userPrincipalName);
    }

    /** a helper method to put in all the security credentials needed
     *  by the JSP views;
     *  Note:  requires that the ModelAndView passed in is instantiated in
     *      the calling controller; this is also the ModelAndView returned
     *      after the credentials are set
     *  code is a combination of the following:
     *      http://stackoverflow.com/a/10232526/1274852
     *      http://stackoverflow.com/a/12455803/1274852
     */
    public static ModelAndView setSecurityCredentialsForView(
        HttpServletRequest request, // needed for UserPrincipal
        ModelAndView mv )           // the mv of each controller, already instantiated
    {
    	AuthenticationModel authenticationModel = getAuthenticationModel(request);

        mv.addObject("authenticated", authenticationModel.isAuthenticated());
        mv.addObject("isAdmin", authenticationModel.isAdmin());
        mv.addObject("UserPrincipalName", authenticationModel.getUserPrincipalName());

        return mv;
    }

    @PostMapping("/logout")
    public String logout(
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session) {
        session.invalidate();
        SecurityContextHolder.clearContext(); // prevent a user from recovering a session
        String redirect = "redirect:/login";
        if (reason != null) {
            redirect += "?reason=" + reason;
        }
        return redirect;
    }

    @GetMapping("/login")
    public Object getLogin(
            @SessionAttribute(name = WebAttributes.AUTHENTICATION_EXCEPTION, required = false)
            Exception authException,
            Authentication authentication) {

        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/";
        }

        ModelAndView model = new ModelAndView("login_view");
        model.addObject("version", propertiesUtil.getSemanticVersion());

        if (authException instanceof BadCredentialsException) {
            model.addObject("error", "Invalid username and password!");
        }

        return model;

    }


    @GetMapping("/user/role-info")
    @ResponseBody
    public AuthenticationModel getSecurityCredentials(HttpServletRequest request /*needed for UserPrincipal*/) {
        return getAuthenticationModel(request);
    }


    @GetMapping("/oidc-access-denied")
    public ModelAndView oidcAccessDenied(
            @RequestAttribute(name = WebAttributes.ACCESS_DENIED_403, required = false)
            AccessDeniedException accessDeniedException) {

        if (accessDeniedException != null) {
            log.error(
                "A user successfully authenticated with an OIDC provider, but was not" +
                            " authorized to access Workflow Manager.",
                    accessDeniedException);
        }
        if (accessDeniedException instanceof AccessDeniedWithUserMessageException) {
            return new ModelAndView(
                    "access_denied", "reason",
                    accessDeniedException.getMessage());
        }
        else {
            return new ModelAndView("access_denied");
        }
    }
}
