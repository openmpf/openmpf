package org.mitre.mpf.mvc.security;

import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.LogAuditEventRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class HawtioAuditFilter extends OncePerRequestFilter {
    
    private final AuditEventLogger _auditEventLogger;
    
    public HawtioAuditFilter(AuditEventLogger auditEventLogger) {
        this._auditEventLogger = auditEventLogger;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        if (requestURI.equals("/actuator/hawtio") || requestURI.equals("/actuator/hawtio/")) {
            _auditEventLogger.loginEvent()
                .withSecurityTag()
                .withUri(requestURI)
                .withEventId(LogAuditEventRecord.EventId.HAWTIO_ACCESS.success)
                .allowed(LogAuditEventRecord.EventId.HAWTIO_ACCESS.message + " succeeded");
        }
        filterChain.doFilter(request, response);
    }
}
