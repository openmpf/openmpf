package org.mitre.mpf.mvc.security;

import org.mitre.mpf.wfm.util.AuditEventLogger;
import org.mitre.mpf.wfm.util.LogAuditEventRecord;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.event.AuthorizationFailureEvent;
import org.springframework.security.access.event.AuthorizedEvent;
import org.springframework.security.web.FilterInvocation;
import org.springframework.stereotype.Component;

@Component
public class HawtioAccessEventListener {

    private final AuditEventLogger auditEventLogger;

    public HawtioAccessEventListener(AuditEventLogger auditEventLogger) {
        this.auditEventLogger = auditEventLogger;
    }

    @EventListener
    public void handleAuthorizationSuccess(AuthorizedEvent event) {
        if (event.getSource() instanceof FilterInvocation) {
            FilterInvocation filterInvocation = (FilterInvocation) event.getSource();
            if (filterInvocation.getRequest().getRequestURI().contains("/actuator/hawtio")) {
                auditEventLogger.log(LogAuditEventRecord.TagType.SECURITY, 
                LogAuditEventRecord.OpType.LOGIN, 
                LogAuditEventRecord.ResType.ALLOW, 
                "Hawtio accessed");
            }
        }
    }    

    @EventListener
    public void handleAccessDenied(AuthorizationFailureEvent event) {
        if (event.getSource() instanceof FilterInvocation) {
            FilterInvocation filterInvocation = (FilterInvocation) event.getSource();
            String requestURI = filterInvocation.getRequest().getRequestURI();
            
            if (requestURI.startsWith("/actuator/hawtio")) {
                auditEventLogger.log(
                    LogAuditEventRecord.TagType.SECURITY, 
                    LogAuditEventRecord.OpType.LOGIN, 
                    LogAuditEventRecord.ResType.DENY, 
                    "Unauthorized attempt to access Hawtio console"
                );
            }
        }
    }
}
