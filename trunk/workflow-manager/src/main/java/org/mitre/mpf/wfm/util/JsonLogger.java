package org.mitre.mpf.wfm.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JsonLogger {
    private static final Logger log = LoggerFactory.getLogger(JsonLogger.class);
    private final ObjectMapper mapper;

    @Autowired
    public JsonLogger() {
        this.mapper = new ObjectMapper();
    }

    public void log(LogEventRecord event) {
        try {
            log.info("JSON_LOG: {}", mapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to log event: {}", event, e);
        }
    }

    public LogEventRecord createEvent() {
        return LogEventRecord.create();
    }
    
}
