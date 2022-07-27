package org.mitre.mpf.rest.api;

import java.util.List;

public record TiesDbRepostResponse(
        List<Long> success,
        List<Failure> failures) {


    public record Failure(long jobId, String error){}
}
