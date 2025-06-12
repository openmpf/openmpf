package org.mitre.mpf.mvc.security;

import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.LogAuditEventRecord;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(1)
public class HawtioAuditFilter extends OncePerRequestFilter {
    
    private final AuditEventLogger auditEventLogger;
    
    public HawtioAuditFilter(AuditEventLogger auditEventLogger) {
        this.auditEventLogger = auditEventLogger;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/actuator/hawtio")) {
            auditEventLogger.log(LogAuditEventRecord.TagType.SECURITY, 
                LogAuditEventRecord.OpType.LOGIN, // Or ACCESS if you have that
                LogAuditEventRecord.ResType.ALLOW, 
                "Hawtio endpoint accessed: " + request.getRequestURI());
        }
        filterChain.doFilter(request, response);
    }
}