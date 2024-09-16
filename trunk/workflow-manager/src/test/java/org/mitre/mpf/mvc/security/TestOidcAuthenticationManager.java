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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.enums.UserRole;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class TestOidcAuthenticationManager {

    @Test
    public void testDifferentAdminUserClaims() {
        var config = new OidcClaimConfig(
            Optional.of("admin-claim"),
            Optional.of("ADMIN"),
            Optional.of("user-claim"),
            Optional.of("USER")
        );
        var manager = new OidcAuthenticationManager(config);

        assertFalse(manager.check(() -> createAnonymousToken(), null).isGranted());

        assertEquals(
                "The token did not contain a claim named \"user-claim\" or \"admin-claim\".",
                getErrorMessage(manager, createJwt(manager, "A", "B")));

        assertEquals(
                "The token's \"user-claim\" claim did not contain \"USER\" and the \"admin-claim\" claim did not contain \"ADMIN\".",
                getErrorMessage(manager, createJwt(manager, "admin-claim", "USER")));

        assertAuthenticationSuccessful(
                "admin-claim", "ADMIN", UserRole.ADMIN, manager);
        assertAuthenticationSuccessful(
                "user-claim", "USER", UserRole.USER, manager);
    }

    @Test
    public void testOnlyAdminClaims() {
        var config = new OidcClaimConfig(
            Optional.of("admin-claim"),
            Optional.of("ADMIN"),
            Optional.empty(),
            Optional.empty()
        );
        var manager = new OidcAuthenticationManager(config);

        assertFalse(manager.check(() -> createAnonymousToken(), null).isGranted());

        assertEquals(
                "The token did not contain a claim named \"admin-claim\".",
                getErrorMessage(manager, createJwt(manager, "A", "B")));

        assertEquals(
                "The token's \"admin-claim\" claim did not contain \"ADMIN\".",
                getErrorMessage(manager, createJwt(manager, "admin-claim", "USER")));

        assertAuthenticationSuccessful(
                "admin-claim", "ADMIN", UserRole.ADMIN, manager);
    }


    @Test
    public void testOnlyUserClaims() {
        var config = new OidcClaimConfig(
            Optional.empty(),
            Optional.empty(),
            Optional.of("user-claim"),
            Optional.of("USER")
        );
        var manager = new OidcAuthenticationManager(config);

        assertFalse(manager.check(() -> createAnonymousToken(), null).isGranted());

        assertEquals(
                "The token did not contain a claim named \"user-claim\".",
                getErrorMessage(manager, createJwt(manager, "A", "B")));

        assertEquals(
                "The token's \"user-claim\" claim did not contain \"USER\".",
                getErrorMessage(manager, createJwt(manager, "user-claim", "ADMIN")));

        assertAuthenticationSuccessful(
                "user-claim", "USER", UserRole.USER, manager);
    }


    @Test
    public void testSameAdminUserClaimNames() {
        var config = new OidcClaimConfig(
            Optional.of("role-claim"),
            Optional.of("ADMIN"),
            Optional.of("role-claim"),
            Optional.of("USER")
        );
        var manager = new OidcAuthenticationManager(config);

        assertFalse(manager.check(() -> createAnonymousToken(), null).isGranted());

        assertEquals(
                "The token did not contain a claim named \"role-claim\".",
                getErrorMessage(manager, createJwt(manager, "A", "B")));

        assertEquals(
                "The token's \"role-claim\" claim did not contain \"ADMIN\" or \"USER\".",
                getErrorMessage(manager, createJwt(manager, "role-claim", "ASDF")));

        assertAuthenticationSuccessful(
                "role-claim", "ADMIN", UserRole.ADMIN, manager);
        assertAuthenticationSuccessful(
                "role-claim", "USER", UserRole.USER, manager);
    }

    @Test
    public void testSameAdminUserClaimNameAndValue() {
        var config = new OidcClaimConfig(
            Optional.of("role-claim"),
            Optional.of("ALL"),
            Optional.of("role-claim"),
            Optional.of("ALL")
        );
        var manager = new OidcAuthenticationManager(config);

        assertFalse(manager.check(() -> createAnonymousToken(), null).isGranted());

        assertEquals(
                "The token did not contain a claim named \"role-claim\".",
                getErrorMessage(manager, createJwt(manager, "A", "B")));

        assertEquals(
                "The token's \"role-claim\" claim did not contain \"ALL\".",
                getErrorMessage(manager, createJwt(manager, "role-claim", "ASDF")));

        assertAuthenticationSuccessful(
                "role-claim", "ALL", UserRole.ADMIN, manager);
        assertAuthenticationSuccessful(
                "role-claim", "ALL", UserRole.USER, manager);
    }

    private void assertAuthenticationSuccessful(
            String claimName, String claimValue, UserRole expectedRole,
            OidcAuthenticationManager manager) {
        var jwt = createJwt(manager, claimName, claimValue);
        assertTrue(manager.check(() -> jwt, null).isGranted());
        var hasRole = jwt.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals(expectedRole.springName));
        assertTrue(hasRole);
    }

    private static AnonymousAuthenticationToken createAnonymousToken() {
        return new AnonymousAuthenticationToken(
            "key", "principal", List.of(new SimpleGrantedAuthority("admin")));
    }

    private static JwtAuthenticationToken createJwt(
            OidcAuthenticationManager authManager, String claimName, String claimValue) {
        var jwt = new Jwt(
            "token",
            Instant.now().minusSeconds(10),
            Instant.now().plus(5, ChronoUnit.MINUTES),
            Map.of("TEST", "TEST"),
            Map.of(claimName, claimValue));
        return new JwtAuthenticationToken(jwt, authManager.convert(jwt));
    }

    private static String getErrorMessage(
            OidcAuthenticationManager authManager, Authentication auth) {
        var ex = TestUtil.assertThrows(
                AccessDeniedWithUserMessageException.class,
                () -> authManager.check(() -> auth, null));
        return ex.getMessage();
    }
}
