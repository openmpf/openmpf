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

package org.mitre.mpf.mvc.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.mitre.mpf.wfm.enums.UserRole;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;


@Profile("oidc")
@Component
public class OidcAuthenticationManager implements
        AuthorizationManager<RequestAuthorizationContext>,
        GrantedAuthoritiesMapper,
        Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter _jwtGrantedAuthoritiesConverter
            = new JwtGrantedAuthoritiesConverter();

    private final String _adminClaimName;

    private final String _adminClaimValue;

    private final String _userClaimName;

    private final String _userClaimValue;

    @Inject
    OidcAuthenticationManager(OidcClaimConfig claimConfig) {
        _adminClaimName = claimConfig.adminClaimName();
        _adminClaimValue = claimConfig.adminClaimValue();
        _userClaimName = claimConfig.userClaimName();
        _userClaimValue = claimConfig.userClaimValue();

    }


    @Override
    public AuthorizationDecision check(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext ctx) {

        var authentication = authenticationSupplier.get();
        if (!(authentication instanceof OAuth2AuthenticationToken)
                && !(authentication instanceof JwtAuthenticationToken)) {
            // This is usually when someone initially goes to WFM and gets redirected to the
            // provider's login page.
            return new AuthorizationDecision(false);
        }

        var isAuthorized = hasRole(UserRole.ADMIN, authentication.getAuthorities())
                    || hasRole(UserRole.USER, authentication.getAuthorities());
        if (isAuthorized) {
            return new AuthorizationDecision(true);
        }

        if (!(authentication.getPrincipal() instanceof ClaimAccessor claims)) {
            return new AuthorizationDecision(false);
        }

        var userClaimValues = claims.getClaimAsStringList(_userClaimName);
        if (_adminClaimName.equals(_userClaimName)) {
            if (userClaimValues == null) {
                throw new AccessDeniedWithUserMessageException(
                        "The token did not contain a claim named \"%s\"."
                        .formatted(_userClaimName));
            }
            throw new AccessDeniedWithUserMessageException(
                    "The token's \"%s\" claim did not contain \"%s\" or \"%s\"."
                    .formatted(_userClaimName, _userClaimValue, _adminClaimValue));
        }


        var adminClaimValues = claims.getClaimAsStringList(_adminClaimName);
        if (userClaimValues == null && adminClaimValues == null) {
            throw new AccessDeniedWithUserMessageException(
                    "The token did not contain a claim named \"%s\" or \"%s\"."
                    .formatted(_adminClaimName, _userClaimName));
        }

        String presentClaimName;
        String requiredClaimValue;
        if (userClaimValues == null) {
            presentClaimName = _adminClaimName;
            requiredClaimValue = _adminClaimValue;
        }
        else {
            presentClaimName = _userClaimName;
            requiredClaimValue = _userClaimValue;
        }
        throw new AccessDeniedWithUserMessageException(
                "The token's \"%s\" claim did not contain \"%s\"."
                .formatted(presentClaimName, requiredClaimValue));
    }


    // Called when a user tries to log in to the Web UI.
    @Override
    public Collection<GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities) {
        var tokens = authorities.stream()
                .filter(a -> a instanceof OidcUserAuthority)
                .map(a -> ((OidcUserAuthority) a).getIdToken())
                .toList();
        return convertClaimsToRoles(tokens, authorities);
    }


    // Called when a client attempts to authenticate to the REST API.
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        var baseAuthorities = _jwtGrantedAuthoritiesConverter.convert(jwt);
        return convertClaimsToRoles(List.of(jwt), baseAuthorities);
    }


    private Collection<GrantedAuthority> convertClaimsToRoles(
            Collection<? extends ClaimAccessor> claimAccessors,
            Collection<? extends GrantedAuthority> authorities) {

        var newAuthorities = new ArrayList<GrantedAuthority>(authorities);
        boolean hasAdminClaim = claimAccessors.stream().anyMatch(c -> hasAdminClaim(c));
        if (hasAdminClaim) {
            addRole(UserRole.ADMIN, newAuthorities);
        }
        else {
            removeRole(UserRole.ADMIN, newAuthorities);
        }

        boolean hasUserClaim = claimAccessors.stream().anyMatch(c -> hasUserClaim(c));
        if (hasUserClaim) {
            addRole(UserRole.USER, newAuthorities);
        }
        else {
            removeRole(UserRole.USER, newAuthorities);
        }

        return newAuthorities;
    }

    private boolean hasAdminClaim(ClaimAccessor claimAccessor) {
        var adminClaimValues = claimAccessor.getClaimAsStringList(_adminClaimName);
        return adminClaimValues != null && adminClaimValues.contains(_adminClaimValue);
    }

    private boolean hasUserClaim(ClaimAccessor claimAccessor) {
        var userClaimValues = claimAccessor.getClaimAsStringList(_userClaimName);
        return userClaimValues != null && userClaimValues.contains(_userClaimValue);
    }

    private static void addRole(UserRole role, List<GrantedAuthority> authorities) {
        if (!hasRole(role, authorities)) {
            authorities.add(new SimpleGrantedAuthority(role.springName));
        }
    }

    private static boolean hasRole(
            UserRole role,
            Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equals(role.springName));
    }


    // Spring adds the USER role to anyone who successfully logs in to the OIDC provider,
    // so we need to remove it if the user does not have the correct claim.
    private static void removeRole(UserRole role, List<GrantedAuthority> authorities) {
        authorities.removeIf(a -> a.getAuthority().equals(role.springName));
    }
}
