package org.mitre.mpf.wfm.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.ConnectionClosedException;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.data.InProgressBatchJobsService;
import org.mitre.mpf.wfm.enums.MpfConstants;
import org.mitre.mpf.wfm.util.AggregateJobPropertiesUtil;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.mitre.mpf.wfm.util.PropertiesUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import spark.Request;
import spark.Response;
import spark.Route;
import spark.Service;
import spark.Spark;

public class TestS3Copy {
    private static Service _sparkSource;

    private static Service _sparkDestination;

    private static final String SOURCE_ACCESS_KEY = "SOURCE_ACCESS_KEY";

    private static final String DESTINATION_ACCESS_KEY = "DESTINATION_ACCESS_KEY";

    private final PropertiesUtil _mockPropertiesUtil = mock(PropertiesUtil.class);

    private final LocalStorageBackend _mockLocalStorageBackend = mock(LocalStorageBackend.class);

    private final InProgressBatchJobsService _mockInProgressJobs = mock(InProgressBatchJobsService.class);

    private final AggregateJobPropertiesUtil _mockAggJobProps = mock(AggregateJobPropertiesUtil.class);

    private final ObjectMapper _objectMapper = ObjectMapperFactory.customObjectMapper();

    private S3StorageBackend _s3StorageBackend;


    @BeforeClass
    public static void initClass() {
        startSpark();
        startSparkDestination();
    }

    @AfterClass
    public static void tearDownClass() {
        if (_sparkSource != null) {
            _sparkSource.stop();
        }
        if (_sparkDestination != null) {
            _sparkDestination.stop();
        }
    }


    @Before
    public void init() {
        when(_mockPropertiesUtil.getS3ClientCacheCount())
                .thenReturn(20);
        when(_mockPropertiesUtil.getHttpStorageUploadRetryCount())
                .thenReturn(2);
        _s3StorageBackend = new S3StorageBackend(
                _mockPropertiesUtil,
                _mockLocalStorageBackend,
                _mockInProgressJobs,
                _mockAggJobProps,
                _objectMapper);
    }


    @Test
    public void testGetOutputObjectErrorStatus() throws IOException, StorageException {
        var numGetRequests = new AtomicInteger(0);
        setSourceGetRoute((req, resp) -> {
            if (numGetRequests.getAndIncrement() == 0) {
                Spark.halt(500);
            }
            return "hello";
        });

        var props = getS3Properties();
        var copyConfig = new S3CopyConfig(props::get);
        var oldOutputObjectUri = URI.create("http://localhost:5000/RESULTS_BUCKET/key");
        try (var is = _s3StorageBackend.getOldJobOutputObjectStream(oldOutputObjectUri, copyConfig)) {
            assertEquals("hello", new String(is.readAllBytes()));
        }

        assertEquals(2, numGetRequests.get());
    }


    @Test
    public void testGetOutObjectIncompleteDownload() throws StorageException, IOException {
        var data = "x".repeat(100).getBytes();
        var numAttempts = new AtomicInteger();
        setSourceGetRoute((req, resp) -> {
            numAttempts.incrementAndGet();
            resp.status(200);
            setLength(resp, data.length);
            resp.raw().getOutputStream().write(data, 0, 10);
            return "";
        });

        var props = getS3Properties();
        var copyConfig = new S3CopyConfig(props::get);

        var oldOutputObjectUri = URI.create("http://localhost:5000/RESULTS_BUCKET/key");
        var ex = TestUtil.assertThrows(
            StorageException.class,
            () -> _s3StorageBackend.getOldJobOutputObject(oldOutputObjectUri, copyConfig));
        assertThat(ex.getCause(), Matchers.instanceOf(ConnectionClosedException.class));
        assertEquals(3, numAttempts.get());
    }


    @Test
    public void testGetOutObjectIncompleteDownloadRecovery() throws IOException, StorageException {
        var originalOutputObject = new JsonOutputObject(
                "job id", "obj id", null, 4, "site id", "X.Y", "ext job id",
                Instant.now(), Instant.now(), "status");
        var bytes = _objectMapper.writeValueAsBytes(originalOutputObject);
        var numAttempts = new AtomicInteger();
        setSourceGetRoute((req, resp) -> {
            resp.status(200);
            setLength(resp, bytes.length);
            var os = resp.raw().getOutputStream();
            if (numAttempts.getAndIncrement() == 0) {
                os.write(bytes, 0, 10);
            }
            else {
                os.write(bytes);
            }
            return "";
        });

        var copyConfig = new S3CopyConfig(getS3Properties()::get);
        var oldOutputObjectUri = URI.create("http://localhost:5000/RESULTS_BUCKET/key");
        var downloaded = _s3StorageBackend.getOldJobOutputObject(oldOutputObjectUri, copyConfig);

        assertEquals(originalOutputObject.getJobId(), downloaded.getJobId());
        assertEquals(2, numAttempts.get());
    }


    @Test
    public void testSlowCopyErrorStatus() throws IOException, StorageException {
        var numGetRequests = new AtomicInteger(0);
        setSourceGetRoute((req, resp) -> {
            if (numGetRequests.getAndIncrement() == 0) {
                Spark.halt(500);
            }
            var dataBytes = "hello".getBytes();
            setLength(resp, dataBytes.length);
            return dataBytes;
        });

        var numPutRequests = new AtomicInteger(0);
        var putResult = new AtomicReference<String>();
        setDestinationPutRoute((req, resp) -> {
            if (numPutRequests.getAndIncrement() == 0) {
                Spark.halt(500);
            }
            putResult.set(unChunkBody(req));
            return "";
        });

        var copyConfig = new S3CopyConfig(getS3Properties()::get);
        var sourceUri = URI.create("http://localhost:5000/RESULTS_BUCKET/key");
        var results = _s3StorageBackend.copyResults(List.of(sourceUri), copyConfig);
        assertEquals("hello", putResult.get());
        assertEquals(
            URI.create("http://localhost:5001/RESULTS_BUCKET/key"),
            results.get(sourceUri));
        assertEquals(2, numGetRequests.get());
        assertEquals(2, numPutRequests.get());
    }


    @Test
    public void testSlowCopyIncompleteDownload() throws StorageException, IOException {
        var data = "x".repeat(100);
        var bytes = data.getBytes();
        var numGetRequests = new AtomicInteger(0);
        setSourceGetRoute((req, resp) -> {
            resp.status(200);
            setLength(resp, bytes.length);
            var os = resp.raw().getOutputStream();
            if (numGetRequests.getAndIncrement() == 0) {
                os.write(bytes, 0, 10);
            }
            else {
                os.write(bytes);
            }
            return "";
        });

        var numPuts = new AtomicInteger();
        var putData = new AtomicReference<String>();
        setDestinationPutRoute((req, resp) -> {
            if (numPuts.getAndIncrement() == 0) {
                resp.status(400);
                return INCOMPLETE_BODY_RESPONSE;
            }
            putData.set(unChunkBody(req));
            return "";
        });

        when(_mockPropertiesUtil.getHttpStorageUploadRetryCount())
                .thenReturn(1);

        var copyConfig = new S3CopyConfig(getS3Properties()::get);
        var sourceUri = URI.create("http://localhost:5000/RESULTS_BUCKET/key");

        var results = _s3StorageBackend.copyResults(List.of(sourceUri), copyConfig);
        assertEquals(
                URI.create("http://localhost:5001/RESULTS_BUCKET/key"),
                results.get(sourceUri));
        assertEquals(data, putData.get());
        assertEquals(2, numGetRequests.get());
        assertEquals(2, numPuts.get());
    }


    @Test
    public void testSlowCopyDestinationError() throws StorageException, IOException {
        var numGetRequests = new AtomicInteger();
        setSourceGetRoute((req, resp) -> {
            numGetRequests.incrementAndGet();
            var data = "hello".getBytes();
            setLength(resp, data.length);
            return data;
        });

        var numPutRequests = new AtomicInteger();
        var putResult = new AtomicReference<String>();
        setDestinationPutRoute((req, resp) -> {
            if (numPutRequests.getAndIncrement() == 0) {
                Spark.halt(500);
            }
            putResult.set(unChunkBody(req));
            return "";
        });

        var copyConfig = new S3CopyConfig(getS3Properties()::get);
        var sourceUri = URI.create("http://localhost:5000/RESULTS_BUCKET/key");
        var results = _s3StorageBackend.copyResults(List.of(sourceUri), copyConfig);
        assertEquals("hello", putResult.get());
        assertEquals(
            URI.create("http://localhost:5001/RESULTS_BUCKET/key"),
            results.get(sourceUri));
        assertEquals(1, numGetRequests.get());
        assertEquals(2, numPutRequests.get());
    }


    @Test
    public void testFastCopy() throws StorageException, IOException {
        var numPutRequests = new AtomicInteger();
        var destinationPath = new AtomicReference<String>();
        var bodyLength = new AtomicInteger(-1);
        var copySourceHeader = new AtomicReference<String>();
        setDestinationPutRoute((req, resp) -> {
            if (numPutRequests.getAndIncrement() == 0) {
                Spark.halt(500);
            }
            destinationPath.set(req.uri());
            copySourceHeader.set(req.headers("x-amz-copy-source"));
            bodyLength.set(req.bodyAsBytes().length);
            return "";
        });


        var properties = new HashMap<>(getS3Properties());
        properties.put(MpfConstants.TIES_DB_COPY_SRC_S3_ACCESS_KEY, DESTINATION_ACCESS_KEY);
        var copyConfig = new S3CopyConfig(properties::get);
        var sourceUri = URI.create("http://localhost:5001/SOURCE_BUCKET/key");
        var results = _s3StorageBackend.copyResults(List.of(sourceUri), copyConfig);
        assertEquals(
            URI.create("http://localhost:5001/RESULTS_BUCKET/key"),
            results.get(sourceUri));
        assertEquals("/RESULTS_BUCKET/key", destinationPath.get());
        assertEquals("SOURCE_BUCKET/key", copySourceHeader.get());
        assertEquals(0, bodyLength.get());
        assertEquals(2, numPutRequests.get());
    }

    @Test
    public void testSourceKeyPrefix() throws StorageException, IOException {
        var sourceToDest = new ConcurrentHashMap<String, String>();
        setDestinationPutRoute((req, resp) -> {
            sourceToDest.put(req.headers("x-amz-copy-source"), req.uri());
            return "";
        });

        var properties = new HashMap<>(getS3Properties());
        properties.put(MpfConstants.TIES_DB_COPY_SRC_S3_ACCESS_KEY, DESTINATION_ACCESS_KEY);
        properties.put(MpfConstants.TIES_DB_COPY_SRC_S3_UPLOAD_OBJECT_KEY_PREFIX, "prefix/");
        var copyConfig = new S3CopyConfig(properties::get);


        var uriNoPrefix = URI.create("http://localhost:5001/SOURCE_BUCKET/key");
        var uriWithPrefix = URI.create("http://localhost:5001/SOURCE_BUCKET/prefix/key2");
        var results = _s3StorageBackend.copyResults(List.of(uriNoPrefix, uriWithPrefix), copyConfig);
        assertEquals(
            URI.create("http://localhost:5001/RESULTS_BUCKET/key"),
            results.get(uriNoPrefix));
        assertEquals(
            URI.create("http://localhost:5001/RESULTS_BUCKET/key2"),
            results.get(uriWithPrefix));

        assertEquals("/RESULTS_BUCKET/key", sourceToDest.get("SOURCE_BUCKET/key"));
        assertEquals("/RESULTS_BUCKET/key2", sourceToDest.get("SOURCE_BUCKET/prefix/key2"));
    }


    @Test
    public void testDestinationKeyPrefix() throws StorageException, IOException {
        var sourceToDest = new ConcurrentHashMap<String, String>();
        setDestinationPutRoute((req, resp) -> {
            sourceToDest.put(req.headers("x-amz-copy-source"), req.uri());
            return "";
        });

        var properties = new HashMap<>(getS3Properties());
        properties.put(MpfConstants.TIES_DB_COPY_SRC_S3_ACCESS_KEY, DESTINATION_ACCESS_KEY);
        properties.put(MpfConstants.S3_UPLOAD_OBJECT_KEY_PREFIX, "prefix/");
        var copyConfig = new S3CopyConfig(properties::get);


        var uriNoPrefix = URI.create("http://localhost:5001/SOURCE_BUCKET/key");
        var uriWithPrefix = URI.create("http://localhost:5001/SOURCE_BUCKET/prefix/key2");
        var results = _s3StorageBackend.copyResults(List.of(uriNoPrefix, uriWithPrefix), copyConfig);
        assertEquals(
            URI.create("http://localhost:5001/RESULTS_BUCKET/prefix/key"),
            results.get(uriNoPrefix));
        assertEquals(
            URI.create("http://localhost:5001/RESULTS_BUCKET/prefix/key2"),
            results.get(uriWithPrefix));

        assertEquals("/RESULTS_BUCKET/prefix/key", sourceToDest.get("SOURCE_BUCKET/key"));
        assertEquals("/RESULTS_BUCKET/prefix/key2", sourceToDest.get("SOURCE_BUCKET/prefix/key2"));
    }


    @Test
    public void testSourceAndDestinationKeyPrefix() throws StorageException, IOException {
        var sourceToDest = new ConcurrentHashMap<String, String>();
        setDestinationPutRoute((req, resp) -> {
            sourceToDest.put(req.headers("x-amz-copy-source"), req.uri());
            return "";
        });

        var properties = new HashMap<>(getS3Properties());
        properties.put(MpfConstants.TIES_DB_COPY_SRC_S3_ACCESS_KEY, DESTINATION_ACCESS_KEY);
        properties.put(MpfConstants.S3_UPLOAD_OBJECT_KEY_PREFIX, "dest-prefix/");
        properties.put(MpfConstants.TIES_DB_COPY_SRC_S3_UPLOAD_OBJECT_KEY_PREFIX, "src-prefix/");
        var copyConfig = new S3CopyConfig(properties::get);

        var uriNoPrefix = URI.create("http://localhost:5001/SOURCE_BUCKET/key");
        var uriSrcPrefix = URI.create("http://localhost:5001/SOURCE_BUCKET/src-prefix/key2");
        var uriDestPrefix = URI.create("http://localhost:5001/SOURCE_BUCKET/dest-prefix/key3");
        var results = _s3StorageBackend.copyResults(
                List.of(uriNoPrefix, uriSrcPrefix, uriDestPrefix), copyConfig);

        assertEquals(
            URI.create("http://localhost:5001/RESULTS_BUCKET/dest-prefix/key"),
            results.get(uriNoPrefix));

        assertEquals(
            URI.create("http://localhost:5001/RESULTS_BUCKET/dest-prefix/key2"),
            results.get(uriSrcPrefix));

        assertEquals(
            URI.create("http://localhost:5001/RESULTS_BUCKET/dest-prefix/key3"),
            results.get(uriDestPrefix));

        assertEquals(
                "/RESULTS_BUCKET/dest-prefix/key",
                sourceToDest.get("SOURCE_BUCKET/key"));
        assertEquals(
                "/RESULTS_BUCKET/dest-prefix/key2",
                sourceToDest.get("SOURCE_BUCKET/src-prefix/key2"));
        assertEquals(
                "/RESULTS_BUCKET/dest-prefix/key3",
                sourceToDest.get("SOURCE_BUCKET/dest-prefix/key3"));
    }

    @Test
    public void testPrefixWithSlowCopy() throws StorageException, IOException {
        var sourceUriPath = new AtomicReference<String>();
        setSourceGetRoute((req, resp) -> {
            sourceUriPath.set(req.uri());
            var dataBytes = "hello".getBytes();
            setLength(resp, dataBytes.length);
            return dataBytes;
        });

        var destUriPath = new AtomicReference<String>();
        setDestinationPutRoute((req, resp) -> {
            destUriPath.set(req.uri());
            return "";
        });

        var properties = new HashMap<>(getS3Properties());
        properties.put(MpfConstants.S3_UPLOAD_OBJECT_KEY_PREFIX, "prefix/");
        var copyConfig = new S3CopyConfig(properties::get);
        var sourceUri = URI.create("http://localhost:5000/RESULTS_BUCKET/key");
        var results = _s3StorageBackend.copyResults(List.of(sourceUri), copyConfig);
        assertEquals(
                URI.create("http://localhost:5001/RESULTS_BUCKET/prefix/key"),
                results.get(sourceUri));
        assertEquals("/RESULTS_BUCKET/key", sourceUriPath.get());
        assertEquals("/RESULTS_BUCKET/prefix/key", destUriPath.get());

    }


    private static Map<String, String> getS3Properties() {
        return Map.of(
            MpfConstants.S3_RESULTS_BUCKET, "http://localhost:5001/RESULTS_BUCKET",
            MpfConstants.S3_SECRET_KEY, "<MY_SECRET_KEY>",
            MpfConstants.S3_ACCESS_KEY, DESTINATION_ACCESS_KEY,
            MpfConstants.TIES_DB_COPY_SRC_S3_ACCESS_KEY, SOURCE_ACCESS_KEY,
            MpfConstants.S3_REGION, "us-east-1");
    }



    private static volatile Route _sourceGetRoute = (req, resp) -> Spark.halt(500);
    private static void setSourceGetRoute(Route route) {
        _sourceGetRoute = route;
    }


    private static volatile Route _destinationPutRoute = _sourceGetRoute;
    private static void setDestinationPutRoute(Route route) {
        _destinationPutRoute = route;
    }

    private static void startSpark() {
        var instance = Service.ignite().port(5000);
        _sparkSource = instance;

        instance.before((req, resp) -> {
            if (!req.headers("Authorization").contains(SOURCE_ACCESS_KEY)) {
                Spark.halt(403, "Unauthorized");
            }
        });

        instance.head("/:bucket/*", (req, resp) -> {
            return "";
        });

        instance.get("/:bucket/*", (req, resp) -> _sourceGetRoute.handle(req, resp));
        instance.awaitInitialization();
    }

    private static void startSparkDestination() {
        var instance = Service.ignite().port(5001);
        _sparkDestination = instance;

        instance.before((req, resp) -> {
            if (!req.headers("Authorization").contains(DESTINATION_ACCESS_KEY)) {
                Spark.halt(403, "Unauthorized");
            }
        });

        instance.head("/:bucket/*", (req, resp) -> {
            instance.halt(404);
            return "";
        });

        instance.put("/:bucket/*", (req, resp) -> _destinationPutRoute.handle(req, resp));
        instance.awaitInitialization();
    }


    private static void setLength(Response resp, long length) {
        resp.header("Content-Length", String.valueOf(length));
    }

    private static String unChunkBody(Request req) {
        return req.body().split("\r\n")[1];
    }

    private static final String INCOMPLETE_BODY_RESPONSE = """
        <Error>
            <Code>IncompleteBody</Code>
            <Message>You did not provide the number of bytes specified by the Content-Length HTTP header.</Message>
            <Resource>resource</Resource>
            <RequestId>4442587FB7D0A2F9</RequestId>
        </Error>""";
}
