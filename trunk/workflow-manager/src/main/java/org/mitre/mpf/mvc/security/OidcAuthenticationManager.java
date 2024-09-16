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

package org.mitre.mpf.mvc.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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

    private final Optional<String> _adminClaimName;

    private final String _adminClaimValue;

    private final Optional<String> _userClaimName;

    private final String _userClaimValue;

    private final Function<ClaimAccessor, String> _errorMessageCreator;

    @Inject
    OidcAuthenticationManager(OidcClaimConfig claimConfig) {
        _adminClaimName = claimConfig.adminClaimName();
        _adminClaimValue = claimConfig.adminClaimValue().orElse(null);
        _userClaimName = claimConfig.userClaimName();
        _userClaimValue = claimConfig.userClaimValue().orElse(null);
        _errorMessageCreator = getErrorMessageCreator();
    }


    // Makes sure the user or client was assigned a WFM role.
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
        throw new AccessDeniedWithUserMessageException( _errorMessageCreator.apply(claims));
    }


    private Function<ClaimAccessor, String> getErrorMessageCreator() {
        var quotedAdminClaimValue = '"' + _adminClaimValue + '"';
        var quotedUserClaimValue = '"' + _userClaimValue + '"';
        if (_userClaimName.isEmpty()) {
            return claims -> getSingleClaimNameErrorMessage(
                    claims, _adminClaimName.get(), quotedAdminClaimValue);
        }
        if (_adminClaimName.isEmpty()) {
            return claims -> getSingleClaimNameErrorMessage(
                    claims, _userClaimName.get(), quotedUserClaimValue);
        }

        var userClaimName = _userClaimName.get();
        var adminClaimName = _adminClaimName.get();
        if (userClaimName.equals(adminClaimName)) {
            var quotedClaim = _userClaimValue.equals(_adminClaimValue)
                    ? quotedAdminClaimValue
                    : quotedAdminClaimValue + " or " + quotedUserClaimValue;
            return claims -> getSingleClaimNameErrorMessage(claims, adminClaimName, quotedClaim);
        }
        return claims -> {
            if (claims.hasClaim(adminClaimName) || claims.hasClaim(userClaimName)) {
                return "The token's \"%s\" claim did not contain \"%s\" and the \"%s\" claim did not contain \"%s\"."
                       .formatted(userClaimName, _userClaimValue, adminClaimName, _adminClaimValue);
            }
            else {
                return "The token did not contain a claim named \"%s\" or \"%s\"."
                        .formatted(userClaimName, adminClaimName);
            }
        };
    }

    private static String getSingleClaimNameErrorMessage(
            ClaimAccessor claims, String claimName, String quotedClaimValue) {
        if (claims.hasClaim(claimName)) {
            return "The token's \"%s\" claim did not contain %s.".formatted(
                    claimName, quotedClaimValue);
        }
        else {
            return "The token did not contain a claim named \"%s\".".formatted(claimName);
        }
    }

    // This is called when a user tries to log in to the Web UI to map OIDC to claims to
    // Workflow Manager roles.
    @Override
    public Collection<GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities) {
        var tokens = authorities.stream()
                .filter(OidcUserAuthority.class::isInstance)
                .map(a -> ((OidcUserAuthority) a).getIdToken())
                .toList();
        return convertClaimsToRoles(tokens, authorities);
    }


    // This is called when a client attempts to authenticate to the REST API to map OIDC to claims
    // to Workflow Manager roles.
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        var baseAuthorities = _jwtGrantedAuthoritiesConverter.convert(jwt);
        return convertClaimsToRoles(List.of(jwt), baseAuthorities);
    }


    private Collection<GrantedAuthority> convertClaimsToRoles(
            Collection<? extends ClaimAccessor> claimAccessors,
            Collection<? extends GrantedAuthority> authorities) {

        var newAuthorities = new ArrayList<GrantedAuthority>(authorities);
        boolean hasAdminClaim = claimAccessors.stream().anyMatch(this::hasAdminClaim);
        if (hasAdminClaim) {
            addRole(UserRole.ADMIN, newAuthorities);
        }
        else {
            removeRole(UserRole.ADMIN, newAuthorities);
        }

        boolean hasUserClaim = claimAccessors.stream().anyMatch(this::hasUserClaim);
        if (hasUserClaim) {
            addRole(UserRole.USER, newAuthorities);
        }
        else {
            removeRole(UserRole.USER, newAuthorities);
        }
        return newAuthorities;
    }

    private boolean hasAdminClaim(ClaimAccessor claimAccessor) {
        return _adminClaimName.map(claimAccessor::getClaimAsStringList)
            .map(claims -> claims.contains(_adminClaimValue))
            .orElse(false);
    }

    private boolean hasUserClaim(ClaimAccessor claimAccessor) {
        return _userClaimName.map(claimAccessor::getClaimAsStringList)
            .map(claims -> claims.contains(_userClaimValue))
            .orElse(false);
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
