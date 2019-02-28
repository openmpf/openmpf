Deserializing Objects with Timestamps
-----------------
OpenMPF serializes all time fields in the ISO-8601 with offset format. For example, `2019-12-19T12:12:59.995-05:00`.
In order to properly deserialize these fields you can:
1. Use `org.mitre.mpf.interop.util.MpfObjectMapper`
2. If you would like to use your own Jackson ObjectMapper, then you will need to register the 
   `org.mitre.mpf.interop.util.InstantJsonModule` with the ObjectMapper instance. 
   For example: `objectMapper.registerModule(new InstantJsonModule());`
3. If you are using a different serialization library then you can use 
   `org.mitre.mpf.interop.util.TimeUtils.toIsoString(Instant instant)` and 
   `org.mitre.mpf.interop.util.TimeUtils.toInstant(String isoString)` to convert `java.time.Instant` to and from
   the ISO-8601 format.
