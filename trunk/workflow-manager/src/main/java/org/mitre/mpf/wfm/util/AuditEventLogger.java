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


package org.mitre.mpf.wfm.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

import org.mitre.mpf.interop.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
public class AuditEventLogger {
    private static final Logger log = LoggerFactory.getLogger(AuditEventLogger.class);
    private final ObjectMapper mapper;

    @Autowired
    public AuditEventLogger() {
        this.mapper = new ObjectMapper();
    }

    private void writeToLogger(LogAuditEventRecord event) {
        try {
            log.info("{}", mapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to log event: {}", event, e);
        }
    }

    private String getCurrentLoggedInUser() {
        Authentication auth= SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    public AuditEventLogger log (LogAuditEventRecord.TagType tag, LogAuditEventRecord.OpType op, LogAuditEventRecord.ResType res, String msg) {
        writeToLogger(new LogAuditEventRecord(TimeUtils.toIsoString(Instant.now()), tag, "openmpf", getCurrentLoggedInUser(), op, res, msg));
        return this;
    }
    
    public void logFileDownload(String fileUri, String fileType) {
        log(LogAuditEventRecord.TagType.SECURITY, 
            LogAuditEventRecord.OpType.READ, 
            LogAuditEventRecord.ResType.ACCESS, 
            "Downloaded " + fileType + ": " + fileUri);
    }
}
