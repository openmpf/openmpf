package org.mitre.mpf.wfm.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
public class JsonLogger {
    private static final Logger log = LoggerFactory.getLogger(JsonLogger.class);
    private final ObjectMapper mapper;

    @Autowired
    public JsonLogger() {
        this.mapper = new ObjectMapper();
    }

    private void writeToLogger(LogEventRecord event) {
        try {
            log.info("JSON_LOG: {}", mapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to log event: {}", event, e);
        }
    }

    private String getCurrentLoggedInUser() {
        Authentication auth= SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    public JsonLogger log (LogEventRecord.TagType tag, LogEventRecord.OpType op, LogEventRecord.ResType res, String msg) {
        writeToLogger(new LogEventRecord(Instant.now().toString(), tag, "workflow-manager", getCurrentLoggedInUser(), op, res, msg));
        return this;
    }
    
}
