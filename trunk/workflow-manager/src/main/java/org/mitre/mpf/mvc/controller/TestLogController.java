package org.mitre.mpf.mvc.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestLogController {
    private static final Logger log = LoggerFactory.getLogger(TestLogController.class);

    @GetMapping("/api/test-log")
    public String testLog() {
        log.trace("Test TRACE log");
        log.debug("Test DEBUG log");
        log.info("Test INFO log");
        log.warn("Test WARN log");
        log.error("Test ERROR log");
        return "Logs generated. Check workflow-manager.log";
    }
}