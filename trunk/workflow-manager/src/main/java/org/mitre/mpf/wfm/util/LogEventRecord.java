package org.mitre.mpf.wfm.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogEventRecord(
    String time,
    TagType tag,
    String app,
    String user,
    OpType op,
    ResType res,
    String msg
) {
    public enum TagType {
        SECURITY("&B1E7-FFFF&"),
        OPERATIONAL("&A29B-FFFF&"),
        SECURITY_SENSITIVE("&C254-FFFF&"),
        OPERATIONAL_SENSITIVE("&E58A-FFFF&");
        
        private final String value;
        TagType( String value) {
            this.value = value;
        }
    }

    public enum OpType {
        CREATE("c"),
        READ("r"),
        MODIFY("m"),
        DELETE("d"),
        LOGIN("l"),
        EXTRACT("e");

        private final String value;
        OpType( String value) {
            this.value = value;
        }
    }

    public enum ResType {
        ACCESS("a"),
        DENY("d"),
        ERROR("e");
        
        private final String value;
        ResType( String value) {
            this.value = value;
        }

    }

    public LogEventRecord {
        if (time == null) {
            time = Instant.now().toString();
        }
    }
    
    public static LogEventRecord create() {
        return new LogEventRecord(
            Instant.now().toString(),
            TagType.SECURITY,
            "ApplicationName",
            "Logged In User", 
            OpType.LOGIN,
            ResType.ACCESS, 
            "The full error log message"
        );
    }
    
    
}