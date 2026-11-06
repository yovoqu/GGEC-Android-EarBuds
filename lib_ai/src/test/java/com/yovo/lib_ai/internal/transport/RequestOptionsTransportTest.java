package com.yovo.lib_ai.internal.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_ai.api.YoRequestOptions;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiTransportException;
import com.yovo.lib_ai.internal.runtime.CancelToken;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class RequestOptionsTransportTest {

    private MockWebServer server;
    private OkHttpClient client;
    private OkHttpTransport transport;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient.Builder().build();
        transport = new OkHttpTransport(client, 0, 0, 0, Thread::sleep);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
        OkHttpTransport.shutdown(client);
    }

    @Test
    public void writesIdempotencyKeyHeader() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        HttpRequestSpec spec = new HttpRequestSpec(
                "POST",
                server.url("/x").toString(),
                Map.of("Content-Type", "application/json"),
                "{}",
                false,
                new CancelToken(),
                5_000L,
                "idem-123");
        HttpExchange exchange = transport.execute(spec);
        try {
            assertEquals(200, exchange.statusCode());
            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            assertEquals("idem-123", request.getHeader("Idempotency-Key"));
        } finally {
            exchange.close();
        }
    }

    @Test
    public void perRequestTimeoutInterruptsLongCall() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .setBodyDelay(800, TimeUnit.MILLISECONDS));
        HttpRequestSpec spec = new HttpRequestSpec(
                "POST",
                server.url("/slow").toString(),
                Map.of("Content-Type", "application/json"),
                "{}",
                false,
                new CancelToken(),
                100L,
                null);
        try {
            HttpExchange exchange = transport.execute(spec);
            exchange.close();
            fail("expected timeout");
        } catch (YoAiTransportException exception) {
            assertEquals(YoAiErrorKind.TIMEOUT, YoAiErrors.kindOf(exception));
        }
    }

    @Test
    public void optionsBuilderRejectsNonPositiveTimeout() {
        try {
            YoRequestOptions.builder().timeoutMillis(0L).build();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("timeoutMillis"));
        }
    }

    @Test
    public void optionsHeadersAreDefensiveCopies() {
        Map<String, String> base = new LinkedHashMap<>();
        base.put("X-Base", "1");
        YoRequestOptions options = YoRequestOptions.builder().header("X-Extra", "2").build();
        base.putAll(options.headers());
        assertEquals("2", base.get("X-Extra"));
        assertEquals("1", base.get("X-Base"));
    }
}
