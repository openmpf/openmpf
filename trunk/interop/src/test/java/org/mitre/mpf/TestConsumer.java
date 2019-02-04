package org.mitre.mpf;

import java.io.IOException;
import java.util.Date;

public class TestConsumer {

    public static void main(String[] args) throws IOException {
        /*
        ObjectMapper objectMapper = new ObjectMapper(); // doesn't work
        // MpfObjectMapper objectMapper = new MpfObjectMapper(); // works
        JsonOutputObject output = objectMapper.readValue(
                new File("/home/mpf/Desktop/TMP/detection-test-r4-diff.json"),
                JsonOutputObject.class);
        System.out.println(output.getJobId());
        */
        Date data = new Date("2019-01-31 10:53:26"); // "2019-01-31T10:53:26-05:00"
    }
}