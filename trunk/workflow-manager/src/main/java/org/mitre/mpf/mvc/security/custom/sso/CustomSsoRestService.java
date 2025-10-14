package org.mitre.mpf.mvc.security.custom.sso;

import java.io.IOException;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Profile("custom_sso")
public class CustomSsoRestService extends BaseCustomSsoService {

    private final CustomSsoTokenValidator _customSsoTokenValidator;

    private final ObjectMapper _objectMapper;

    @Inject
    CustomSsoRestService(
            AuthenticationEventPublisher authEventPublisher,
            CustomSsoTokenValidator customSsoTokenValidator,
            ObjectMapper objectMapper) {
        super(authEventPublisher);
        _customSsoTokenValidator = customSsoTokenValidator;
        _objectMapper = objectMapper;
    }

    @Override
    protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
        // We unconditionally return a non-null value here because we have no way to initiate an
        // authentication process like we do when the client is a browser.
        return "Unauthenticated REST Client";
    }

    @Override
    protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
        return request.getHeader("Authorization");
    }


    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        return _customSsoTokenValidator.authenticateBearer(authentication);
    }

    @Override
    protected void handleAuthCommence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        var messageObj = Map.of("message", authException.getMessage());
        _objectMapper.writeValue(response.getWriter(), messageObj);
    }
}
