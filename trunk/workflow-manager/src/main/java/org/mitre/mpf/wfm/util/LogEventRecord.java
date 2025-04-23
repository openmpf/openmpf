package org.mitre.mpf.wfm.util;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        SECURITY("sec"),
        OPERATIONAL("oper"),
        SECURITY_SENSITIVE("sec_sec"),
        OPERATIONAL_SENSITIVE("op_sen");
        
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
}